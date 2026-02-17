package com.otectus.arsnspells.augmentation;

import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResonanceManager {
    // Fixed: Use UUID instead of Player to prevent garbage collection issues
    private static final Map<UUID, Double> resonanceCache = new ConcurrentHashMap<>();
    private static double clientResonance = 1.0;

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
        clientResonance = (double) value;
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
            double manaPercent = data.getMana() / Math.max(1.0, maxMana);
            double strength = AnsConfig.RESONANCE_STRENGTH.get();
            
            // Scaling Iron's spell damage based on current mana % and mod progress
            double resonance = 1.0 + (manaPercent * strength * 0.2);
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
}
