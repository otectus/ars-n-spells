package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BridgeManager - central hub for mana system integration. Resolves the
 * active bridge based on configuration and the presence of Iron's
 * Spellbooks at common-setup time.
 */
public class BridgeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeManager.class);
    private static IManaBridge activeBridge;
    private static IManaBridge secondaryBridge;
    private static ManaUnificationMode currentMode;
    private static boolean isIronsLoaded = false;

    public static void init(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            isIronsLoaded = ModList.get().isLoaded("irons_spellbooks");
            currentMode = AnsConfig.getManaMode();
            initializeBridges();
            logInitialization();
        });
    }

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
                    activeBridge = new IronsBridge();
                    secondaryBridge = new ArsNativeBridge();
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
                activeBridge = new ArsNativeBridge();
                secondaryBridge = null;
                break;

            default:
                LOGGER.error("Unknown mana mode: {}. Defaulting to ISS_PRIMARY.", currentMode);
                activeBridge = isIronsLoaded ? new IronsBridge() : new ArsNativeBridge();
                break;
        }
    }

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

    public static IManaBridge getBridge() {
        return activeBridge != null ? activeBridge : new ArsNativeBridge();
    }

    public static IManaBridge getSecondaryBridge() {
        return secondaryBridge;
    }

    public static ManaUnificationMode getCurrentMode() {
        return currentMode;
    }

    public static boolean isIronsSpellbooksLoaded() {
        return isIronsLoaded;
    }

    public static boolean isUnificationEnabled() {
        if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
            return false;
        }
        ManaUnificationMode mode = getCurrentMode();
        return mode != null && mode.isUnificationEnabled();
    }

    public static boolean usesSharedPool() {
        ManaUnificationMode mode = getCurrentMode();
        return isUnificationEnabled() && mode != null && mode.usesSharedPool();
    }

    public static boolean usesDualCost() {
        ManaUnificationMode mode = getCurrentMode();
        return isUnificationEnabled() && mode != null && mode.requiresDualCost();
    }

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
                return activeBridge.consumeMana(player, amount);

            case SEPARATE:
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

                float arsManaBefore = arsBridge.getMana(player);
                boolean arsSuccess = arsBridge.consumeMana(player, arsCost);
                if (!arsSuccess) {
                    return false;
                }

                boolean issSuccess = issBridge.consumeMana(player, issCost);
                if (!issSuccess) {
                    arsBridge.setMana(player, arsManaBefore);
                    return false;
                }

                return true;

            default:
                return activeBridge.consumeMana(player, amount);
        }
    }
}
