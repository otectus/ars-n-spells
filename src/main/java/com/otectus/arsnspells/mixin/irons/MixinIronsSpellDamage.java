package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.config.AnsConfig;
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
            // enable_irons_resonance is the Iron's half of the per-direction toggle the
            // config advertised and nothing read. flag(), not get(): this runs on the client
            // render path too (the spell wheel and the inscription table both call
            // getSpellPower), where the SERVER config may not be loaded and get() throws
            // straight into the render loop.
            if (!AnsConfig.flag(AnsConfig.ENABLE_IRONS_RESONANCE, true)) {
                return;
            }
            float spellPower = cir.getReturnValue();
            double resonanceMultiplier = ResonanceManager.getResonance(player);
            cir.setReturnValue((float) (spellPower * resonanceMultiplier));
        }
    }

    // The canBeCastedBy -> MagicData.getMana() redirect that used to live here moved
    // to MixinIronsCastValidation, which is the declared single owner of that call
    // site. Two @Redirects on one call site do not stack - Mixin silently keeps only
    // one - so the ARS_PRIMARY conversion and the (Covenant) ring bypass have to be
    // folded into one handler. Do not re-add a redirect here.
}
