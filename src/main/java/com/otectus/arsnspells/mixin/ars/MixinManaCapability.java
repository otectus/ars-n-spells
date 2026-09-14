package com.otectus.arsnspells.mixin.ars;

import com.otectus.arsnspells.bridge.ArsRegenTickScope;
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
    private static final ThreadLocal<java.util.Map<java.util.UUID, java.util.EnumSet<Direction>>>
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
    private enum Direction {
        /** Reading a balance or ceiling out of the authoritative pool. */
        READ,
        /** Moving the authoritative pool. */
        WRITE
    }

    @Unique
    private boolean arsnspells$enterGuard(Player player, Direction direction) {
        return arsnspells$inBridgeCall.get()
            .computeIfAbsent(player.getUUID(), k -> java.util.EnumSet.noneOf(Direction.class))
            .add(direction);
    }

    @Unique
    private boolean arsnspells$isGuarded(Player player, Direction direction) {
        java.util.EnumSet<Direction> held = arsnspells$inBridgeCall.get().get(player.getUUID());
        return held != null && held.contains(direction);
    }

    @Unique
    private void arsnspells$exitGuard(Player player, Direction direction) {
        java.util.Map<java.util.UUID, java.util.EnumSet<Direction>> held = arsnspells$inBridgeCall.get();
        java.util.EnumSet<Direction> directions = held.get(player.getUUID());
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
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }
        if (arsnspells$isGuarded(player, Direction.READ)) {
            return; // Recursion guard for THIS player and direction — let native method run
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
        arsnspells$enterGuard(player, Direction.READ);
        try {
            double current = (double) BridgeManager.getBridge().getMana(player);
            if (mode != null && mode.isHybrid()) {
                int cap = arsnspells$arsNativeMaxMana > 0 ? arsnspells$arsNativeMaxMana : this.maxMana;
                current = Math.min(current, cap);
            }
            cir.setReturnValue(current);
        } finally {
            arsnspells$exitGuard(player, Direction.READ);
        }
    }

    @Inject(method = "getMaxMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$getMaxMana(CallbackInfoReturnable<Integer> cir) {
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }
        if (arsnspells$isGuarded(player, Direction.READ)) {
            return; // Recursion guard for THIS player and direction — let native method run
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
        arsnspells$enterGuard(player, Direction.READ);
        try {
            cir.setReturnValue((int) BridgeManager.getBridge().getMaxMana(player));
        } finally {
            arsnspells$exitGuard(player, Direction.READ);
        }
    }

    /**
     * {@code setMana} stays a read-only sync, deliberately.
     *
     * <p>V06 routed the two <em>delta</em> mutators ({@code addMana}, {@code removeMana}) to
     * the authoritative pool, because a delta is unambiguous: {@code +50} means fifty more
     * mana whoever asked. An absolute {@code setMana} is not. In the pinned Ars 4.12.7 the
     * calls that reach this site are Ars-internal - capability attach, {@code playerClone},
     * {@code deserializeNBT}, the max-mana clamp - and they carry a value computed against
     * Ars's own stale pool, typically zero. Forwarding those would overwrite the shared pool
     * with a number nobody asked for, which is a strictly worse bug than the one V06 names,
     * and there is no information at this site that separates them from a third-party set.
     *
     * <p>So the shadow field is synced from the authoritative pool (read-only) and the
     * original is cancelled, which keeps Ars's direct field reads consistent and stops it
     * clamping against its own stale {@code maxMana}. Third-party code that wants to set an
     * absolute value can express it as a delta through {@code addMana}/{@code removeMana},
     * which now work.
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
        arsnspells$enterGuard(player, Direction.READ);
        try {
            double ironsCurrentMana = (double) BridgeManager.getBridge().getMana(player);
            this.mana = ironsCurrentMana;  // Sync shadow field from Iron's for consistency
            cir.setReturnValue(amount);     // Return requested value to satisfy API contract
        } finally {
            arsnspells$exitGuard(player, Direction.READ);
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
        if (!(this.livingEntity instanceof Player player)) {
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
            this.mana = unchanged;
            cir.setReturnValue(unchanged);
            return;
        }
        // Injection two: every other caller is a legitimate mutation and is routed.
        if (!arsnspells$enterGuard(player, Direction.WRITE)) {
            // A bridge write is already in flight for this player and re-entered us. Let
            // native ManaCap run so the shadow field still moves; routing again doubles it.
            return;
        }
        try {
            if (amount != 0.0d) {
                BridgeManager.getBridge().addMana(player, (float) amount);
            }
            double current = (double) BridgeManager.getBridge().getMana(player);
            this.mana = current;
            cir.setReturnValue(current);
        } finally {
            arsnspells$exitGuard(player, Direction.WRITE);
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
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }
        if (!arsnspells$shouldIntercept()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        if (!arsnspells$enterGuard(player, Direction.WRITE)) {
            return;
        }
        try {
            if (amount > 0.0d) {
                BridgeManager.getBridge().addMana(player, (float) -amount);
            }
            double current = (double) BridgeManager.getBridge().getMana(player);
            this.mana = current;
            cir.setReturnValue(current);
        } finally {
            arsnspells$exitGuard(player, Direction.WRITE);
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
        arsnspells$enterGuard(player, Direction.READ);
        try {
            this.maxMana = (int) BridgeManager.getBridge().getMaxMana(player);
        } finally {
            arsnspells$exitGuard(player, Direction.READ);
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
