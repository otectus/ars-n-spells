package com.otectus.arsnspells.rituals;

import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.contract.InscriptionPlanner;
import com.otectus.arsnspells.inscription.InscriptionOutcome;
import com.otectus.arsnspells.inscription.StackInscriptionView;
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
public class SpellTranscriptionRitual extends AnsRitual {
    public static final String REGISTRY_PATH = "spell_transcription";
    private static final String LANG_PREFIX = "ritual.ars_n_spells.spell_transcription.";
    private static final int SEARCH_RADIUS = 3;

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
            error(LANG_PREFIX + "error.empty_range");
            return;
        }

        InscriptionInputs inputs = InscriptionInputs.classify(entities);

        // Already-inscribed items in range are a distinct error from
        // too-many-targets: the fix is "uninscribe first", not "remove".
        if (!inputs.inscribed.isEmpty()) {
            error(LANG_PREFIX + "error.inscribed_items",
                InscriptionInputs.joinNames(inputs.inscribed));
            return;
        }

        if (inputs.sources.isEmpty()) {
            error(LANG_PREFIX + "error.no_source");
            return;
        }
        if (inputs.sources.size() > 1) {
            error(LANG_PREFIX + "error.multiple_sources",
                inputs.sources.size(), InscriptionInputs.joinNames(inputs.sources));
            return;
        }

        if (inputs.blankTargets.isEmpty()) {
            error(LANG_PREFIX + "error.no_target");
            return;
        }
        if (inputs.blankTargets.size() > 1) {
            error(LANG_PREFIX + "error.multiple_targets",
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
            error(LANG_PREFIX + "error.target_ars_rooted",
                targetStack.getHoverName().getString());
            return;
        }

        InscriptionSource source = InscriptionInputs.readSource(sourceStack);
        if (source == null) {
            // Classify said this was a source but a second read failed -- a
            // genuinely transient parse problem, not a validation error.
            error(LANG_PREFIX + "error.source_parse_failed",
                sourceStack.getHoverName().getString());
            return;
        }

        // Audit V18: the authoritative blank/filled decision. The bucket split above only
        // says which dropped entity plays which role; whether the target may legally be
        // written is the planner's call, taken from what the items are and from the target's
        // own native container -- never from "ANS has not written here".
        InscriptionPlan plan = InscriptionPlanner.plan(
            new StackInscriptionView(sourceStack, targetStack));
        if (!plan.isPermitted()) {
            error(LANG_PREFIX + "error.refused." + reasonSuffix(plan.reasonCode()),
                targetStack.getHoverName().getString());
            return;
        }
        // Audit V19: one inscription is one unit on both sides. The ritual used to hand the
        // whole target stack to the mutation below, so a stack of 64 blanks came back as 64
        // inscribed items for the price of one source.
        InscriptionOutcome outcome =
            InscriptionOutcome.of(plan, sourceStack.getCount(), targetStack.getCount());
        if (!outcome.isPermitted()) {
            error(LANG_PREFIX + "error.refused." + reasonSuffix(outcome.reasonCode()),
                targetStack.getHoverName().getString());
            return;
        }

        // Validation complete -- mutation begins here.
        ItemStack inscribed = targetStack.copyWithCount(outcome.transformedTargets());
        switch (source.type) {
            case ARS_NOUVEAU:
                CrossCastingHandler.addCrossModSpell(inscribed, source.arsSpell);
                break;
            case IRONS_SPELLBOOKS:
                CrossCastingHandler.addCrossModSpell(inscribed, source.spellId,
                    source.spellLevel, CrossSpellType.IRONS_SPELLBOOKS);
                break;
        }
        // Split the untouched remainder back into the world instead of transforming it.
        if (outcome.targetRemainder() > 0) {
            targetEntity.setItem(targetStack.copyWithCount(outcome.targetRemainder()));
            dropBeside(level, targetEntity, inscribed);
        } else {
            targetEntity.setItem(inscribed);
        }
        // A reusable book or focus is read, not eaten: the plan says zero units for one, and
        // InscriptionPlan refuses to be constructed saying otherwise.
        if (outcome.consumedFromSource() > 0) {
            sourceStack.shrink(outcome.consumedFromSource());
            if (sourceStack.isEmpty()) {
                sourceEntity.discard();
            } else {
                sourceEntity.setItem(sourceStack);
            }
        }

        playInscribeEffects(level, pos);
        success(LANG_PREFIX + "success", sourceLabel(source));
    }

    /**
     * Put the single inscribed item into the world next to the stack it was split off, so the
     * player can see that one item changed and the rest did not.
     */
    private void dropBeside(Level level, ItemEntity origin, ItemStack inscribed) {
        ItemEntity dropped = new ItemEntity(level, origin.getX(), origin.getY() + 0.25,
            origin.getZ(), inscribed, 0.0, 0.0, 0.0);
        dropped.setPickUpDelay(10);
        level.addFreshEntity(dropped);
    }

    /**
     * Reason code to lang-key suffix. The codes are stable machine-readable strings from
     * {@link InscriptionPlan}; the mapping is here so the ritual can name the actual rule that
     * refused it instead of the old catch-all "no blank target".
     */
    private static String reasonSuffix(String reasonCode) {
        return switch (reasonCode) {
            case InscriptionPlan.REASON_NOT_BLANK -> "not_blank";
            case InscriptionPlan.REASON_TARGET_NOT_EMPTY -> "target_not_empty";
            case InscriptionPlan.REASON_INSUFFICIENT_STACK -> "insufficient_stack";
            default -> "unknown";
        };
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
