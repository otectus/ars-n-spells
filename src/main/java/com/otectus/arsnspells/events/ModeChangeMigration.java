package com.otectus.arsnspells.events;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.IManaBridge;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import com.otectus.arsnspells.modifier.AnsFeatureCleanup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Brings every online player onto a new routing snapshot after the mana mode changes
 * (audit V14).
 *
 * <p><b>Why a migration and not a recompute.</b> Turning a feature off, or switching to a mode
 * that does not use it, used to leave the modifiers the old mode had applied sitting on the
 * player: the recompute paths are all gated on the feature being enabled, so the disable case
 * ran nothing at all. The order below is what makes the transition total rather than
 * incremental, and it is the order for a reason:
 *
 * <ol>
 *   <li><b>removeAll</b> - unconditionally, through {@link AnsFeatureCleanup}, so what the old
 *       mode applied is gone whether or not the new mode would have applied it.</li>
 *   <li><b>recompute</b> - only the contributions the <em>new</em> mode enables. Each of these
 *       is itself gated, which is correct now that it can only ever add.</li>
 *   <li><b>clamp</b> - see {@link #clampMana}.</li>
 *   <li><b>synchronize</b> - the client mirror last, so it reflects the finished state rather
 *       than an intermediate one.</li>
 * </ol>
 *
 * <p>Driven off {@link BridgeManager#routingGeneration()}, which bumps on every publish. Both
 * entry points - the MOD-bus {@code ModConfigEvent.Reloading} listener in {@code ArsNSpells}
 * and {@code /ans mode set} - call {@link #onRoutingChanged()} after
 * {@code BridgeManager.refreshMode()}, and the generation check makes the second of two calls
 * for the same snapshot a no-op rather than a second full migration.
 */
public final class ModeChangeMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModeChangeMigration.class);

    /** The generation already migrated to. 0 is "none", matching an unpublished snapshot. */
    private static volatile int migratedGeneration;

    private ModeChangeMigration() {}

    /**
     * Migrate every online player onto the current routing snapshot, once per generation.
     *
     * <p>Called after the snapshot has been republished, never before: the recompute step reads
     * the new mode. Safe with no server (a client-side config reload before world join) and
     * safe with no players.
     */
    public static void onRoutingChanged() {
        int generation = BridgeManager.routingGeneration();
        if (generation == migratedGeneration) {
            return;
        }
        migratedGeneration = generation;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        for (ServerPlayer player : players) {
            try {
                migrate(player);
            } catch (Throwable t) {
                // One player's broken state must not abort the migration for the rest.
                LOGGER.error("Mode-change migration failed for {}",
                    player.getName().getString(), t);
            }
        }
    }

    /** Reset the generation gate. Server shutdown only; the next boot republishes from 0. */
    public static void reset() {
        migratedGeneration = 0;
    }

    /**
     * The four steps for one player, in the order the class javadoc fixes.
     *
     * <p>Public because the online-player loop is not the only way in: the GameTest drives a
     * {@code FakePlayer}, which is never on the player list.
     */
    public static void migrate(ServerPlayer player) {
        AnsFeatureCleanup.removeAll(player);

        ProgressionHandler.reapplyAll(player);
        EquipmentIntegration.recomputeFor(player);

        clampMana(player);

        CapabilityResyncHandler.syncClientState(player);
    }

    /**
     * Bring the pool inside the bounds the new mode gives it.
     *
     * <p><b>The migration rule, stated once.</b> A mode change can lower the maximum - dropping
     * a shared-pool mode takes the ANS ceiling modifier with it - and Iron's
     * {@code MagicData.setMana} clamps every write down to that maximum, so a pool left above
     * it is not capped on the next write, it is silently deleted. Mana is therefore moved
     * <em>never above the new maximum and never below zero</em>, and only ever downward: a
     * mode change is not a refill, so a player who was at 30/100 stays at 30 when the maximum
     * rises. The delta is logged because it is the one place this mod destroys mana on
     * purpose, and an unexplained drop after a config edit is otherwise indistinguishable from
     * a bug.
     */
    private static void clampMana(ServerPlayer player) {
        IManaBridge bridge = BridgeManager.getBridge();
        if (bridge == null) {
            return;
        }
        float mana = bridge.getMana(player);
        float max = bridge.getMaxMana(player);
        if (!(max > 0.0f)) {
            // No usable ceiling (bridge not ready, or NaN): clamping to it would delete the
            // whole pool, which is precisely the outcome this method exists to prevent.
            return;
        }
        float clamped = Math.max(0.0f, Math.min(mana, max));
        if (clamped != mana) {
            LOGGER.info("Mode change clamped {}'s mana {} -> {} (new max {})",
                player.getName().getString(), mana, clamped, max);
            bridge.setMana(player, clamped);
        }
    }
}
