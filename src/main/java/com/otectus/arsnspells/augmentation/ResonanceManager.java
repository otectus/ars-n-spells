package com.otectus.arsnspells.augmentation;

import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ResonanceManager {
    // Fixed: Use UUID instead of Player to prevent garbage collection issues
    private static final Map<UUID, Double> resonanceCache = new ConcurrentHashMap<>();
    /** ANS-HIGH-007 / E-MED-06: volatile so the network-thread write is visible to the render thread. */
    private static volatile double clientResonance = 1.0;

    public static double getResonance(Player player) {
        if (player == null || !AnsConfig.ENABLE_RESONANCE_SYSTEM.get()) {
            return 1.0;
        }
        if (player.level().isClientSide()) {
            return clientResonance;
        }
        return resonanceCache.getOrDefault(player.getUUID(), 1.0);
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

    public static void computeResonance(Player player) {
        try {
            if (player == null || !AnsConfig.ENABLE_RESONANCE_SYSTEM.get()) {
                return;
            }
            if (!ModList.get().isLoaded("irons_spellbooks")) {
                return;
            }
            MagicData data = MagicData.getPlayerMagicData(player);
            if (data == null) {
                return;
            }
            double maxMana = player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
            // ANS-HIGH-007: clamp manaPercent to [0,1]. Iron's MagicData.getMana() can
            // briefly exceed maxMana from external buff scripts or attribute injection;
            // without the clamp, resonance scales unboundedly into spell damage.
            double rawPercent = data.getMana() / Math.max(1.0, maxMana);
            double manaPercent = Math.max(0.0, Math.min(1.0, rawPercent));
            double strength = AnsConfig.RESONANCE_STRENGTH.get();
            double cap = AnsConfig.MAX_DAMAGE_MULTIPLIER.get();

            // Cap the final resonance to the documented MAX_DAMAGE_MULTIPLIER ceiling.
            double resonance = Math.min(cap, 1.0 + (manaPercent * strength * 0.2));
            if (!Double.isFinite(resonance)) resonance = 1.0;
            resonanceCache.put(player.getUUID(), resonance);
        } catch (Exception e) {
            // Silently fail if Iron's API is unavailable
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
        Set<UUID> onlineUUIDs = server.getPlayerList().getPlayers().stream()
            .map(p -> p.getUUID())
            .collect(Collectors.toSet());
        resonanceCache.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    }

    /**
     * Clear all cached resonance values. Call on server stop.
     */
    public static void clearAll() {
        resonanceCache.clear();
        clientResonance = 1.0;
    }
}
