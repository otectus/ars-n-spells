package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.common.capability.ManaData;
import com.otectus.arsnspells.glyph.GlyphKnowledgeBonus;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Normalizes the glyph-knowledge count Ars reads for maximum mana and regeneration, so the level
 * variants of one imported Iron's spell add at most one glyph's worth ({@link GlyphKnowledgeBonus}).
 *
 * <p>{@code ManaCap} is Ars's only {@code IManaCap}, and {@code getGlyphBonus()} is the read
 * {@code ManaUtil.calcMaxMana} and {@code getManaRegen} make. Read-only: the stored count
 * ({@code ManaData}) and the known-glyph list are never changed, so relogs, syncs and equipment
 * changes cannot inflate or erode it. Ars's learning code compares this value with the known-glyph
 * count and then stores the raw count, which is unaffected.
 */
@Mixin(value = ManaCap.class, remap = false)
public abstract class MixinManaCapGlyphBonus {
    @Shadow private ManaData manaData;
    @Shadow LivingEntity entity;

    @Inject(method = "getGlyphBonus", at = @At("HEAD"), cancellable = true)
    private void arsnspells$normalizeImportedVariants(CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof Player player && manaData != null) {
            cir.setReturnValue(GlyphKnowledgeBonus.effective(player, manaData.getGlyphBonus()));
        }
    }
}
