package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.CrossSpellType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Stamps a source spell (Ars spell parchment/focus/spellbook or Iron's scroll)
 * onto a blank target item as a cross-cast NBT payload. The target can then
 * be right-clicked to cast the inscribed spell through the cross-cast pipeline.
 *
 * Usage:
 *  <ol>
 *    <li>Drop exactly one spell-bearing source near the ritual brazier.</li>
 *    <li>Drop exactly one blank target item in the same area.</li>
 *    <li>Activate the ritual. On success the source is consumed and the
 *        target gains the {@code arsnspells:cross_spells} NBT list.</li>
 *  </ol>
 *
 * Validation runs fully before any item mutation: every failure produces a
 * lang-keyed, player-facing message naming the offending items and the rule.
 */
public class SpellTranscriptionRitual extends AbstractRitual {
    public static final String REGISTRY_PATH = "spell_transcription";
    private static final String LANG_PREFIX = "ritual.ars_n_spells.spell_transcription.";
    private static final int SEARCH_RADIUS = 3;

    @Override
    protected void tick() {}

    @Override
    public void onEnd() {
        Level level = getWorld();
        BlockPos pos = getPos();
        if (level == null || pos == null || level.isClientSide()) {
            return;
        }

        AABB area = new AABB(pos).inflate(SEARCH_RADIUS);
        List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, area,
            e -> e.isAlive() && !e.getItem().isEmpty());

        if (entities.isEmpty()) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.empty_range");
            return;
        }

        InscriptionInputs inputs = InscriptionInputs.classify(entities);

        // Already-inscribed items in range are a distinct error from
        // too-many-targets: the fix is "uninscribe first", not "remove".
        if (!inputs.inscribed.isEmpty()) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.inscribed_items",
                InscriptionInputs.joinNames(inputs.inscribed));
            return;
        }

        if (inputs.sources.isEmpty()) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.no_source");
            return;
        }
        if (inputs.sources.size() > 1) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.multiple_sources",
                inputs.sources.size(), InscriptionInputs.joinNames(inputs.sources));
            return;
        }

        if (inputs.blankTargets.isEmpty()) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.no_target");
            return;
        }
        if (inputs.blankTargets.size() > 1) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.multiple_targets",
                inputs.blankTargets.size(), InscriptionInputs.joinNames(inputs.blankTargets));
            return;
        }

        ItemEntity sourceEntity = inputs.sources.get(0);
        ItemEntity targetEntity = inputs.blankTargets.get(0);
        ItemStack sourceStack = sourceEntity.getItem();
        ItemStack targetStack = targetEntity.getItem();

        // Defensive pass for decision 5: classification routes filled Ars items
        // into sources, so this only fires if an item has partial Ars NBT at
        // root that the source parser didn't recognize as filled but would
        // still let Ars's own right-click handler shadow the cross-cast.
        if (InscriptionInputs.hasArsSpellAtRoot(targetStack)) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.target_ars_rooted",
                targetStack.getHoverName().getString());
            return;
        }

        InscriptionSource source = InscriptionInputs.readSource(sourceStack);
        if (source == null) {
            // Classify said this was a source but a second read failed -- a
            // genuinely transient parse problem, not a validation error.
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.source_parse_failed",
                sourceStack.getHoverName().getString());
            return;
        }

        // Validation complete -- mutation begins here.
        switch (source.type) {
            case ARS_NOUVEAU:
                CrossCastingHandler.addCrossModSpell(targetStack, source.arsSpell);
                break;
            case IRONS_SPELLBOOKS:
                CrossCastingHandler.addCrossModSpell(targetStack, source.spellId,
                    source.spellLevel, CrossSpellType.IRONS_SPELLBOOKS);
                break;
        }
        targetEntity.setItem(targetStack);
        sourceEntity.discard();

        playInscribeEffects(level, pos);
        RitualFeedback.success(level, pos, LANG_PREFIX + "success", sourceLabel(source));
    }

    private void playInscribeEffects(Level level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.2;
        double cz = pos.getZ() + 0.5;
        if (level instanceof ServerLevel server) {
            // Enchantment glyph particles spiral into the brazier -- reads as
            // "binding arcane knowledge to an object".
            server.sendParticles(ParticleTypes.ENCHANT, cx, cy, cz, 60, 0.6, 0.8, 0.6, 0.2);
        }
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE,
            SoundSource.BLOCKS, 0.8f, 1.1f);
    }

    private String sourceLabel(InscriptionSource source) {
        if (source.type == CrossSpellType.ARS_NOUVEAU) {
            return Component.translatable(LANG_PREFIX + "source.ars").getString();
        }
        String id = source.spellId == null ? "" : source.spellId.toString();
        return Component.translatable(LANG_PREFIX + "source.irons", id, source.spellLevel)
            .getString();
    }

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation("ars_n_spells", REGISTRY_PATH);
    }
}
