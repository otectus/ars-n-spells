package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.otectus.arsnspells.util.CasterContext;
import com.otectus.arsnspells.util.SpellScalingUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SpellStats.class, remap = false)
public class MixinSpellStatsPotency {
    // Added require = 0 for high-fidelity fallback. Ensures game loads even if external API drifts.
    @Inject(method = "getPotency", at = @At("RETURN"), cancellable = true, require = 0)
    private void arsnspells$applyFinalScaling(CallbackInfoReturnable<Integer> cir) {
        CasterContext.getPlayer().ifPresent(player -> {
            int original = cir.getReturnValue();
            float multiplier = SpellScalingUtil.getMultiplierForCaster(player, CasterContext.getSpell().orElse(null));
            cir.setReturnValue(Math.round(original * multiplier));
        });
    }
}