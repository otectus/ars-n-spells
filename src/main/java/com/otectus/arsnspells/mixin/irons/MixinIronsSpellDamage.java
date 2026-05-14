package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.augmentation.ResonanceManager;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to apply resonance multiplier to Iron's Spellbooks spell power.
 *
 * Updated for Iron's Spellbooks 3.15.2:
 * - Changed from getDamage(int, LivingEntity) to getSpellPower(int, Entity)
 * - getSpellPower is the public method in AbstractSpell that calculates spell effectiveness
 * - This affects all spell damage, healing, and other power-based calculations
 *
 * Note: a previous {@code @Redirect} of {@code MagicData.getMana()} inside
 * {@code canBeCastedBy} (named {@code arsnspells$scaleManaForConversion}) used to live
 * here, but it collided at the same call site with the ring-bypass redirect in
 * {@link MixinIronsCastValidation}. Both behaviours (ARS_PRIMARY cross-conversion
 * scaling AND the ring bypass) are now folded into the single redirect over there.
 * Do NOT add another {@code @Redirect} on {@code MagicData.getMana()} inside
 * {@code canBeCastedBy} from this class — Mixin will silently skip one of them.
 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class MixinIronsSpellDamage {
    /**
     * Apply resonance multiplier to spell power calculations.
     *
     * Method signature in Iron's Spellbooks 3.15.2:
     * public float getSpellPower(int spellLevel, Entity sourceEntity)
     *
     * This method is called by all spells to calculate their effectiveness,
     * making it the perfect injection point for global damage scaling.
     */
    @Inject(method = "getSpellPower", at = @At("RETURN"), cancellable = true, require = 0)
    private void arsnspells$applyResonanceMultiplier(int spellLevel, Entity sourceEntity, CallbackInfoReturnable<Float> cir) {
        if (sourceEntity instanceof Player player) {
            float spellPower = cir.getReturnValue();
            double resonanceMultiplier = ResonanceManager.getResonance(player);
            cir.setReturnValue((float) (spellPower * resonanceMultiplier));
        }
    }
}
