package com.otectus.arsnspells.mixin.ars;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.ManaUnificationMode;
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

    // Prevents Ars native regeneration only in ISS_PRIMARY mode where Iron's is
    // the sole mana source. In HYBRID mode, Ars tick must run to properly
    // initialize and maintain its native max mana value (150).
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void arsnspells$suppressNativeRegen(CallbackInfo ci) {
        if (this.livingEntity instanceof Player) {
            ManaUnificationMode mode = BridgeManager.getCurrentMode();
            if (mode != null && mode.isIssPrimary()) {
                ci.cancel();
            }
        }
    }
}
