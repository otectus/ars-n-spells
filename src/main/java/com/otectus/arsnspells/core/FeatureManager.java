package com.otectus.arsnspells.core;

import com.otectus.arsnspells.config.AnsConfig;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.Map;

/**
 * Centralized feature management system.
 * Controls which features are enabled and provides runtime checks.
 */
public class FeatureManager {
    private static final Logger LOGGER = LogManager.getLogger();
    
    private static final Map<Feature, Boolean> featureStates = new EnumMap<>(Feature.class);
    private static boolean initialized = false;

    public enum Feature {
        UNIFIED_COOLDOWNS("Unified Cooldown System"),
        RESONANCE_SYSTEM("Resonance System"),
        AFFINITY_TRACKING("Affinity Tracking"),
        PROGRESSION_SYNC("Progression Synchronization"),
        MANA_REGEN_SYNERGY("Mana Regeneration Synergy"),
        RITUAL_INTEGRATION("Ritual Integration"),
        ADDON_DETECTION("Addon Detection"),
        BRIDGE_SYSTEM("Mana Bridge System");

        private final String displayName;

        Feature(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Initialize the feature manager.
     * Should be called during mod initialization.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        synchronized (FeatureManager.class) {
            if (initialized) {
                return;
            }

            LOGGER.info("[Ars 'n' Spells] Initializing Feature Manager...");

            // Initialize feature states from config
            updateFeatureStates();

            // Log enabled features
            logFeatureStatus();

            initialized = true;
            LOGGER.info("[Ars 'n' Spells] Feature Manager initialized");
        }
    }

    /**
     * Update feature states from configuration.
     */
    private static void updateFeatureStates() {
        featureStates.put(Feature.UNIFIED_COOLDOWNS, 
            AnsConfig.ENABLE_COOLDOWN_SYSTEM.get() && AnsConfig.ENABLE_UNIFIED_COOLDOWNS.get());
        featureStates.put(Feature.RESONANCE_SYSTEM, 
            AnsConfig.ENABLE_RESONANCE_SYSTEM.get() && isIronsSpellbooksLoaded());
        featureStates.put(Feature.AFFINITY_TRACKING, 
            AnsConfig.ENABLE_AFFINITY_SYSTEM.get());
        featureStates.put(Feature.PROGRESSION_SYNC, 
            AnsConfig.ENABLE_PROGRESSION_SYSTEM.get() && isIronsSpellbooksLoaded());
        featureStates.put(Feature.MANA_REGEN_SYNERGY, 
            AnsConfig.ENABLE_MANA_UNIFICATION.get() && isIronsSpellbooksLoaded());
        featureStates.put(Feature.RITUAL_INTEGRATION, 
            isArsNouveauLoaded());
        featureStates.put(Feature.ADDON_DETECTION, 
            true);
        featureStates.put(Feature.BRIDGE_SYSTEM, 
            AnsConfig.ENABLE_MANA_UNIFICATION.get() && (isArsNouveauLoaded() || isIronsSpellbooksLoaded()));
    }

    /**
     * Disable all features.
     */
    private static void disableAllFeatures() {
        for (Feature feature : Feature.values()) {
            featureStates.put(feature, false);
        }
    }

    /**
     * Check if a feature is enabled.
     */
    public static boolean isEnabled(Feature feature) {
        if (!initialized) {
            initialize();
        }
        return featureStates.getOrDefault(feature, false);
    }

    /**
     * Enable or disable a feature at runtime.
     * Note: Some features may require restart to take effect.
     */
    public static void setEnabled(Feature feature, boolean enabled) {
        if (!initialized) {
            initialize();
        }

        boolean wasEnabled = featureStates.getOrDefault(feature, false);
        featureStates.put(feature, enabled);

        if (wasEnabled != enabled) {
            LOGGER.info("[Ars 'n' Spells] Feature '{}' {} at runtime", 
                feature.getDisplayName(), enabled ? "enabled" : "disabled");
        }
    }

    /**
     * Check if Ars Nouveau is loaded.
     */
    public static boolean isArsNouveauLoaded() {
        return ModList.get().isLoaded("ars_nouveau");
    }

    /**
     * Check if Iron's Spellbooks is loaded.
     */
    public static boolean isIronsSpellbooksLoaded() {
        return ModList.get().isLoaded("irons_spellbooks");
    }

    /**
     * Get the number of enabled features.
     */
    public static int getEnabledFeatureCount() {
        if (!initialized) {
            initialize();
        }
        return (int) featureStates.values().stream().filter(enabled -> enabled).count();
    }

    /**
     * Get the total number of features.
     */
    public static int getTotalFeatureCount() {
        return Feature.values().length;
    }

    /**
     * Log feature status.
     */
    private static void logFeatureStatus() {
        LOGGER.info("[Ars 'n' Spells] ========================================");
        LOGGER.info("[Ars 'n' Spells] Feature Status:");
        LOGGER.info("[Ars 'n' Spells] ========================================");

        for (Feature feature : Feature.values()) {
            boolean enabled = featureStates.getOrDefault(feature, false);
            String status = enabled ? "OK ENABLED" : "FAILED DISABLED";
            LOGGER.info("[Ars 'n' Spells] {} - {}", status, feature.getDisplayName());
        }

        LOGGER.info("[Ars 'n' Spells] ========================================");
        LOGGER.info("[Ars 'n' Spells] {}/{} features enabled", 
            getEnabledFeatureCount(), getTotalFeatureCount());
        LOGGER.info("[Ars 'n' Spells] ========================================");
    }

    /**
     * Reload feature states from configuration.
     * Useful for runtime config changes.
     */
    public static void reload() {
        LOGGER.info("[Ars 'n' Spells] Reloading feature states from configuration...");
        updateFeatureStates();
        logFeatureStatus();
    }

    /**
     * Get a summary of feature states.
     */
    public static String getSummary() {
        if (!initialized) {
            initialize();
        }
        return String.format("Features: %d/%d enabled", 
            getEnabledFeatureCount(), getTotalFeatureCount());
    }
}
