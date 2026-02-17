package com.otectus.arsnspells.mixin.ars;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ManaCap.class, remap = false)
public abstract class MixinManaCapability {

    // Shadowing the verified field name: livingEntity
    @Shadow @Final private LivingEntity livingEntity;
    @Shadow private double mana;
    @Shadow private int maxMana;

    /**
     * Tracks the Ars Nouveau native max mana value in HYBRID mode.
     * This prevents Iron's default (200) from leaking into the displayed max.
     * Captured when Ars calls setMaxMana() during capability initialization.
     */
    @Unique
    private int arsnspells$arsNativeMaxMana = -1;

    @Inject(method = "getCurrentMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$getCurrentMana(CallbackInfoReturnable<Double> cir) {
        if (this.livingEntity instanceof Player player) {
            if (!BridgeManager.isUnificationEnabled()) {
                return;
            }
            double current = (double) BridgeManager.getBridge().getMana(player);
            ManaUnificationMode mode = BridgeManager.getCurrentMode();
            // In HYBRID mode, cap current mana at the Ars native max so the HUD
            // doesn't show mana exceeding the displayed max value.
            // Use the captured native max if available, otherwise fall back to shadow field.
            if (mode != null && mode.isHybrid()) {
                int cap = arsnspells$arsNativeMaxMana > 0 ? arsnspells$arsNativeMaxMana : this.maxMana;
                current = Math.min(current, cap);
            }
            cir.setReturnValue(current);
        }
    }

    @Inject(method = "getMaxMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$getMaxMana(CallbackInfoReturnable<Integer> cir) {
        if (this.livingEntity instanceof Player player) {
            if (!BridgeManager.isUnificationEnabled()) {
                return;
            }
            ManaUnificationMode mode = BridgeManager.getCurrentMode();
            // In HYBRID mode, report the captured Ars native max mana (typically 150)
            // rather than Iron's value (200). If not yet captured, let Ars report natively.
            if (mode != null && mode.isHybrid()) {
                if (arsnspells$arsNativeMaxMana > 0) {
                    cir.setReturnValue(arsnspells$arsNativeMaxMana);
                }
                return;
            }
            cir.setReturnValue((int) BridgeManager.getBridge().getMaxMana(player));
        }
    }

    @Inject(method = "setMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$setMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }
        if (!shouldRedirectToIrons()) {
            return;
        }
        BridgeManager.getBridge().setMana(player, (float) amount);
        double updated = BridgeManager.getBridge().getMana(player);
        this.mana = updated;
        cir.setReturnValue(updated);
        cir.cancel();
    }

    @Inject(method = "addMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$addMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }
        if (!shouldRedirectToIrons()) {
            return;
        }
        float current = BridgeManager.getBridge().getMana(player);
        BridgeManager.getBridge().setMana(player, current + (float) amount);
        double updated = BridgeManager.getBridge().getMana(player);
        this.mana = updated;
        cir.setReturnValue(updated);
        cir.cancel();
    }

    @Inject(method = "removeMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$removeMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }
        if (!shouldRedirectToIrons()) {
            return;
        }
        if (amount < 0) {
            amount = 0;
        }
        BridgeManager.getBridge().consumeMana(player, (float) amount);
        double updated = BridgeManager.getBridge().getMana(player);
        this.mana = updated;
        cir.setReturnValue(updated);
        cir.cancel();
    }

    @Inject(method = "setMaxMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$setMaxMana(int amount, CallbackInfo ci) {
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        // In HYBRID mode, capture the Ars native max value but let Ars set it normally.
        // This prevents Iron's default (200) from overriding Ars's native max (150).
        if (mode != null && mode.isHybrid()) {
            arsnspells$arsNativeMaxMana = amount;
            return; // Let Ars set its own maxMana natively
        }
        // Only redirect in ISS_PRIMARY mode where Iron's is the sole source of truth.
        if (mode == null || !mode.isIssPrimary()) {
            return;
        }
        this.maxMana = (int) BridgeManager.getBridge().getMaxMana(player);
        ci.cancel();
    }

    private boolean shouldRedirectToIrons() {
        if (!BridgeManager.isUnificationEnabled()) {
            return false;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        return mode != null && (mode.isIssPrimary() || mode.isHybrid());
    }
}
