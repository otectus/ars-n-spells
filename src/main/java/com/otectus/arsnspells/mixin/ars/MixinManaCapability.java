package com.otectus.arsnspells.mixin.ars;

import com.otectus.arsnspells.bridge.ArsRegenTickScope;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.ManaAccessDirection;
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
public abstract class MixinManaCapability implements com.otectus.arsnspells.bridge.ManaOwnerBinding {

    @Shadow @Final private LivingEntity livingEntity;
    @Shadow private double mana;
    @Shadow private int maxMana;

    @Unique private LivingEntity arsnspells$attachedOwner;
    @Override public void arsnspells$bindOwner(LivingEntity owner) { arsnspells$attachedOwner = owner; }
    @Unique private LivingEntity arsnspells$owner() { return livingEntity != null ? livingEntity : arsnspells$attachedOwner; }

    /**
     * Tracks the Ars Nouveau native max mana value in HYBRID mode.
     * This prevents Iron's default (200) from leaking into the displayed max.
     * Captured when Ars calls setMaxMana() during capability initialization.
     */
    @Unique
    private int arsnspells$arsNativeMaxMana = -1;

    /**
     * Recursion guard: prevents infinite recursion when bridge == ArsNativeBridge
     * (ARS_PRIMARY mode), since {@code ArsNativeBridge.getMana()} calls
     * {@code cap.getCurrentMana()} which would trigger this mixin again.
     *
     * <p>ANS-HIGH-010: per-player UUID set instead of a global boolean. The previous
     * design used {@code ThreadLocal<Boolean>}, which meant ANY in-flight bridge call
     * on the current thread blocked all other ManaCap operations for ALL players —
     * so AoE / party-share spells that read other players' mana while one player's
     * bridge call was active would silently fall through to native ManaCap data,
     * bypassing the bridge.
     */
    @Unique
    private static final ThreadLocal<java.util.Map<java.util.UUID, java.util.EnumSet<ManaAccessDirection>>>
        arsnspells$inBridgeCall = ThreadLocal.withInitial(java.util.HashMap::new);

    /**
     * Which way a guarded bridge call is going (audit V06).
     *
     * <p>The guard is per player <em>and</em> per direction. A single shared flag meant that
     * a write routed to the pool blocked the read the very same call needs to report its
     * result, so the mutation fell through to native {@code ManaCap} and moved the wrong
     * pool. Reads and writes now guard independently and only against themselves.
     */
    @Unique
    private boolean arsnspells$enterGuard(Player player, ManaAccessDirection direction) {
        return arsnspells$inBridgeCall.get()
            .computeIfAbsent(player.getUUID(), k -> java.util.EnumSet.noneOf(ManaAccessDirection.class))
            .add(direction);
    }

    @Unique
    private boolean arsnspells$isGuarded(Player player, ManaAccessDirection direction) {
        java.util.EnumSet<ManaAccessDirection> held = arsnspells$inBridgeCall.get().get(player.getUUID());
        return held != null && held.contains(direction);
    }

    @Unique
    private void arsnspells$exitGuard(Player player, ManaAccessDirection direction) {
        java.util.Map<java.util.UUID, java.util.EnumSet<ManaAccessDirection>> held = arsnspells$inBridgeCall.get();
        java.util.EnumSet<ManaAccessDirection> directions = held.get(player.getUUID());
        if (directions != null) {
            directions.remove(direction);
            if (directions.isEmpty()) {
                held.remove(player.getUUID());
            }
        }
        if (held.isEmpty()) {
            // Avoid ThreadLocal leak on long-lived threads.
            arsnspells$inBridgeCall.remove();
        }
    }

    @Inject(method = "getCurrentMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$getCurrentMana(CallbackInfoReturnable<Double> cir) {
        if (!(arsnspells$owner() instanceof Player player)) {
            return;
        }
        if (com.otectus.arsnspells.bridge.NativeManaAccess.active(player, com.otectus.arsnspells.contract.ResourceUnit.ARS_MANA) || arsnspells$hydrating || arsnspells$isGuarded(player, ManaAccessDirection.READ)) {
            return; // Recursion guard for THIS player and direction — let native method run
        }
        if (arsnspells$hydrating || !BridgeManager.isUnificationEnabled()) {
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
        arsnspells$enterGuard(player, ManaAccessDirection.READ);
        try {
            double current = (double) BridgeManager.getBridge().getMana(player);
            if (mode != null && mode.isHybrid()) {
                int cap = arsnspells$arsNativeMaxMana > 0 ? arsnspells$arsNativeMaxMana : this.maxMana;
                current = Math.min(current, cap);
            }
            cir.setReturnValue(current);
        } finally {
            arsnspells$exitGuard(player, ManaAccessDirection.READ);
        }
    }

    @Inject(method = "getMaxMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$getMaxMana(CallbackInfoReturnable<Integer> cir) {
        if (!(arsnspells$owner() instanceof Player player)) {
            return;
        }
        if (com.otectus.arsnspells.bridge.NativeManaAccess.active(player, com.otectus.arsnspells.contract.ResourceUnit.ARS_MANA) || arsnspells$hydrating || arsnspells$isGuarded(player, ManaAccessDirection.READ)) {
            return; // Recursion guard for THIS player and direction — let native method run
        }
        if (arsnspells$hydrating || !BridgeManager.isUnificationEnabled()) {
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
        arsnspells$enterGuard(player, ManaAccessDirection.READ);
        try {
            cir.setReturnValue((int) BridgeManager.getBridge().getMaxMana(player));
        } finally {
            arsnspells$exitGuard(player, ManaAccessDirection.READ);
        }
    }

    /** Native NBT hydration restores the dormant Ars shadow, not the active shared pool. */
    @Unique private boolean arsnspells$hydrating;

    @Inject(method = "serializeNBT()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void arsnspells$persistNativeBalance(CallbackInfoReturnable<net.minecraft.nbt.CompoundTag> cir) {
        // Persistence owns the dormant native state. Ars's PacketUpdateMana reads the
        // public getters separately, so preserving disk state does not hide the shared HUD.
        if (arsnspells$owner() instanceof Player player && !player.level().isClientSide()) {
            cir.getReturnValue().putDouble("current", mana);
            cir.getReturnValue().putInt("max", maxMana);
        }
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("HEAD"))
    private void arsnspells$beginHydration(net.minecraft.nbt.CompoundTag tag, CallbackInfo ci) {
        arsnspells$hydrating = true;
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void arsnspells$endHydration(net.minecraft.nbt.CompoundTag tag, CallbackInfo ci) {
        arsnspells$hydrating = false;
    }

    /** Public absolute writes honor their value; only the identified native hydration is scoped out. */
    @Inject(method = "setMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$setMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(arsnspells$owner() instanceof Player player) || player.level().isClientSide()
                || !arsnspells$shouldIntercept() || arsnspells$hydrating) return;
        if (!Double.isFinite(amount)) {
            cir.setReturnValue((double) BridgeManager.getBridge().getMana(player));
            return;
        }
        if (!arsnspells$enterGuard(player, ManaAccessDirection.WRITE)) return;
        try {
            BridgeManager.getBridge().setMana(player, (float) Math.max(0, Math.min(Float.MAX_VALUE, amount)));
            cir.setReturnValue((double) BridgeManager.getBridge().getMana(player));
        } finally {
            arsnspells$exitGuard(player, ManaAccessDirection.WRITE);
        }
    }
    /**
     * Route an {@code addMana} to the shared authoritative pool (audit V06).
     *
     * <p>This used to be a blanket no-op, because it was doubling as the suppressor for
     * Ars's native regen tick. That suppression now lives at the one caller that needs it
     * ({@link MixinManaRegenTick}), so this site is free to do what its API says: a
     * third-party {@code addMana(+50)} moves the authoritative pool by 50 and returns the
     * resulting balance. Previously it moved nothing and reported success, so a mana potion
     * or an addon's refund vanished.
     *
     * <p>Return-value semantics in the pinned Ars 4.12.7: {@code addMana}, {@code removeMana}
     * and {@code setMana} all return {@code getCurrentMana()} <em>after</em> the write - the
     * resulting balance clamped into {@code [0, maxMana]}, never the delta and never the
     * requested value.
     */
    @Inject(method = "addMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$addMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(arsnspells$owner() instanceof Player player)) {
            return;
        }
        if (!arsnspells$shouldIntercept()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        if (ArsRegenTickScope.isInRegenTickFor(player.getUUID())
                && BridgeManager.ironsOwnsSharedPool()) {
            // Injection one of the V06 split: Ars's own regeneration tick, and only that
            // caller, is suppressed - Iron's already regenerates this pool.
            // MixinManaRegenTick is what names the scope. Report the unchanged balance,
            // which is exactly what a zero-sized regen tick would have returned.
            double unchanged = (double) BridgeManager.getBridge().getMana(player);
            cir.setReturnValue(unchanged);
            return;
        }
        // Injection two: every other caller is a legitimate mutation and is routed.
        if (!arsnspells$enterGuard(player, ManaAccessDirection.WRITE)) {
            // A bridge write is already in flight for this player and re-entered us. Let
            // native ManaCap run so the shadow field still moves; routing again doubles it.
            return;
        }
        try {
            if (Double.isFinite(amount) && amount != 0.0d) {
                BridgeManager.getBridge().addMana(player, (float) Math.max(-Float.MAX_VALUE, Math.min(Float.MAX_VALUE, amount)));
            }
            double current = (double) BridgeManager.getBridge().getMana(player);
            cir.setReturnValue(current);
        } finally {
            arsnspells$exitGuard(player, ManaAccessDirection.WRITE);
        }
    }

    /**
     * Route a {@code removeMana} to the shared authoritative pool.
     *
     * <p>Same finding as {@link #arsnspells$addMana}: a drain that reported success and
     * removed nothing. Spell consumption does not come through here - it goes through the
     * cast ledger - so anything reaching this site is a third-party or Ars-item mutation
     * that genuinely means to move the pool.
     */
    @Inject(method = "removeMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$removeMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(arsnspells$owner() instanceof Player player)) {
            return;
        }
        if (!arsnspells$shouldIntercept()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        if (!arsnspells$enterGuard(player, ManaAccessDirection.WRITE)) {
            return;
        }
        try {
            if (Double.isFinite(amount) && amount > 0.0d) {
                BridgeManager.getBridge().addMana(player, (float) -Math.min(Float.MAX_VALUE, amount));
            }
            double current = (double) BridgeManager.getBridge().getMana(player);
            cir.setReturnValue(current);
        } finally {
            arsnspells$exitGuard(player, ManaAccessDirection.WRITE);
        }
    }

    @Inject(method = "setMaxMana", at = @At("HEAD"))
    private void arsnspells$setMaxMana(int amount, CallbackInfo ci) {
        // Keep the real Ars ceiling in its own field. Shared display reads are routed
        // independently; a raw native snapshot must not contain Iron's ceiling.
        arsnspells$arsNativeMaxMana = amount;
    }
    /**
     * Check if this ManaCap operation should be intercepted (ISS_PRIMARY or HYBRID).
     * Unlike the old shouldRedirectToIrons(), this does NOT imply writing to Iron's.
     */
    @Unique
    private boolean arsnspells$shouldIntercept() {
        if (arsnspells$owner() instanceof Player p && com.otectus.arsnspells.bridge.NativeManaAccess.active(p,
                com.otectus.arsnspells.contract.ResourceUnit.ARS_MANA)) return false;
        if (arsnspells$hydrating || !BridgeManager.isUnificationEnabled()) {
            return false;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        return mode != null && (mode.isIssPrimary() || mode.isHybrid());
    }
}
