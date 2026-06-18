package com.otectus.arsnspells.cooldown;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.data.CooldownData;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages unified cooldowns across both Ars Nouveau and Iron's Spells 'n Spellbooks.
 * Tracks cooldowns per category per player to prevent spell spam.
 * Uses Capability-based storage for persistence.
 */
public class UnifiedCooldownManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnifiedCooldownManager.class);
    private static final CooldownTracker CLIENT_TRACKER = new CooldownTracker();

    /**
     * Check if a spell category is on cooldown for a player.
     * @param player The player to check
     * @param category The cooldown category
     * @return True if the category is on cooldown
     */
    public static boolean isOnCooldown(Player player, CooldownCategory category) {
        return isOnCooldown(player, category, "global");
    }

    /**
     * Check if a spell category is on cooldown for a player with mod namespace.
     * @param player The player to check
     * @param category The cooldown category
     * @param modNamespace The mod namespace ("ars", "irons", or "global")
     * @return True if the category is on cooldown
     */
    public static boolean isOnCooldown(Player player, CooldownCategory category, String modNamespace) {
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

        CooldownData data = player.getData(AttachmentTypes.COOLDOWN.get());
        long lastCast = data.getLastCast(category);
        if (lastCast == 0) return false;
        long currentTime = player.level().getGameTime();
        return currentTime < lastCast;
    }

    /**
     * Get the remaining cooldown time for a category.
     * @param player The player to check
     * @param category The cooldown category
     * @return Remaining cooldown in ticks, or 0 if not on cooldown
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

        CooldownData data = player.getData(AttachmentTypes.COOLDOWN.get());
        long cooldownEnd = data.getLastCast(category);
        long currentTime = player.level().getGameTime();
        return Math.max(0, cooldownEnd - currentTime);
    }

    /**
     * Apply cooldown to a category for a player.
     * @param player The player
     * @param category The cooldown category
     * @param isCrossModSpell True if this spell is from the other mod
     */
    public static void applyCooldown(Player player, CooldownCategory category, boolean isCrossModSpell) {
        applyCooldown(player, category, isCrossModSpell, "global");
    }

    /**
     * Apply cooldown to a category for a player with mod namespace.
     * @param player The player
     * @param category The cooldown category
     * @param isCrossModSpell True if this spell is from the other mod
     * @param modNamespace The mod namespace ("ars", "irons", or "global")
     */
    public static void applyCooldown(Player player, CooldownCategory category, boolean isCrossModSpell, String modNamespace) {
        if (!isEnabled()) {
            return;
        }

        if (player == null || category == null) {
            return;
        }

        int baseDuration = AnsConfig.COOLDOWN_CATEGORY_DURATION.get();
        double multiplier = isCrossModSpell ? AnsConfig.CROSS_MOD_COOLDOWN_MULTIPLIER.get() : 1.0;
        long duration = (long) (baseDuration * multiplier);

        long currentTime = player.level().getGameTime();
        long cooldownEnd = currentTime + duration;

        if (player.level().isClientSide()) {
            CLIENT_TRACKER.setLastCastTime(category, cooldownEnd);
        } else {
            player.getData(AttachmentTypes.COOLDOWN.get()).setLastCast(category, cooldownEnd);
        }

        logDebug("Applied cooldown to {} for {} (namespace: {}): {} ticks (cross-mod: {})",
                player.getName().getString(), category.getDisplayName(), modNamespace, duration, isCrossModSpell);
    }

    /**
     * Apply cooldown and return the cooldown end tick.
     */
    public static long applyCooldownAndGetEnd(Player player, CooldownCategory category, boolean isCrossModSpell) {
        return applyCooldownAndGetEnd(player, category, isCrossModSpell, "global");
    }

    /**
     * Apply cooldown and return the cooldown end tick with mod namespace.
     */
    public static long applyCooldownAndGetEnd(Player player, CooldownCategory category, boolean isCrossModSpell, String modNamespace) {
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
            player.getData(AttachmentTypes.COOLDOWN.get()).setLastCast(category, cooldownEnd);
        }

        logDebug("Applied cooldown to {} for {} (namespace: {}): {} ticks (cross-mod: {})",
            player.getName().getString(), category.getDisplayName(), modNamespace, duration, isCrossModSpell);

        return cooldownEnd;
    }

    /**
     * Clear all cooldowns for a player.
     * @param player The player
     */
    public static void clearCooldowns(Player player) {
        if (player != null) {
            CooldownData data = player.getData(AttachmentTypes.COOLDOWN.get());
            for (CooldownCategory cat : CooldownCategory.values()) {
                data.setLastCast(cat, 0);
            }
            logDebug("Cleared all cooldowns for {}", player.getName().getString());
        }
    }

    /**
     * Clear cooldown for a specific category for a player.
     * @param player The player
     * @param category The category to clear
     */
    public static void clearCooldown(Player player, CooldownCategory category) {
        if (player != null && category != null) {
            player.getData(AttachmentTypes.COOLDOWN.get()).setLastCast(category, 0);
            logDebug("Cleared cooldown for category {} for {}",
                category.getDisplayName(), player.getName().getString());
        }
    }

    /**
     * Check if the unified cooldown system is enabled.
     * @return True if enabled
     */
    public static boolean isEnabled() {
        return AnsConfig.ENABLE_COOLDOWN_SYSTEM != null
            && AnsConfig.ENABLE_COOLDOWN_SYSTEM.get()
            && AnsConfig.ENABLE_UNIFIED_COOLDOWNS != null
            && AnsConfig.ENABLE_UNIFIED_COOLDOWNS.get();
    }

    /**
     * Get statistics about the cooldown system.
     * @return A formatted string with statistics
     */
    public static String getStats() {
        return String.format("Unified Cooldown System: %s (Capability Based)",
                           isEnabled() ? "ACTIVE" : "DISABLED");
    }

    /**
     * Clean up expired cooldowns for a player.
     * Not strictly necessary with Capability approach as we just check timestamps,
     * but kept for API compatibility.
     * @param player The player to clean up
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
