package com.otectus.arsnspells.config;

/**
 * Defines how mana systems from Ars Nouveau and Iron's Spellbooks interact.
 * 
 * This enum controls the fundamental behavior of mana unification in Ars 'n' Spells.
 */
public enum ManaUnificationMode {
    /**
     * Mode 1: Iron's Spellbooks Primary (Default)
     * - ISS mana is the single source of truth
     * - Ars Nouveau spells consume ISS mana
     * - Ars mana UI shows ISS values
     * - All mana regeneration goes to ISS pool
     * 
     * Use Case: Players who want ISS as their main magic system
     */
    ISS_PRIMARY("iss_primary", "Iron's Spellbooks is the primary mana source"),
    
    /**
     * Mode 2: Ars Nouveau Primary
     * - Ars mana is the single source of truth
     * - ISS spells consume Ars mana
     * - ISS mana UI shows Ars values
     * - All mana regeneration goes to Ars pool
     * 
     * Use Case: Players who want Ars Nouveau as their main magic system
     */
    ARS_PRIMARY("ars_primary", "Ars Nouveau is the primary mana source"),
    
    /**
     * Mode 3: Hybrid Pool
     * - Both systems share a unified mana pool
     * - Mana is synchronized bidirectionally
     * - Either system can consume from the shared pool
     * - Regeneration from both systems adds to shared pool
     * - Conversion rate applies for balancing
     * 
     * Use Case: Players who want seamless integration between both systems
     */
    HYBRID("hybrid", "Both systems share a unified mana pool"),
    
    /**
     * Mode 4: Separate Pools (Dual-Cost)
     * - Each system maintains its own mana pool
     * - No conversion between pools
     * - Spells that cross systems require mana from BOTH pools
     * - Each system regenerates independently
     * 
     * Use Case: Players who want resource management challenge
     */
    SEPARATE("separate", "Separate mana pools with dual-cost mechanics"),
    
    /**
     * Mode 5: Disabled (No Integration)
     * - No mana unification at all
     * - Each system works completely independently
     * - No cross-mod mana consumption
     * - No shared mechanics
     * 
     * Use Case: Players who want zero interaction between magic systems
     */
    DISABLED("disabled", "No mana integration between systems");
    
    private final String configName;
    private final String description;
    
    ManaUnificationMode(String configName, String description) {
        this.configName = configName;
        this.description = description;
    }
    
    public String getConfigName() {
        return configName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Get mode from config string value
     */
    public static ManaUnificationMode fromString(String value) {
        for (ManaUnificationMode mode : values()) {
            if (mode.configName.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return ISS_PRIMARY; // Default fallback
    }
    
    /**
     * Check if mana unification is active
     */
    public boolean isUnificationEnabled() {
        return this != DISABLED;
    }
    
    /**
     * Check if this mode uses a shared mana pool
     */
    public boolean usesSharedPool() {
        return this == ISS_PRIMARY || this == ARS_PRIMARY || this == HYBRID;
    }
    
    /**
     * Check if this mode requires dual-cost mechanics
     */
    public boolean requiresDualCost() {
        return this == SEPARATE;
    }
    
    /**
     * Check if ISS is the primary mana source
     */
    public boolean isIssPrimary() {
        return this == ISS_PRIMARY;
    }
    
    /**
     * Check if Ars is the primary mana source
     */
    public boolean isArsPrimary() {
        return this == ARS_PRIMARY;
    }
    
    /**
     * Check if hybrid mode is active
     */
    public boolean isHybrid() {
        return this == HYBRID;
    }
}
