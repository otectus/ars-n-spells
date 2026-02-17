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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ManaCap.class, remap = false)
public abstract class MixinManaCapability {
    
    // Shadowing the verified field name: livingEntity
    @Shadow @Final private LivingEntity livingEntity;
    @Shadow private double mana;
    @Shadow private int maxMana;

    @Inject(method = "getCurrentMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$getCurrentMana(CallbackInfoReturnable<Double> cir) {
        if (this.livingEntity instanceof Player player) {
            if (!BridgeManager.isUnificationEnabled()) {
                return;
            }
            double current = (double) BridgeManager.getBridge().getMana(player);
            ManaUnificationMode mode = BridgeManager.getCurrentMode();
            // In HYBRID mode, cap current mana at Ars native max so the HUD
            // doesn't show mana exceeding the displayed max value
            if (mode != null && mode.isHybrid()) {
                current = Math.min(current, this.maxMana);
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
            // In HYBRID mode, let Ars report its own native max mana so the
            // displayed value matches Ars's base pool (150) rather than Iron's (200).
            // The bridge handles actual mana sync between the two systems.
            if (mode != null && mode.isHybrid()) {
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
        // Only redirect in ISS_PRIMARY mode where Iron's is the sole source of truth.
        // In HYBRID mode, Ars manages its own max mana natively to prevent
        // Iron's default (200) from overriding Ars's native max (150).
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
