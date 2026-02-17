package com.otectus.arsnspells.compat;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects and tracks Iron's Spellbooks addon mods at runtime.
 * Provides version checking and compatibility validation.
 */
public class AddonModDetector {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AddonModDetector.class);
    
    private static final Map<AddonModInfo, AddonStatus> detectedAddons = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;
    
    /**
     * Initialize addon detection.
     * Should be called during mod initialization.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        
        synchronized (AddonModDetector.class) {
            if (initialized) {
                return;
            }
            
            LOGGER.info("[Ars 'n' Spells] ========================================");
            LOGGER.info("[Ars 'n' Spells] Scanning for Iron's Spellbooks addons...");
            LOGGER.info("[Ars 'n' Spells] ========================================");
            
            ModList modList = ModList.get();
            int foundCount = 0;
            
            for (AddonModInfo addonInfo : AddonModInfo.values()) {
                AddonStatus status = detectAddon(modList, addonInfo);
                detectedAddons.put(addonInfo, status);
                
                if (status.isPresent()) {
                    foundCount++;
                    logAddonFound(addonInfo, status);
                }
            }
            
            if (foundCount == 0) {
                LOGGER.info("[Ars 'n' Spells] No addon mods detected");
                LOGGER.info("[Ars 'n' Spells] Ars 'n' Spells will work with base Iron's Spellbooks");
            } else {
                LOGGER.info("[Ars 'n' Spells] ========================================");
                LOGGER.info("[Ars 'n' Spells] Found {} addon mod(s)", foundCount);
                LOGGER.info("[Ars 'n' Spells] All detected addons will be integrated");
                LOGGER.info("[Ars 'n' Spells] ========================================");
            }
            
            initialized = true;
        }
    }
    
    /**
     * Detect a specific addon mod.
     */
    private static AddonStatus detectAddon(ModList modList, AddonModInfo addonInfo) {
        String modId = addonInfo.getModId();
        
        if (!modList.isLoaded(modId)) {
            return new AddonStatus(false, null, null, "Mod not loaded");
        }
        
        Optional<? extends IModInfo> modInfoOpt = modList.getModContainerById(modId)
            .map(container -> container.getModInfo());
        
        if (!modInfoOpt.isPresent()) {
            return new AddonStatus(false, null, null, "Mod info not available");
        }
        
        IModInfo modInfo = modInfoOpt.get();
        String detectedVersion = modInfo.getVersion().toString();
        
        // Check version compatibility
        boolean versionMatch = isVersionCompatible(detectedVersion, addonInfo.getExpectedVersion());
        String warning = null;
        
        if (!versionMatch) {
            warning = "Version mismatch: detected " + detectedVersion + 
                     ", expected " + addonInfo.getExpectedVersion() + 
                     " (may cause compatibility issues)";
        }
        
        return new AddonStatus(true, detectedVersion, warning, null);
    }
    
    /**
     * Check if a detected version is compatible with the expected version.
     * Currently does simple prefix matching, can be enhanced for version ranges.
     */
    private static boolean isVersionCompatible(String detected, String expected) {
        if (detected.equals(expected)) {
            return true;
        }
        
        // Allow minor version differences (e.g., 1.2.8 matches 1.2.x)
        String[] detectedParts = detected.split("\\.");
        String[] expectedParts = expected.split("\\.");
        
        if (detectedParts.length >= 2 && expectedParts.length >= 2) {
            return detectedParts[0].equals(expectedParts[0]) && 
                   detectedParts[1].equals(expectedParts[1]);
        }
        
        return false;
    }
    
    /**
     * Log when an addon is found.
     */
    private static void logAddonFound(AddonModInfo addonInfo, AddonStatus status) {
        LOGGER.info("[Ars 'n' Spells] OK Found: {} v{}", 
            addonInfo.getDisplayName(), 
            status.getDetectedVersion());
        
        if (status.hasWarning()) {
            LOGGER.warn("[Ars 'n' Spells]   WARN {}", status.getWarning());
        }
    }
    
    /**
     * Check if a specific addon is present.
     */
    public static boolean isAddonPresent(AddonModInfo addon) {
        initialize();
        AddonStatus status = detectedAddons.get(addon);
        return status != null && status.isPresent();
    }
    
    /**
     * Get the status of a specific addon.
     */
    public static AddonStatus getAddonStatus(AddonModInfo addon) {
        initialize();
        return detectedAddons.getOrDefault(addon, 
            new AddonStatus(false, null, null, "Not checked"));
    }
    
    /**
     * Get all detected addons.
     */
    public static Map<AddonModInfo, AddonStatus> getAllDetectedAddons() {
        initialize();
        return new HashMap<>(detectedAddons);
    }
    
    /**
     * Get list of present addons.
     */
    public static List<AddonModInfo> getPresentAddons() {
        initialize();
        List<AddonModInfo> present = new ArrayList<>();
        for (Map.Entry<AddonModInfo, AddonStatus> entry : detectedAddons.entrySet()) {
            if (entry.getValue().isPresent()) {
                present.add(entry.getKey());
            }
        }
        return present;
    }
    
    /**
     * Check if any addons are present.
     */
    public static boolean hasAnyAddons() {
        initialize();
        return !getPresentAddons().isEmpty();
    }
    
    /**
     * Status information for a detected addon.
     */
    public static class AddonStatus {
        private final boolean present;
        private final String detectedVersion;
        private final String warning;
        private final String failureReason;
        
        public AddonStatus(boolean present, String detectedVersion, String warning, String failureReason) {
            this.present = present;
            this.detectedVersion = detectedVersion;
            this.warning = warning;
            this.failureReason = failureReason;
        }
        
        public boolean isPresent() {
            return present;
        }
        
        public String getDetectedVersion() {
            return detectedVersion;
        }
        
        public boolean hasWarning() {
            return warning != null;
        }
        
        public String getWarning() {
            return warning;
        }
        
        public String getFailureReason() {
            return failureReason;
        }
    }
}
