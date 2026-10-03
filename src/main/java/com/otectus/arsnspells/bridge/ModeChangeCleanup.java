package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.events.CapabilityResyncHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts every online player back into a consistent state when the mana mode or the feature
 * toggles change under them (audit V14). Same sequence as the Forge 1.20.1 build.
 *
 * <p>The sequence per player is fixed and is the whole point:
 *
 * <ol>
 *   <li><b>removeAll</b> - {@link AnsFeatureCleanup} strips every ANS-owned modifier under
 *       every current and legacy identity, with no gate at all.</li>
 *   <li><b>recompute</b> - the features that are <em>now</em> enabled re-apply their
 *       contributions from scratch (equipment, progression and the client snapshots through
 *       {@link CapabilityResyncHandler#resync}, then the ISS_PRIMARY potion redirect).</li>
 *   <li><b>clamp</b> - the migration rule below is applied to the current pool.</li>
 *   <li><b>synchronize</b> - the clamped value is sent through the owning mod's own sync.</li>
 * </ol>
 *
 * <p><b>The migration rule.</b> A mode change can lower a player's maximum. The pool is clamped
 * into {@code [0, newMax]} and the delta is logged; it is never raised to meet a higher new
 * maximum, because mana the player never had is not theirs, and never driven below zero.
 *
 * <p><b>Progression data is preserved.</b> Only the transient attribute modifier is removed;
 * the persisted per-school cast counts are untouched.
 *
 * <p>{@link BridgeManager#refreshMode()} runs {@link #reconcileAll()}, so config load, config
 * reload and {@code /ans mode set} all reconcile.
 */
public final class ModeChangeCleanup {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModeChangeCleanup.class);

    /** Below this, a clamp is float noise from the round trip and not worth a log line. */
    private static final float CLAMP_LOG_THRESHOLD = 0.01f;

    private ModeChangeCleanup() {}

    /** Reconcile every online player. Hops to the server thread if called from elsewhere. */
    public static void reconcileAll() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        if (!server.isSameThread()) {
            server.execute(ModeChangeCleanup::reconcileAll);
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                reconcile(player);
            } catch (Exception e) {
                LOGGER.error("Failed to reconcile {} after a mode or config change",
                    player.getName().getString(), e);
            }
        }
    }

    /** removeAll, recompute, clamp, synchronize - for one player. */
    public static void reconcile(ServerPlayer player) {
        int removed = AnsFeatureCleanup.removeAll(player);
        // Replays live equipment, progression and the client snapshots.
        CapabilityResyncHandler.resync(player);
        if (com.otectus.arsnspells.compat.IronsCompat.isLoaded()) {
            com.otectus.arsnspells.equipment.PotionContributions.reconcile(player);
        }
        clampAndSynchronize(player, removed);
        NativeManaSync.send(player);
    }

    private static void clampAndSynchronize(ServerPlayer player, int removedModifiers) {
        ResourceUnit unit = BridgeManager.getRoutingSnapshot().authoritativeUnit();
        IManaBridge bridge = BridgeManager.getBridge();
        float current = bridge.getMana(player);
        float max = bridge.getMaxMana(player);
        if (!Float.isFinite(current) || !Float.isFinite(max) || max < 0.0f) {
            return;
        }
        float clamped = Math.max(0.0f, Math.min(current, max));
        if (Math.abs(clamped - current) >= CLAMP_LOG_THRESHOLD) {
            LOGGER.info("Mode/config change clamped {}'s {} pool from {} to {} (new max {}, "
                    + "delta {}); {} stale ANS modifier(s) were removed first",
                player.getName().getString(), unit, current, clamped, max, clamped - current,
                removedModifiers);
        }
        bridge.setMana(player, clamped);
    }
}
