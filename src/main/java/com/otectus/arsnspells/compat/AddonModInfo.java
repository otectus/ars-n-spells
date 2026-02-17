package com.otectus.arsnspells.compat;

/**
 * Metadata for supported addon mods.
 * Includes both Iron's Spellbooks addons and other compatible mods.
 * Each addon is detected at runtime and integrated if available.
 */
public enum AddonModInfo {
    
    // Iron's Spellbooks Addons
    CATACLYSM_SPELLBOOKS(
        "cataclysm_spellbooks",
        "Cataclysm Spellbooks",
        "1.2.8",
        "Adds spells based on Cataclysm mod creatures and mechanics",
        AddonType.IRONS_ADDON
    ),
    
    DARKER_MAGIC(
        "darkermagic",
        "Darker Magic",
        "1.1.0",
        "Adds darker, forbidden magic spells and mechanics",
        AddonType.IRONS_ADDON
    ),
    
    GALOSPHERE_SPELLBOOKS(
        "galosphere_spellbooks",
        "Galosphere Spellbooks",
        "1.1.1",
        "Adds spells themed around the Galosphere mod",
        AddonType.IRONS_ADDON
    ),
    
    ICE_AND_FIRE_SPELLBOOKS(
        "ice_and_fire_spellbooks",
        "Ice and Fire Spellbooks",
        "2.3.2",
        "Adds spells based on Ice and Fire mod creatures",
        AddonType.IRONS_ADDON
    ),
    
    // Ars Nouveau Addons
    SANCTIFIED_LEGACY(
        "sanctified_legacy",
        "Sanctified Legacy",
        "2.2.5",
        "Adds holy/divine magic themed around sanctification and legacy powers",
        AddonType.ARS_ADDON
    ),
    
    // Other Compatible Mods
    WIZARDS_REBORN_EXTENDED(
        "wre",
        "Wizards Reborn Extended",
        "1.0.0",
        "Extended content for Wizards Reborn mod with additional spells and mechanics",
        AddonType.OTHER
    );
    
    private final String modId;
    private final String displayName;
    private final String expectedVersion;
    private final String description;
    private final AddonType type;
    
    AddonModInfo(String modId, String displayName, String expectedVersion, String description, AddonType type) {
        this.modId = modId;
        this.displayName = displayName;
        this.expectedVersion = expectedVersion;
        this.description = description;
        this.type = type;
    }
    
    public String getModId() {
        return modId;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getExpectedVersion() {
        return expectedVersion;
    }
    
    public String getDescription() {
        return description;
    }
    
    public AddonType getType() {
        return type;
    }
    
    /**
     * Get addon info by mod ID.
     */
    public static AddonModInfo fromModId(String modId) {
        for (AddonModInfo info : values()) {
            if (info.modId.equals(modId)) {
                return info;
            }
        }
        return null;
    }
    
    /**
     * Type of addon mod for categorization.
     */
    public enum AddonType {
        IRONS_ADDON("Iron's Spellbooks Addon"),
        ARS_ADDON("Ars Nouveau Addon"),
        OTHER("Other Compatible Mod");
        
        private final String displayName;
        
        AddonType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}
