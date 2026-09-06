package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts every online player back into a consistent state when the mana mode or the feature
 * toggles change under them (audit V14).
 *
 * <p>Closes the finding that a mode or config change bumped {@link BridgeManager}'s routing
 * generation and nothing else. The per-player transient state - the Iron's max-mana ceiling,
 * the mirrored gear and potion bonuses, the progression spell-power modifier - was only ever
 * rebuilt by an equipment change, a login or a respawn, and every one of those rebuild paths
 * was itself gated on the feature being enabled. Disable a feature and its bonus stayed on the
 * player until they next died; enable one and the ceiling stayed missing until they next
 * swapped a helmet.
 *
 * <p>The sequence per player is fixed and is the whole point:
 *
 * <ol>
 *   <li><b>removeAll</b> - {@link AnsFeatureCleanup} strips every ANS-owned modifier under
 *       every current and legacy identity, with no gate at all.</li>
 *   <li><b>recompute</b> - the features that are <em>now</em> enabled re-apply their
 *       contributions from scratch.</li>
 *   <li><b>clamp</b> - the migration rule below is applied to the current pool.</li>
 *   <li><b>synchronize</b> - the clamped value is written back through the owning mod's own
 *       setter, which is that mod's client-sync path.</li>
 * </ol>
 *
 * <p><b>The migration rule.</b> A mode change can lower a player's maximum. The pool is clamped
 * into {@code [0, newMax]} and the delta is logged; it is never raised to meet a higher new
 * maximum, because mana the player never had is not theirs, and never driven below zero. This
 * touches transient state only - no capability data is rewritten and
 * {@code AnsDataVersion} does not move.
 *
 * <p><b>Progression data is preserved.</b> Only the transient attribute modifier is removed;
 * the persisted per-school cast counts are untouched, so re-enabling the feature restores the
 * same bonus rather than starting the player at zero.
 *
 * <p>MOD bus, because {@code ModConfigEvent} is a mod-bus event. The command path calls
 * {@link #reconcileAll()} directly.
 */
@Mod.EventBusSubscriber(modid = ArsNSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModeChangeCleanup {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModeChangeCleanup.class);

    /** Below this, a clamp delta is float noise rather than a mana loss worth reporting. */
    private static final float CLAMP_LOG_THRESHOLD = 0.01f;

    private ModeChangeCleanup() {
    }

    /**
     * A config file edit or {@code /reload}. {@code BridgeManager.refreshMode} has already run
     * from {@code ArsNSpells}, so the routing generation is current by the time this lands.
     */
    @SubscribeEvent
    public static void onConfigReloading(final ModConfigEvent.Reloading event) {
        if (!ArsNSpells.MODID.equals(event.getConfig().getModId())) {
            return;
        }
        reconcileAll();
    }

    /**
     * Reconcile every online player. Safe to call when no server is running (config reloads
     * can fire on the client before a world exists) and safe to call repeatedly.
     */
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

    /** The four steps, in order, for one player. */
    public static void reconcile(ServerPlayer player) {
        // 1. removeAll - ungated, so a feature that has just been turned off still has its
        //    modifier taken away by something that is still running.
        int removed = AnsFeatureCleanup.removeAll(player);

        // 2. Recompute whatever is enabled now. Both helpers below re-read the live config and
        //    the fresh routing snapshot, and both are no-ops for a feature that is off.
        com.otectus.arsnspells.events.EquipmentHandler.recomputeContributions(player);
        com.otectus.arsnspells.events.ProgressionHandler.reapplyAllBonuses(player);

        // 3 + 4. Clamp under the migration rule, then write it back through the owning mod.
        clampAndSynchronize(player, removed);
    }

    /**
     * Clamp the authoritative pool into {@code [0, max]} and write it back.
     *
     * <p>The write is the synchronize step: {@code IManaBridge.setMana} goes through the owning
     * mod's own setter, which is what ships the new value to the client. Writing unconditionally
     * (rather than only when the clamp bit) is deliberate - after a mode switch the client's
     * last-known value came from the <em>other</em> pool.
     */
    private static void clampAndSynchronize(ServerPlayer player, int removedModifiers) {
        ResourceUnit unit = BridgeManager.getRoutingSnapshot().authoritativeUnit();
        IManaBridge bridge = BridgeManager.getBridge();
        float current = bridge.getMana(player);
        float max = bridge.getMaxMana(player);
        if (Float.isNaN(current) || Float.isNaN(max) || max < 0.0f) {
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
