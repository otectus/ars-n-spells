package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * Mirror of {@link SpellTranscriptionRitual} that removes the cross-cast NBT
 * payload from a single inscribed item. The result is bit-identical to a
 * fresh blank target so the same item can be re-used in a subsequent
 * transcribe ritual without orphan NBT or cycle-index residue.
 *
 * Deliberately Iron's-independent: it manipulates a known root NBT key only,
 * so it remains useful even if Iron's Spellbooks has been uninstalled and
 * the player wants to clean up legacy inscribed items.
 *
 * Validation rules (strict, matching transcribe):
 *  <ul>
 *    <li>Exactly one inscribed item in range.</li>
 *    <li>No spell-bearing sources allowed (those belong to transcribe).</li>
 *    <li>No blank items allowed (would imply the player intended transcribe).</li>
 *  </ul>
 * Every violation produces a lang-keyed message naming the offending items.
 */
public class SpellUninscriptionRitual extends AbstractRitual {
    public static final String REGISTRY_PATH = "spell_uninscription";
    private static final String LANG_PREFIX = "ritual.ars_n_spells.spell_uninscription.";
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

        // Sources or blanks in range mean the player likely meant transcribe;
        // refuse early so we never accidentally strip an item the player
        // intended to keep inscribed.
        if (!inputs.sources.isEmpty()) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.unexpected_source",
                InscriptionInputs.joinNames(inputs.sources));
            return;
        }
        if (!inputs.blankTargets.isEmpty()) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.unexpected_blank",
                InscriptionInputs.joinNames(inputs.blankTargets));
            return;
        }

        if (inputs.inscribed.isEmpty()) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.no_inscribed");
            return;
        }
        if (inputs.inscribed.size() > 1) {
            RitualFeedback.error(level, pos, LANG_PREFIX + "error.multiple_inscribed",
                inputs.inscribed.size(), InscriptionInputs.joinNames(inputs.inscribed));
            return;
        }

        ItemEntity inscribedEntity = inputs.inscribed.get(0);
        ItemStack stack = inscribedEntity.getItem();
        String displayName = stack.getHoverName().getString();

        // Strip cleanly. CrossCastNbt drops both the spells list and the
        // cycle index, then collapses an empty residual root tag to null so
        // the result matches a never-inscribed item bit-for-bit.
        CrossModSpellComponents.clear(stack);
        inscribedEntity.setItem(stack);

        playUninscribeEffects(level, pos);
        RitualFeedback.success(level, pos, LANG_PREFIX + "success", displayName);
    }

    private void playUninscribeEffects(Level level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.2;
        double cz = pos.getZ() + 0.5;
        if (level instanceof ServerLevel server) {
            // Ash and smoke read as "ink dissolving / glyphs scattering".
            server.sendParticles(ParticleTypes.ASH, cx, cy, cz, 80, 0.7, 0.5, 0.7, 0.04);
            server.sendParticles(ParticleTypes.SMOKE, cx, cy, cz, 24, 0.4, 0.3, 0.4, 0.02);
        }
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH,
            SoundSource.BLOCKS, 0.7f, 0.85f);
    }

    @Override
    public ResourceLocation getRegistryName() {
        return ResourceLocation.fromNamespaceAndPath("ars_n_spells", REGISTRY_PATH);
    }
}
