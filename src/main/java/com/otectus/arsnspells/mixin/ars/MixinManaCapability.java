package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.common.capability.ManaData;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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

    @Inject(method = "syncToClient", at = @At("HEAD"), cancellable = true)
    private void arsnspells$syncSharedView(net.minecraft.server.level.ServerPlayer player, CallbackInfo ci) {
        if (!BridgeManager.ironsOwnsSharedPool()) return;
        com.hollingsworth.arsnouveau.common.network.Networking.sendToPlayerClient(
            new com.hollingsworth.arsnouveau.common.network.PacketUpdateMana(
                com.otectus.arsnspells.bridge.ArsManaDisplay.snapshot(player, manaData)), player);
        ci.cancel();
    }

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
     *
     * <p>ANS-HIGH-010: keyed by player, not a single boolean. A thread-global flag meant that
     * while ANY player's bridge call was in flight, ManaCap interception was suppressed for
     * EVERY player on that thread - so an AoE or party-share spell that reads another player's
     * mana mid-call fell through to native Ars data and bypassed the bridge entirely. The
     * recursion this guards against is always same-player, so the key makes it exact.
     */
    @Unique
    private static final ThreadLocal<Set<UUID>> arsnspells$inBridgeCall =
        ThreadLocal.withInitial(HashSet::new);

    @Unique
    private static boolean arsnspells$isGuarded(Player player) {
        return com.otectus.arsnspells.bridge.NativeManaAccess.active(player,
            com.otectus.arsnspells.contract.ResourceUnit.ARS_MANA)
            || arsnspells$inBridgeCall.get().contains(player.getUUID());
    }

    @Unique
    private static void arsnspells$enterGuard(Player player) {
        arsnspells$inBridgeCall.get().add(player.getUUID());
    }

    @Unique
    private static void arsnspells$exitGuard(Player player) {
        Set<UUID> active = arsnspells$inBridgeCall.get();
        active.remove(player.getUUID());
        if (active.isEmpty()) {
            // Avoid a ThreadLocal leak on long-lived server threads.
            arsnspells$inBridgeCall.remove();
        }
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

    /** Public mana writes honor the requested operation; actual native regen is scoped separately. */
    @Inject(method = "setMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$setMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(entity instanceof Player player) || player.level().isClientSide() || !arsnspells$shouldIntercept()) return;
        arsnspells$enterGuard(player);
        try {
            if (Double.isFinite(amount)) BridgeManager.getBridge().setMana(player,
                (float) Math.max(0, Math.min(Float.MAX_VALUE, amount)));
            double result = BridgeManager.getBridge().getMana(player);

            cir.setReturnValue(result);
        } finally { arsnspells$exitGuard(player); }
    }

    @Inject(method = "addMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$addMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(entity instanceof Player player) || player.level().isClientSide() || !arsnspells$shouldIntercept()) return;
        arsnspells$enterGuard(player);
        try {
            boolean nativeRegen = com.otectus.arsnspells.bridge.ArsRegenTickScope.isInRegenTickFor(player.getUUID());
            if (!nativeRegen && Double.isFinite(amount)) BridgeManager.getBridge().addMana(player,
                (float) Math.max(-Float.MAX_VALUE, Math.min(Float.MAX_VALUE, amount)));
            double result = BridgeManager.getBridge().getMana(player);

            cir.setReturnValue(result);
        } finally { arsnspells$exitGuard(player); }
    }

    @Inject(method = "removeMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$removeMana(double amount, CallbackInfoReturnable<Double> cir) {
        if (!(entity instanceof Player player) || player.level().isClientSide() || !arsnspells$shouldIntercept()) return;
        arsnspells$enterGuard(player);
        try {
            if (Double.isFinite(amount) && amount > 0) BridgeManager.getBridge().addMana(player,
                (float) -Math.min(Float.MAX_VALUE, amount));
            double result = BridgeManager.getBridge().getMana(player);

            cir.setReturnValue(result);
        } finally { arsnspells$exitGuard(player); }
    }

    @Inject(method = "setMaxMana", at = @At("HEAD"))
    private void arsnspells$setMaxMana(int amount, CallbackInfo ci) {
        // Keep the native ceiling as native data; getMaxMana supplies the shared display view.
        arsnspells$arsNativeMaxMana = amount;
    }

    /**
     * Check if this ManaCap operation should be intercepted (ISS_PRIMARY or HYBRID).
     * Unlike the old shouldRedirectToIrons(), this does NOT imply writing to Iron's.
     */
    @Unique
    private boolean arsnspells$shouldIntercept() {
        if (entity instanceof Player player && arsnspells$isGuarded(player)) return false;
        if (!BridgeManager.isUnificationEnabled()) {
            return false;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        return mode != null && (mode.isIssPrimary() || mode.isHybrid());
    }
}
