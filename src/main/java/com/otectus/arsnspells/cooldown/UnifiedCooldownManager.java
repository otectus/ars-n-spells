package com.otectus.arsnspells.cooldown;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.CooldownData;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages unified cooldowns across both Ars Nouveau and Iron's Spells 'n Spellbooks.
 * Tracks cooldowns per category per player to prevent spell spam.
 * Uses Capability-based storage for persistence.
 *
 * <p><b>Cooldowns are global per category</b> — they intentionally span both mods.
 * A spell on cooldown in {@link CooldownCategory#OFFENSIVE} blocks any other
 * OFFENSIVE spell regardless of which mod cast first. Earlier versions of this
 * class accepted a {@code modNamespace} parameter that suggested per-mod
 * isolation, but it was never part of the storage key — only the debug log.
 * The parameter was removed in 1.9.0 to make the surface match the behavior.
 */
public class UnifiedCooldownManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnifiedCooldownManager.class);
    private static final CooldownTracker CLIENT_TRACKER = new CooldownTracker();

    /**
     * Check if a spell category is on cooldown for a player.
     */
    public static boolean isOnCooldown(Player player, CooldownCategory category) {
        if (!isEnabled()) {
            return false;
        }

        if (player == null || category == null) {
            return false;
        }

        if (player.level().isClientSide()) {
            long currentTime = player.level().getGameTime();
            return CLIENT_TRACKER.isOnCooldown(category, currentTime);
        }

        return player.getCapability(CooldownData.COOLDOWN_CAP).map(data -> {
            long lastCast = data.getLastCast(category);
            if (lastCast == 0) return false;
            // Cooldown end tick is stored in the capability
            long currentTime = player.level().getGameTime();
            return currentTime < lastCast;
        }).orElse(false);
    }

    /**
     * Get the remaining cooldown time for a category.
     */
    public static long getRemainingCooldown(Player player, CooldownCategory category) {
        if (!isEnabled()) {
            return 0;
        }

        if (player == null || category == null) {
            return 0;
        }

        if (player.level().isClientSide()) {
            long cooldownEnd = CLIENT_TRACKER.getLastCastTime(category);
            long currentTime = player.level().getGameTime();
            return Math.max(0, cooldownEnd - currentTime);
        }

        return player.getCapability(CooldownData.COOLDOWN_CAP).map(data -> {
            long cooldownEnd = data.getLastCast(category);
            long currentTime = player.level().getGameTime();
            return Math.max(0, cooldownEnd - currentTime);
        }).orElse(0L);
    }

    /**
     * Apply cooldown to a category for a player.
     */
    public static void applyCooldown(Player player, CooldownCategory category, boolean isCrossModSpell) {
        applyCooldownAndGetEnd(player, category, isCrossModSpell);
    }

    /**
     * Apply cooldown and return the cooldown end tick.
     */
    public static long applyCooldownAndGetEnd(Player player, CooldownCategory category, boolean isCrossModSpell) {
        if (!isEnabled() || player == null || category == null) {
            return 0L;
        }
        int baseDuration = AnsConfig.COOLDOWN_CATEGORY_DURATION.get();
        double multiplier = isCrossModSpell ? AnsConfig.CROSS_MOD_COOLDOWN_MULTIPLIER.get() : 1.0;
        long duration = (long) (baseDuration * multiplier);
        long currentTime = player.level().getGameTime();
        long cooldownEnd = currentTime + duration;

        if (player.level().isClientSide()) {
            CLIENT_TRACKER.setLastCastTime(category, cooldownEnd);
        } else {
            player.getCapability(CooldownData.COOLDOWN_CAP).ifPresent(data -> {
                data.setLastCast(category, cooldownEnd);
            });
        }

        logDebug("Applied cooldown to {} for {}: {} ticks (cross-mod: {})",
            player.getName().getString(), category.getDisplayName(), duration, isCrossModSpell);

        return cooldownEnd;
    }

    /**
     * Clear all cooldowns for a player.
     */
    public static void clearCooldowns(Player player) {
        if (player != null) {
            player.getCapability(CooldownData.COOLDOWN_CAP).ifPresent(data -> {
                for (CooldownCategory cat : CooldownCategory.values()) {
                    data.setLastCast(cat, 0);
                }
            });
            logDebug("Cleared all cooldowns for {}", player.getName().getString());
        }
    }

    /**
     * Clear cooldown for a specific category for a player.
     */
    public static void clearCooldown(Player player, CooldownCategory category) {
        if (player != null && category != null) {
            player.getCapability(CooldownData.COOLDOWN_CAP).ifPresent(data -> {
                data.setLastCast(category, 0);
            });
            logDebug("Cleared cooldown for {} category {} for {}",
                    category.getDisplayName(), player.getName().getString());
        }
    }

    /**
     * Check if the unified cooldown system is enabled.
     */
    public static boolean isEnabled() {
        return AnsConfig.ENABLE_COOLDOWN_SYSTEM != null
            && AnsConfig.ENABLE_COOLDOWN_SYSTEM.get()
            && AnsConfig.ENABLE_UNIFIED_COOLDOWNS != null
            && AnsConfig.ENABLE_UNIFIED_COOLDOWNS.get();
    }

    /**
     * Get statistics about the cooldown system.
     */
    public static String getStats() {
        return String.format("Unified Cooldown System: %s (Capability Based, global-per-category)",
                           isEnabled() ? "ACTIVE" : "DISABLED");
    }

    /**
     * Clean up expired cooldowns for a player.
     * Not strictly necessary with Capability approach as we just check timestamps,
     * but kept for API compatibility.
     */
    public static void cleanupExpiredCooldowns(Player player) {
        // No-op for capability implementation
    }

    /**
     * Update the client tracker from a sync packet.
     */
    public static void setClientCooldownEnd(CooldownCategory category, long cooldownEnd) {
        if (category != null) {
            CLIENT_TRACKER.setLastCastTime(category, cooldownEnd);
        }
    }

    /**
     * Access the client-side tracker (for legacy callers).
     */
    public static CooldownTracker getClientTracker() {
        return CLIENT_TRACKER;
    }

    /**
     * Log debug message if debug mode is enabled.
     */
    private static void logDebug(String message, Object... args) {
        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[Cooldown] [DEBUG] " + message, args);
        }
    }
}
