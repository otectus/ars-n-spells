package com.otectus.arsnspells.cooldown;

import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event handler for Ars Nouveau spell casting events.
 * Intercepts spell casts and applies unified cooldowns.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class ArsSpellCastHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArsSpellCastHandler.class);

    /**
     * Handle Ars Nouveau spell casting.
     * This method should be called from a mixin or event handler that intercepts Ars spell casts.
     *
     * @param player The player casting the spell
     * @param spellId The spell identifier (glyph registry name)
     * @return true if the spell can be cast, false if on cooldown
     */
    public static boolean onSpellCast(Player player, String spellId) {
        if (!UnifiedCooldownManager.isEnabled()) {
            return true; // Allow casting if system is disabled
        }

        CooldownCategory category = SpellCategoryMapper.getArsCategory(spellId);
        if (category == null) {
            // Spell not mapped to any category, allow casting
            logDebug("Ars spell '{}' not mapped to any category, allowing cast", spellId);
            return true;
        }

        if (UnifiedCooldownManager.isOnCooldown(player, category)) {
            long remaining = UnifiedCooldownManager.getRemainingCooldown(player, category);
            logDebug("Ars spell '{}' blocked - category '{}' on cooldown for {} ticks",
                    spellId, category.getDisplayName(), remaining);
            return false; // Block the cast
        }

        // Apply cooldown - this is a same-mod cast (Ars to Ars)
        UnifiedCooldownManager.applyCooldown(player, category, false);
        logDebug("Ars spell '{}' cast - applied cooldown to category '{}'", spellId, category.getDisplayName());

        return true; // Allow the cast
    }

    /**
     * Log debug message if debug mode is enabled.
     */
    private static void logDebug(String message, Object... args) {
        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[ArsSpellHandler] [DEBUG] " + message, args);
        }
    }
}
