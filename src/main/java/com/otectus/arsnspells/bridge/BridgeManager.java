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
    // ANS 2.0.1: volatile — refreshMode() can re-assign these at runtime (server
    // thread) while the client render thread reads them for HUD mode checks.
    private static volatile IManaBridge activeBridge;
    private static volatile IManaBridge secondaryBridge; // For hybrid/separate modes
    private static volatile ManaUnificationMode currentMode;
    private static boolean isIronsLoaded = false;

    public static void init(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Check if Iron's Spellbooks is loaded
            isIronsLoaded = ModList.get().isLoaded("irons_spellbooks");

            // ANS-HIGH-029: the config is SERVER-type and is NOT loaded yet at
            // common setup — in production a read returns defaults; in dev Forge
            // throws IllegalStateException. Treat init as provisional: fall back
            // to DISABLED (fallback bridges, never null) and rely on the
            // ModConfigEvent Loading/Reloading listeners in ArsNSpells to call
            // refreshMode() once real values exist. logInitialization() also
            // reads config values, so it is skipped until then.
            boolean configReadable = true;
            try {
                currentMode = AnsConfig.getManaMode();
            } catch (Exception e) {
                configReadable = false;
                LOGGER.info("Config not loaded at common setup (expected for SERVER configs); "
                    + "starting in DISABLED mode until config load refreshes it");
                currentMode = ManaUnificationMode.DISABLED;
            }

            // Initialize bridges based on mode
            initializeBridges();

            // Log initialization (only when real config values were available;
            // refreshMode() logs the definitive state at config load).
            if (configReadable) {
                logInitialization();
            }
        });
    }

    /**
     * Re-read the configured mana mode and re-select bridges at runtime.
     *
     * <p>ANS 2.0.1: lets {@code /ans mode set} and the in-game config screen apply a
     * mode change live instead of requiring a restart. Bridges are stateless (see the
     * singleton {@link #FALLBACK_BRIDGE}), so re-running {@link #initializeBridges()}
     * is safe. {@code synchronized} serialises concurrent refreshes; the volatile
     * fields give readers a consistent latest reference. Callers must persist the
     * config value ({@code AnsConfig.MANA_UNIFICATION_MODE.set} + {@code safeSave})
     * first, and invoke this on the server thread (command handlers already are; the
     * config screen marshals via the integrated server executor).
     */
    public static synchronized void refreshMode() {
        currentMode = AnsConfig.getManaMode();
        initializeBridges();
        logInitialization();
    }

    /**
     * Test-only seam: set the cached mode without constructing bridges.
     *
     * <p>Bridge construction ({@link IronsBridge}/{@link ArsNativeBridge}) touches mod
     * APIs that are absent from the unit-test classpath, so tests cannot call
     * {@link #refreshMode()}. This sets only the cached enum, which is enough to drive
     * the mode-read branches ({@link #getCurrentMode}, {@link #usesSharedPool},
     * {@link #usesDualCost}, {@link #getManaForMode}). Also the seam the 2.0.1
     * CrossCastCostResolverTest roadmap item depends on.
     */
    static void testSetMode(ManaUnificationMode mode) {
        currentMode = mode;
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
        LOGGER.info("NOTE: mana_unification_mode can be changed live via '/ans mode set' or the in-game config screen (applied by refreshMode()).");
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
     * Cached at init time and refreshed live by {@link #refreshMode()} when the mode is
     * changed via {@code /ans mode set} or the in-game config screen.
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
     * Check if mana unification is enabled.
     *
     * <p><b>Precedence source of truth</b> (audit F5): the master toggle
     * {@code enable_mana_unification} wins over {@code mana_unification_mode} —
     * toggle off forces DISABLED regardless of mode (mirrored in
     * {@link AnsConfig#getManaMode()}). Mode-dependent call sites must use this
     * helper instead of reading {@code ENABLE_MANA_UNIFICATION} directly so the
     * rule lives in exactly one place.
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
                // Dual-cost mode - consume from both pools.
                // ANS: normalize the configured split so the two halves always sum to the
                // base cost. The init-time sum check (logInitialization) only WARNs; without
                // normalizing here a split summing to e.g. 1.2 would silently overcharge the
                // player by 20% on every cast (and an under-1.0 split would undercharge).
                double arsPct = AnsConfig.DUAL_COST_ARS_PERCENTAGE.get();
                double issPct = AnsConfig.DUAL_COST_ISS_PERCENTAGE.get();
                double pctTotal = arsPct + issPct;
                float arsCost;
                float issCost;
                if (pctTotal <= 0.0) {
                    // Degenerate config — treat the whole cost as Ars-side.
                    arsCost = amount;
                    issCost = 0.0f;
                } else {
                    arsCost = (float) (amount * (arsPct / pctTotal));
                    issCost = (float) (amount * (issPct / pctTotal));
                }
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
