package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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
            float spellPower = cir.getReturnValue();
            double resonanceMultiplier = ResonanceManager.getResonance(player);
            cir.setReturnValue((float) (spellPower * resonanceMultiplier));
        }
    }

    @Redirect(
        method = "canBeCastedBy",
        at = @At(
            value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;getMana()F"
        ),
        require = 0
    )
    private float arsnspells$scaleManaForConversion(
        MagicData data,
        int spellLevel,
        CastSource castSource,
        MagicData magicData,
        Player player
    ) {
        float mana = data.getMana();
        if (!BridgeManager.isUnificationEnabled()) {
            return mana;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode != ManaUnificationMode.ARS_PRIMARY) {
            return mana;
        }
        double rate = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
        if (rate <= 0.0) {
            return mana;
        }
        return (float) (mana / rate);
    }
}
