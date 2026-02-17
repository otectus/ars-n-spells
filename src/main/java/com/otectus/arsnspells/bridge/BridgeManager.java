package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * BridgeManager - Central hub for mana system integration
 * 
 * Manages the active mana bridge based on configuration and available mods.
 * Supports 5 different mana unification modes.
 */
public class BridgeManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static IManaBridge activeBridge;
    private static IManaBridge secondaryBridge; // For hybrid/separate modes
    private static ManaUnificationMode currentMode;
    private static boolean isIronsLoaded = false;

    public static void init(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Check if Iron's Spellbooks is loaded
            isIronsLoaded = ModList.get().isLoaded("irons_spellbooks");
            
            // Get configured mode
            currentMode = AnsConfig.getManaMode();
            
            // Initialize bridges based on mode
            initializeBridges();
            
            // Log initialization
            logInitialization();
        });
    }

    /**
     * Initialize mana bridges based on configuration mode
     */
    private static void initializeBridges() {
        switch (currentMode) {
            case ISS_PRIMARY:
                if (isIronsLoaded) {
                    activeBridge = new IronsBridge();
                    secondaryBridge = new ArsNativeBridge();
                } else {
                    LOGGER.warn("ISS_PRIMARY mode selected but Iron's Spellbooks not found. Falling back to ARS_PRIMARY.");
                    activeBridge = new ArsNativeBridge();
                    currentMode = ManaUnificationMode.ARS_PRIMARY;
                }
                break;
                
            case ARS_PRIMARY:
                activeBridge = new ArsNativeBridge();
                if (isIronsLoaded) {
                    secondaryBridge = new IronsBridge();
                }
                break;
                
            case HYBRID:
                if (isIronsLoaded) {
                    activeBridge = new IronsBridge(); // Primary for reads
                    secondaryBridge = new ArsNativeBridge(); // Secondary for sync
                } else {
                    LOGGER.warn("HYBRID mode selected but Iron's Spellbooks not found. Falling back to ARS_PRIMARY.");
                    activeBridge = new ArsNativeBridge();
                    currentMode = ManaUnificationMode.ARS_PRIMARY;
                }
                break;
                
            case SEPARATE:
                activeBridge = new ArsNativeBridge();
                if (isIronsLoaded) {
                    secondaryBridge = new IronsBridge();
                } else {
                    LOGGER.warn("SEPARATE mode selected but Iron's Spellbooks not found. Falling back to ARS_PRIMARY.");
                    currentMode = ManaUnificationMode.ARS_PRIMARY;
                }
                break;
                
            case DISABLED:
                activeBridge = new ArsNativeBridge(); // Fallback to native
                secondaryBridge = null;
                break;
                
            default:
                LOGGER.error("Unknown mana mode: {}. Defaulting to ISS_PRIMARY.", currentMode);
                activeBridge = isIronsLoaded ? new IronsBridge() : new ArsNativeBridge();
                break;
        }
    }

    /**
     * Log initialization details
     */
    private static void logInitialization() {
        LOGGER.info("========================================");
        LOGGER.info("Ars 'n' Spells - Mana Bridge Initialization");
        LOGGER.info("========================================");
        LOGGER.info("Iron's Spellbooks Detected: {}", isIronsLoaded);
        LOGGER.info("Mana Unification Mode: {}", currentMode.getConfigName());
        LOGGER.info("Mode Description: {}", currentMode.getDescription());
        LOGGER.info("Primary Bridge: {}", activeBridge.getBridgeType());
        if (secondaryBridge != null) {
            LOGGER.info("Secondary Bridge: {}", secondaryBridge.getBridgeType());
        }
        LOGGER.info("Mana Unification Enabled: {}", AnsConfig.ENABLE_MANA_UNIFICATION.get());
        LOGGER.info("========================================");
    }

    /**
     * Get the active mana bridge
     */
    public static IManaBridge getBridge() {
        return activeBridge != null ? activeBridge : new ArsNativeBridge();
    }

    /**
     * Get the secondary mana bridge (for hybrid/separate modes)
     */
    public static IManaBridge getSecondaryBridge() {
        return secondaryBridge;
    }

    /**
     * Get the current mana unification mode
     */
    public static ManaUnificationMode getCurrentMode() {
        return currentMode != null ? currentMode : AnsConfig.getManaMode();
    }

    /**
     * Check if Iron's Spellbooks is loaded
     */
    public static boolean isIronsSpellbooksLoaded() {
        return isIronsLoaded;
    }

    /**
     * Check if mana unification is enabled
     */
    public static boolean isUnificationEnabled() {
        if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
            return false;
        }
        ManaUnificationMode mode = getCurrentMode();
        return mode != null && mode.isUnificationEnabled();
    }

    /**
     * Check if we're using a shared mana pool
     */
    public static boolean usesSharedPool() {
        ManaUnificationMode mode = getCurrentMode();
        return isUnificationEnabled() && mode != null && mode.usesSharedPool();
    }

    /**
     * Check if we're using dual-cost mechanics
     */
    public static boolean usesDualCost() {
        ManaUnificationMode mode = getCurrentMode();
        return isUnificationEnabled() && mode != null && mode.requiresDualCost();
    }

    /**
     * Get mana from the appropriate bridge based on mode
     */
    public static float getManaForMode(net.minecraft.world.entity.player.Player player, boolean fromArs) {
        if (!isUnificationEnabled()) {
            return fromArs ? new ArsNativeBridge().getMana(player) : 
                   (isIronsLoaded ? new IronsBridge().getMana(player) : 0);
        }

        ManaUnificationMode mode = getCurrentMode();
        switch (mode) {
            case ISS_PRIMARY:
            case ARS_PRIMARY:
            case HYBRID:
                return activeBridge.getMana(player);
                
            case SEPARATE:
                return fromArs ?
                    activeBridge.getMana(player) :
                    (secondaryBridge != null ? secondaryBridge.getMana(player) : activeBridge.getMana(player));
                
            default:
                return activeBridge.getMana(player);
        }
    }

    /**
     * Consume mana based on current mode
     */
    public static boolean consumeManaForMode(net.minecraft.world.entity.player.Player player, float amount, boolean fromArs) {
        if (!isUnificationEnabled()) {
            if (fromArs) {
                return new ArsNativeBridge().consumeMana(player, amount);
            } else if (isIronsLoaded) {
                return new IronsBridge().consumeMana(player, amount);
            }
            return false;
        }

        ManaUnificationMode mode = getCurrentMode();
        switch (mode) {
            case ISS_PRIMARY:
            case ARS_PRIMARY:
            case HYBRID:
                // Shared pool - consume from primary bridge
                return activeBridge.consumeMana(player, amount);
                
            case SEPARATE:
                // Dual-cost mode - consume from both pools
                float arsCost = amount * AnsConfig.DUAL_COST_ARS_PERCENTAGE.get().floatValue();
                float issCost = amount * AnsConfig.DUAL_COST_ISS_PERCENTAGE.get().floatValue();
                IManaBridge arsBridge = activeBridge;
                IManaBridge issBridge = secondaryBridge;

                if (issBridge == null) {
                    return arsBridge.consumeMana(player, arsCost);
                }

                boolean arsHas = arsBridge.getMana(player) >= arsCost;
                boolean issHas = issBridge.getMana(player) >= issCost;
                if (!arsHas || !issHas) {
                    return false;
                }

                boolean arsSuccess = arsBridge.consumeMana(player, arsCost);
                boolean issSuccess = issBridge.consumeMana(player, issCost);

                return arsSuccess && issSuccess;
                
            default:
                return activeBridge.consumeMana(player, amount);
        }
    }
}
