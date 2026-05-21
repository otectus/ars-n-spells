package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BridgeManager - Central hub for mana system integration
 * 
 * Manages the active mana bridge based on configuration and available mods.
 * Supports 5 different mana unification modes.
 */
public class BridgeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeManager.class);
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
        LOGGER.info("NOTE: Changing mana_unification_mode requires a game restart.");
        if (currentMode == ManaUnificationMode.SEPARATE) {
            double arsPercent = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get();
            double issPercent = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get();
            double total = arsPercent + issPercent;
            if (Math.abs(total - 1.0) > 0.01) {
                LOGGER.warn("Dual-cost percentages sum to {} (Ars: {}, ISS: {}) - expected 1.0", total, arsPercent, issPercent);
            }
        }
        LOGGER.info("========================================");
    }

    /** ANS-OPT-016: cached fallback so the null path does not allocate per call. */
    private static final IManaBridge FALLBACK_BRIDGE = new ArsNativeBridge();

    /**
     * Get the active mana bridge.
     */
    public static IManaBridge getBridge() {
        return activeBridge != null ? activeBridge : FALLBACK_BRIDGE;
    }

    /**
     * Get the secondary mana bridge (for hybrid/separate modes)
     */
    public static IManaBridge getSecondaryBridge() {
        return secondaryBridge;
    }

    /**
     * Get the current mana unification mode.
     * Cached at init time — changing mana_unification_mode requires a restart.
     *
     * <p>ANS-MED-018: if called before {@link #init} runs (e.g. early client render
     * frames during world load), fall back to reading the config directly so callers
     * never see a {@code null} mode and stale-state branches don't fire.
     */
    public static ManaUnificationMode getCurrentMode() {
        ManaUnificationMode cached = currentMode;
        if (cached != null) return cached;
        try {
            return AnsConfig.getManaMode();
        } catch (Throwable t) {
            return ManaUnificationMode.DISABLED;
        }
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
            if (fromArs) {
                return activeBridge != null ? activeBridge.getMana(player) : 0;
            }
            return secondaryBridge != null ? secondaryBridge.getMana(player) :
                   (activeBridge != null ? activeBridge.getMana(player) : 0);
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
                return activeBridge != null && activeBridge.consumeMana(player, amount);
            }
            return secondaryBridge != null ? secondaryBridge.consumeMana(player, amount) :
                   (activeBridge != null && activeBridge.consumeMana(player, amount));
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

                // ANS-CRIT-003: compensating refund (addMana) instead of snapshot+restore.
                // The earlier setMana(arsManaBefore) pattern would clobber any concurrent
                // regen/buff/ritual mana mutation landing between the snapshot and the
                // rollback. addMana delegates to the backing API's atomic add, preserving
                // concurrent deltas.
                boolean arsSuccess = arsBridge.consumeMana(player, arsCost);
                if (!arsSuccess) {
                    return false;
                }

                boolean issSuccess = issBridge.consumeMana(player, issCost);
                if (!issSuccess) {
                    arsBridge.addMana(player, arsCost);
                    return false;
                }

                return true;
                
            default:
                return activeBridge.consumeMana(player, amount);
        }
    }
}
