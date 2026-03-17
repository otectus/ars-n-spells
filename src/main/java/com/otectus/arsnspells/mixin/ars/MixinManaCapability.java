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

    /**
     * Recursion guard: prevents infinite recursion when bridge == ArsNativeBridge
     * (ARS_PRIMARY mode), since ArsNativeBridge.getMana() calls cap.getCurrentMana()
     * which would trigger this mixin again.
     */
    @Unique
    private static final ThreadLocal<Boolean> arsnspells$inBridgeCall = ThreadLocal.withInitial(() -> false);

    @Inject(method = "getCurrentMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$getCurrentMana(CallbackInfoReturnable<Double> cir) {
        if (arsnspells$inBridgeCall.get()) {
            return; // Recursion guard: let native method run
        }
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        // In ARS_PRIMARY, Ars is the source of truth — let native ManaCap handle it.
        // This avoids infinite recursion since ArsNativeBridge.getMana() calls
        // cap.getCurrentMana() which would re-enter this mixin.
        if (mode != null && mode.isArsPrimary()) {
            return;
        }
        try {
            arsnspells$inBridgeCall.set(true);
            double current = (double) BridgeManager.getBridge().getMana(player);
            if (mode != null && mode.isHybrid()) {
                int cap = arsnspells$arsNativeMaxMana > 0 ? arsnspells$arsNativeMaxMana : this.maxMana;
                current = Math.min(current, cap);
            }
            cir.setReturnValue(current);
        } finally {
            arsnspells$inBridgeCall.set(false);
        }
    }

    @Inject(method = "getMaxMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$getMaxMana(CallbackInfoReturnable<Integer> cir) {
        if (arsnspells$inBridgeCall.get()) {
            return; // Recursion guard: let native method run
        }
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        // In ARS_PRIMARY, Ars is the source of truth — let native ManaCap handle it.
        if (mode != null && mode.isArsPrimary()) {
            return;
        }
        if (mode != null && mode.isHybrid()) {
            if (arsnspells$arsNativeMaxMana > 0) {
                cir.setReturnValue(arsnspells$arsNativeMaxMana);
            }
            return;
        }
        try {
            arsnspells$inBridgeCall.set(true);
            cir.setReturnValue((int) BridgeManager.getBridge().getMaxMana(player));
        } finally {
            arsnspells$inBridgeCall.set(false);
        }
    }

    /**
     * FIX: Do NOT forward ManaCap writes to Iron's MagicData.
     *
     * Spell consumption is handled separately by MixinSpellResolverMana → BridgeManager
     * → IronsBridge.consumeMana(), which bypasses ManaCap entirely. Any other calls to
     * setMana() are Ars-internal (capability init, clone, NBT deserialization, max-mana
     * clamp) and must NOT overwrite Iron's mana with stale values.
     *
     * Instead, we sync the shadow field from Iron's current value (read-only) so that
     * any direct field reads in Ars see a consistent value, then cancel the original
     * method to prevent Ars from clamping against its own stale maxMana.
     */
    @Inject(method = "setMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$setMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }
        if (!arsnspells$shouldIntercept()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        // Read-only sync: update shadow field from Iron's actual value.
        // Do NOT write 'amount' to Iron's — that would overwrite Iron's real mana
        // with stale Ars-internal values (typically 0).
        try {
            arsnspells$inBridgeCall.set(true);
            double ironsCurrentMana = (double) BridgeManager.getBridge().getMana(player);
            this.mana = ironsCurrentMana;
            cir.setReturnValue(ironsCurrentMana);
        } finally {
            arsnspells$inBridgeCall.set(false);
        }
    }

    @Inject(method = "addMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$addMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }
        if (!arsnspells$shouldIntercept()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        // Read-only sync: do NOT add to Iron's mana here.
        // Ars regen is cancelled in ISS_PRIMARY (MixinArsManaRegen), and in HYBRID
        // mode Iron's handles its own regen. Any addMana calls from Ars internal code
        // (e.g. potion effects restoring Ars mana) should not affect Iron's pool.
        try {
            arsnspells$inBridgeCall.set(true);
            double ironsCurrentMana = (double) BridgeManager.getBridge().getMana(player);
            this.mana = ironsCurrentMana;
            cir.setReturnValue(ironsCurrentMana);
        } finally {
            arsnspells$inBridgeCall.set(false);
        }
    }

    @Inject(method = "removeMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$removeMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }
        if (!arsnspells$shouldIntercept()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        // Read-only sync: do NOT remove from Iron's mana here.
        // Spell consumption goes through MixinSpellResolverMana → BridgeManager →
        // IronsBridge.consumeMana() directly, bypassing ManaCap. Any other removeMana
        // calls from Ars are internal bookkeeping and should not affect Iron's pool.
        try {
            arsnspells$inBridgeCall.set(true);
            double ironsCurrentMana = (double) BridgeManager.getBridge().getMana(player);
            this.mana = ironsCurrentMana;
            cir.setReturnValue(ironsCurrentMana);
        } finally {
            arsnspells$inBridgeCall.set(false);
        }
    }

    @Inject(method = "setMaxMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$setMaxMana(int amount, CallbackInfo ci) {
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        // In HYBRID mode, capture the Ars native max value but let Ars set it normally.
        if (mode != null && mode.isHybrid()) {
            arsnspells$arsNativeMaxMana = amount;
            return; // Let Ars set its own maxMana natively
        }
        // Only redirect in ISS_PRIMARY mode where Iron's is the sole source of truth.
        if (mode == null || !mode.isIssPrimary()) {
            return;
        }
        try {
            arsnspells$inBridgeCall.set(true);
            this.maxMana = (int) BridgeManager.getBridge().getMaxMana(player);
        } finally {
            arsnspells$inBridgeCall.set(false);
        }
        ci.cancel();
    }

    /**
     * Check if this ManaCap operation should be intercepted (ISS_PRIMARY or HYBRID).
     * Unlike the old shouldRedirectToIrons(), this does NOT imply writing to Iron's.
     */
    @Unique
    private boolean arsnspells$shouldIntercept() {
        if (!BridgeManager.isUnificationEnabled()) {
            return false;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        return mode != null && (mode.isIssPrimary() || mode.isHybrid());
    }
}
