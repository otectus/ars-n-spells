package com.otectus.arsnspells.events;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles equipment change events to update unified mana bonuses
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class EquipmentHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EquipmentHandler.class);
    
    /**
     * Handle equipment changes
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        // Clear equipment cache to force recalculation
        EquipmentIntegration.clearCache(player);
        
        // Recalculate max mana based on new equipment
        updatePlayerMaxMana(player);
        
        logDebug("Equipment changed for {}, recalculating mana bonuses", player.getName().getString());
    }
    
    /**
     * Handle player login to initialize equipment bonuses
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
            return;
        }
        
        Player player = event.getEntity();
        
        // Initialize equipment bonuses
        updatePlayerMaxMana(player);
        
        logDebug("Player {} logged in, initializing equipment bonuses", player.getName().getString());
    }
    
    /**
     * Handle player respawn to restore equipment bonuses
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
            return;
        }
        
        Player player = event.getEntity();
        
        // Clear cache and recalculate
        EquipmentIntegration.clearCache(player);
        updatePlayerMaxMana(player);
        
        logDebug("Player {} respawned, recalculating equipment bonuses", player.getName().getString());
    }

    /**
     * Handle player logout to clear cached equipment data
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EquipmentIntegration.clearCache(event.getEntity());
    }
    
    /**
     * Update player's max mana based on equipment
     */
    private static void updatePlayerMaxMana(Player player) {
        try {
            if (player.level().isClientSide()) {
                return;
            }

            ManaUnificationMode mode = BridgeManager.getCurrentMode();
            if (!BridgeManager.isUnificationEnabled() || mode == null) {
                EquipmentIntegration.clearArsBonusesFromIrons(player);
                return;
            }

            if (!AnsConfig.respectArmorBonuses.get()) {
                EquipmentIntegration.clearArsBonusesFromIrons(player);
                return;
            }

            if (mode.isIssPrimary() || mode.isHybrid()) {
                double conversionRate = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
                EquipmentIntegration.applyArsBonusesToIrons(player, conversionRate);
                EquipmentIntegration.ManaBonus arsBonus = EquipmentIntegration.getArsManaBonuses(player);
                logDebug("Applied Ars gear bonuses to Iron's mana for {}: max={}, regen={}",
                    player.getName().getString(), arsBonus.maxMana, arsBonus.manaRegen);
            } else {
                EquipmentIntegration.clearArsBonusesFromIrons(player);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to update max mana for player {}", player.getName().getString(), e);
        }
    }
    
    /**
     * Log debug message if debug mode is enabled
     */
    private static void logDebug(String message, Object... args) {
        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[EquipmentHandler] [DEBUG] " + message, args);
        }
    }
}
