package com.otectus.arsnspells.mixin.ars;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.hollingsworth.arsnouveau.common.event.ManaCapEvents;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ManaCapEvents.class, remap = false)
public abstract class MixinArsManaRegen {

    /**
     * Prevents Ars native regeneration only in ISS_PRIMARY mode where Iron's is
     * the sole mana source. In HYBRID mode, Ars regen must run to properly
     * initialize and maintain its native max mana value.
     *
     * FIX: Also checks isUnificationEnabled() so that disabling unification
     * (via config toggle or DISABLED mode) immediately restores native Ars regen
     * without requiring a restart.
     */
    @Inject(method = "playerOnTick", at = @At("HEAD"), cancellable = true)
    private static void arsnspells$suppressNativeRegen(PlayerTickEvent event, CallbackInfo ci) {
        if (!BridgeManager.isUnificationEnabled()) {
            return; // Unification disabled — let Ars regen run normally
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode != null && mode.isIssPrimary()) {
            ci.cancel();
        }
    }
}
