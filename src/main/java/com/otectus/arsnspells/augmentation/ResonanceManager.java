package com.otectus.arsnspells.augmentation;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.IManaBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


public class ResonanceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResonanceManager.class);
    /** Audit D4: log the first compute failure per session so a broken Iron's API surface isn't invisible. */
    private static final AtomicBoolean loggedComputeFailure = new AtomicBoolean(false);
    // Fixed: Use UUID instead of Player to prevent garbage collection issues
    private static final Map<UUID, ResonanceState> resonanceCache = new ConcurrentHashMap<>();
    /** ANS-HIGH-007 / E-MED-06: volatile so the network-thread write is visible to the render thread. */
    private static volatile double clientResonance = 1.0;

    public static double getResonance(Player player) {
        if (player == null || !AnsConfig.ENABLE_RESONANCE_SYSTEM.get()) {
            return 1.0;
        }
        if (player.level().isClientSide()) {
            return clientResonance;
        }
        return resonanceCache.getOrDefault(player.getUUID(), ResonanceState.INACTIVE).multiplier();
    }

    public static void setClientResonance(float value) {
        // ANS-HIGH-006 (receiver defense-in-depth): packet decode already clamps but
        // we re-check here so any future caller (commands, debug menu) can't corrupt
        // the static field with a NaN.
        if (!Float.isFinite(value)) {
            return;
        }
        clientResonance = Math.max(0.0, Math.min(100.0, (double) value));
    }

    public static double getArsResonance(Player player) {
        return AnsConfig.ENABLE_ARS_RESONANCE.get() ? getResonance(player) : 1.0;
    }

    public static double getIronsResonance(Player player) {
        return AnsConfig.ENABLE_IRONS_RESONANCE.get() ? getResonance(player) : 1.0;
    }

    public static double cachedResonance(Player player) {
        return resonanceCache.getOrDefault(player.getUUID(), ResonanceState.INACTIVE).multiplier();
    }

    public static void computeResonance(Player player) {
        if (player == null) return;
        if (!AnsConfig.ENABLE_RESONANCE_SYSTEM.get() || !BridgeManager.isUnificationEnabled()
                || !BridgeManager.isIronsSpellbooksLoaded()) {
            clear(player);
            return;
        }
        try {
            // Shared modes use their authoritative pool. Separate mode deliberately uses
            // native Ars mana for this global synergy, never a mirrored Iron's shadow.
            IManaBridge bridge = BridgeManager.getCurrentMode() == com.otectus.arsnspells.config.ManaUnificationMode.SEPARATE
                ? BridgeManager.getNativeArsBridge() : BridgeManager.getBridge();
            ResonanceState next = ResonanceState.update(resonanceCache.get(player.getUUID()),
                player.level().getGameTime(), bridge.getMana(player), bridge.getMaxMana(player),
                AnsConfig.RESONANCE_THRESHOLD.get(), AnsConfig.RESONANCE_DURATION.get(),
                AnsConfig.RESONANCE_STRENGTH.get(), AnsConfig.MAX_DAMAGE_MULTIPLIER.get());
            resonanceCache.put(player.getUUID(), next);
        } catch (Exception e) {
            clear(player);
            if (loggedComputeFailure.compareAndSet(false, true)) {
                LOGGER.warn("[ANS] Resonance computation failed; using neutral resonance", e);
            } else {
                LOGGER.debug("[ANS] Resonance computation failed", e);
            }
        }
    }
    public static void clear(Player player) {
        if (player != null) {
            resonanceCache.remove(player.getUUID());
        }
    }

    /**
     * Remove cache entries for players not currently online.
     * Call periodically to prevent memory leaks from disconnected players.
     */
    public static void cleanupOfflinePlayers(MinecraftServer server) {
        if (server == null) return;
        // ANS-LOW-018: removeIf with a direct getPlayer lookup avoids allocating a
        // Set<UUID> just for the contains check. The lookup is O(1) on Minecraft's
        // PlayerList map, so this is also faster for large player counts.
        resonanceCache.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
    }

    /**
     * Clear all cached resonance values. Call on server stop.
     */
    public static void clearAll() {
        resonanceCache.clear();
        clientResonance = 1.0;
    }
}
