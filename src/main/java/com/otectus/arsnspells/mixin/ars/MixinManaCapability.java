package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.common.capability.ManaData;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.ManaMutationRouter;
import com.otectus.arsnspells.casting.BridgeResourceAccess;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@link ManaCap}'s public read/write surface to route mana
 * through {@link BridgeManager} when the active mana-unification mode
 * sends Ars's pool through Iron's MagicData. Updated for Ars Nouveau 5.x:
 *
 * <ul>
 *   <li>Field rename {@code livingEntity} → {@code entity} (package-private).</li>
 *   <li>Direct {@code mana}/{@code maxMana} fields were replaced with a
 *       {@link ManaData} sub-object; field-sync writes go through
 *       {@code manaData.setMana(…)} / {@code manaData.setMaxMana(…)}.</li>
 * </ul>
 */
@Mixin(value = ManaCap.class, remap = false)
public abstract class MixinManaCapability {

    @Shadow LivingEntity entity;
    @Shadow private ManaData manaData;

    /**
     * Tracks the Ars Nouveau native max mana value in HYBRID mode.
     * This prevents Iron's default (200) from leaking into the displayed max.
     * Captured when Ars calls setMaxMana() during capability initialization.
     */
    @Unique
    private int arsnspells$arsNativeMaxMana = -1;

    /**
     * Recursion guard: prevents infinite recursion when the authoritative bridge is
     * {@code ArsNativeBridge} (ARS_PRIMARY), since {@code ArsNativeBridge.getMana()} calls
     * {@code cap.getCurrentMana()} and would re-enter this mixin.
     *
     * <p>ANS-HIGH-010 keyed it by player: a thread-global flag meant that while ANY player's
     * bridge call was in flight, ManaCap interception was suppressed for EVERY player on that
     * thread, so an AoE or party-share spell that reads another player's mana fell through to
     * native Ars data and bypassed the bridge entirely.
     *
     * <p>Audit V06 adds the second dimension: a <b>direction</b>. Routing a write ends up
     * reading the pool back to report the new balance, and with one flag per player that read
     * looked like recursion and was dropped through to stale native state. A read nested inside
     * a write is normal; only a write inside a write is recursion. The guard now lives in
     * {@link ManaMutationRouter} so the mixin and the routing share one notion of "in flight".
     */
    @Unique
    private static boolean arsnspells$isGuarded(Player player) {
        return ManaMutationRouter.isGuarded(player.getUUID(), ManaMutationRouter.Direction.READ);
    }

    @Unique
    private static void arsnspells$enterGuard(Player player) {
        ManaMutationRouter.enter(player.getUUID(), ManaMutationRouter.Direction.READ);
    }

    @Unique
    private static void arsnspells$exitGuard(Player player) {
        ManaMutationRouter.exit(player.getUUID(), ManaMutationRouter.Direction.READ);
    }

    @Inject(method = "getCurrentMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$getCurrentMana(CallbackInfoReturnable<Double> cir) {
        if (!(this.entity instanceof Player player)) {
            return;
        }
        if (arsnspells$isGuarded(player)) {
            return; // Recursion guard: let native method run
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
            arsnspells$enterGuard(player);
            double current = (double) BridgeManager.getBridge().getMana(player);
            if (mode != null && mode.isHybrid()) {
                int cap = arsnspells$arsNativeMaxMana > 0
                    ? arsnspells$arsNativeMaxMana
                    : this.manaData.getMaxMana();
                current = Math.min(current, cap);
            }
            cir.setReturnValue(current);
        } finally {
            arsnspells$exitGuard(player);
        }
    }

    @Inject(method = "getMaxMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$getMaxMana(CallbackInfoReturnable<Integer> cir) {
        if (!(this.entity instanceof Player player)) {
            return;
        }
        if (arsnspells$isGuarded(player)) {
            return; // Recursion guard: let native method run
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
            arsnspells$enterGuard(player);
            cir.setReturnValue((int) BridgeManager.getBridge().getMaxMana(player));
        } finally {
            arsnspells$exitGuard(player);
        }
    }

    /**
     * {@code setMana} stays a read-only sync, deliberately, and is the one mutation not routed.
     *
     * <p>Audit V06 asks that legitimate mutations reach the authoritative pool, and {@code
     * addMana} / {@code removeMana} now do. {@code setMana} is different in kind: it is an
     * <em>absolute assignment</em>, and every caller of it inside Ars is state restoration -
     * capability construction, {@code clone}, NBT deserialization on login and dimension
     * change, and the max-mana clamp. Those callers pass whatever Ars last had, typically
     * {@code 0} on a fresh capability, and nothing in the signature distinguishes them from a
     * third party setting a balance on purpose. Forwarding would therefore zero the shared pool
     * every time a player crossed a portal.
     *
     * <p>So the sub-object is synced from the real pool (so Ars's own field reads stay
     * consistent) and the native method is cancelled to stop Ars clamping against its own stale
     * state. A third party wanting to change the balance has {@code addMana} / {@code
     * removeMana}, which are routed.
     */
    @Inject(method = "setMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$setMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(this.entity instanceof Player player)) {
            return;
        }
        if (!arsnspells$shouldIntercept()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        // Read-only sync: update ManaData from Iron's actual value.
        // Do NOT write 'amount' to Iron's — that would overwrite Iron's real mana
        // with stale Ars-internal values (typically 0).
        try {
            arsnspells$enterGuard(player);
            double ironsCurrentMana = (double) BridgeManager.getBridge().getMana(player);
            this.manaData.setMana(ironsCurrentMana);  // Sync sub-object for Ars internal consistency
            cir.setReturnValue(amount);               // Return requested value to satisfy API contract
        } finally {
            arsnspells$exitGuard(player);
        }
    }

    /**
     * Route an additive mutation to the authoritative pool (audit V06).
     *
     * <p>This used to be a blanket no-op, which is how Ars's regeneration was suppressed - and
     * how every other grant of Ars mana was suppressed along with it. Regeneration is now
     * suppressed at its own call site by {@code MixinManaCapEventsRegen}, so a {@code +50} that
     * arrives here is a real, legitimate grant from somewhere else and lands in the pool that
     * actually holds this player's mana.
     *
     * <p>Returns the balance after the add, which is {@code ManaCap.addMana}'s own contract in
     * the pinned Ars 5.13.1.1400 ({@code addMana(D)D}).
     */
    @Inject(method = "addMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$addMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(this.entity instanceof Player player)) {
            return;
        }
        if (!arsnspells$shouldIntercept()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        if (ManaMutationRouter.isGuarded(
                player.getUUID(), ManaMutationRouter.Direction.REGEN_TICK)) {
            // Inside Ars's own regeneration tick, which MixinManaCapEventsRegen brackets. This
            // is THE call that regenerates, and in a shared-pool mode Iron's already regenerates
            // that pool - adding here too would mint mana. Report the unchanged balance, which
            // is what ManaCap.addMana contracts to return, and let the tick's client sync run.
            double current = BridgeManager.getBridge().getMana(player);
            this.manaData.setMana(current);
            cir.setReturnValue(current);
            return;
        }
        double now = ManaMutationRouter.routeAdd(player.getUUID(),
            ManaMutationRouter.authoritativeUnit(), BridgeResourceAccess.of(player), amount);
        this.manaData.setMana(now);  // Keep the sub-object consistent for Ars's own field reads.
        cir.setReturnValue(now);
    }

    /**
     * Route a subtractive mutation to the authoritative pool.
     *
     * <p>Spell consumption does not arrive here - it goes through the resolver mixin and the
     * attempt ledger - so anything that does is a third-party drain, and dropping it made those
     * effects free in exactly the modes where mana matters most.
     */
    @Inject(method = "removeMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$removeMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(this.entity instanceof Player player)) {
            return;
        }
        if (!arsnspells$shouldIntercept()) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        double now = ManaMutationRouter.routeRemove(player.getUUID(),
            ManaMutationRouter.authoritativeUnit(), BridgeResourceAccess.of(player), amount);
        this.manaData.setMana(now);
        cir.setReturnValue(now);
    }

    @Inject(method = "setMaxMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$setMaxMana(int amount, CallbackInfo ci) {
        if (!(this.entity instanceof Player player)) {
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
            arsnspells$enterGuard(player);
            this.manaData.setMaxMana((int) BridgeManager.getBridge().getMaxMana(player));
        } finally {
            arsnspells$exitGuard(player);
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
