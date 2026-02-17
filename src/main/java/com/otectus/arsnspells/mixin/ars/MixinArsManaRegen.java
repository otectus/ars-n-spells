package com.otectus.arsnspells.mixin.ars;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ManaCap.class, remap = false)
public abstract class MixinArsManaRegen {

    @Shadow @Final private LivingEntity livingEntity;

    // Prevents Ars native regeneration if the bridge is using Iron's Spells mana.
    // This ensures only one mana pool manages recovery.
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void arsnspells$suppressNativeRegen(CallbackInfo ci) {
        if (this.livingEntity instanceof Player) {
            if (BridgeManager.getBridge().getBridgeType().equals("IRONS_SPELLS")) {
                // Cancellation logic: Let Iron's handles the mana tick.
                ci.cancel();
            }
        }
    }
}