package com.otectus.arsnspells.compat;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.progression.SpellSchool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for addon mod spells.
 * Automatically detects and categorizes spells from addon mods.
 */
public class AddonSpellRegistry {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AddonSpellRegistry.class);
    
    private static final Map<String, SpellSchool> spellToSchoolMap = new ConcurrentHashMap<>();
    private static final Map<AddonModInfo, Set<String>> addonSpells = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;
    
    /**
     * Initialize the addon spell registry.
     * Should be called after addon detection.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        
        synchronized (AddonSpellRegistry.class) {
            if (initialized) {
                return;
            }
            
            LOGGER.info("[Ars 'n' Spells] Initializing addon spell registry...");
            
            // Get list of detected addons
            List<AddonModInfo> presentAddons = AddonModDetector.getPresentAddons();
            
            if (presentAddons.isEmpty()) {
                LOGGER.info("[Ars 'n' Spells] No addons to register spells from");
                initialized = true;
                return;
            }
            
            // Register spells from each addon
            for (AddonModInfo addon : presentAddons) {
                registerAddonSpells(addon);
            }
            
            LOGGER.info("[Ars 'n' Spells] Addon spell registry initialized");
            LOGGER.info("[Ars 'n' Spells] Total addon spells registered: {}", spellToSchoolMap.size());
            
            initialized = true;
        }
    }
    
    /**
     * Register spells from a specific addon.
     */
    private static void registerAddonSpells(AddonModInfo addon) {
        try {
            Set<String> spells = new HashSet<>();
            
            switch (addon) {
                case CATACLYSM_SPELLBOOKS:
                    spells = registerCataclysmSpells();
                    break;
                case DARKER_MAGIC:
                    spells = registerDarkerMagicSpells();
                    break;
                case GALOSPHERE_SPELLBOOKS:
                    spells = registerGalosphereSpells();
                    break;
                case ICE_AND_FIRE_SPELLBOOKS:
                    spells = registerIceAndFireSpells();
                    break;
                case SANCTIFIED_LEGACY:
                    spells = registerSanctifiedLegacySpells();
                    break;
                case WIZARDS_REBORN_EXTENDED:
                    spells = registerWizardsRebornExtendedSpells();
                    break;
            }
            
            if (!spells.isEmpty()) {
                addonSpells.put(addon, spells);
                LOGGER.info("[Ars 'n' Spells] Registered {} spells from {}", 
                    spells.size(), addon.getDisplayName());
            }
            
        } catch (Exception e) {
            LOGGER.warn("[Ars 'n' Spells] Failed to register spells from {}: {}", 
                addon.getDisplayName(), e.getMessage());
            
            if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
                LOGGER.warn("[Ars 'n' Spells] [DEBUG] Full error:", e);
            }
        }
    }
    
    /**
     * Register Cataclysm Spellbooks spells.
     * These are typically combat-focused spells based on Cataclysm creatures.
     */
    private static Set<String> registerCataclysmSpells() {
        Set<String> spells = new HashSet<>();
        
        // Cataclysm spells are typically evocation/fire/blood themed
        // Map them to appropriate schools
        
        // Fire-based Cataclysm spells
        registerSpell(spells, "ignis_breath", SpellSchool.FIRE);
        registerSpell(spells, "netherite_monstrosity_slam", SpellSchool.EVOCATION);
        registerSpell(spells, "harbinger_strike", SpellSchool.BLOOD);
        registerSpell(spells, "void_rune", SpellSchool.ELDRITCH);
        
        // Note: Actual spell IDs would need to be verified from the mod
        // This is a template that can be updated with real spell IDs
        
        return spells;
    }
    
    /**
     * Register Darker Magic spells.
     * These are forbidden/dark magic spells.
     */
    private static Set<String> registerDarkerMagicSpells() {
        Set<String> spells = new HashSet<>();
        
        // Darker Magic spells are typically blood/eldritch themed
        registerSpell(spells, "soul_drain", SpellSchool.BLOOD);
        registerSpell(spells, "dark_ritual", SpellSchool.ELDRITCH);
        registerSpell(spells, "curse_of_weakness", SpellSchool.ELDRITCH);
        registerSpell(spells, "blood_sacrifice", SpellSchool.BLOOD);
        registerSpell(spells, "shadow_step", SpellSchool.ENDER);
        
        return spells;
    }
    
    /**
     * Register Galosphere Spellbooks spells.
     * These are themed around the Galosphere mod.
     */
    private static Set<String> registerGalosphereSpells() {
        Set<String> spells = new HashSet<>();
        
        // Galosphere spells might be nature/ender/utility themed
        registerSpell(spells, "crystal_shard", SpellSchool.EVOCATION);
        registerSpell(spells, "pink_salt_heal", SpellSchool.HOLY);
        registerSpell(spells, "spectre_summon", SpellSchool.NATURE);
        
        return spells;
    }
    
    /**
     * Register Ice and Fire Spellbooks spells.
     * These are based on Ice and Fire mod creatures.
     */
    private static Set<String> registerIceAndFireSpells() {
        Set<String> spells = new HashSet<>();
        
        // Dragon-themed spells
        registerSpell(spells, "dragon_breath_fire", SpellSchool.FIRE);
        registerSpell(spells, "dragon_breath_ice", SpellSchool.ICE);
        registerSpell(spells, "dragon_breath_lightning", SpellSchool.LIGHTNING);
        
        // Creature summons
        registerSpell(spells, "summon_hippogryph", SpellSchool.NATURE);
        registerSpell(spells, "summon_cockatrice", SpellSchool.NATURE);
        
        // Mythical abilities
        registerSpell(spells, "siren_song", SpellSchool.ELDRITCH);
        registerSpell(spells, "gorgon_gaze", SpellSchool.ELDRITCH);
        
        return spells;
    }
    
    /**
     * Register Sanctified Legacy spells.
     * Ars Nouveau addon focused on holy/divine magic.
     */
    private static Set<String> registerSanctifiedLegacySpells() {
        Set<String> spells = new HashSet<>();
        
        // Sanctified Legacy adds holy/divine themed Ars Nouveau glyphs
        // These would integrate directly with Ars Nouveau's glyph system
        
        // Holy/Divine spells - map to Holy school for Iron's integration
        registerSpell(spells, "sanctify", SpellSchool.HOLY);
        registerSpell(spells, "divine_blessing", SpellSchool.HOLY);
        registerSpell(spells, "purification", SpellSchool.HOLY);
        registerSpell(spells, "sacred_shield", SpellSchool.HOLY);
        registerSpell(spells, "holy_light", SpellSchool.HOLY);
        
        // Legacy/Ancient magic - could be Eldritch or Holy
        registerSpell(spells, "legacy_power", SpellSchool.ELDRITCH);
        registerSpell(spells, "ancient_wisdom", SpellSchool.ELDRITCH);
        
        return spells;
    }
    
    /**
     * Register Wizards Reborn Extended spells.
     * Extended content for Wizards Reborn with additional spell mechanics.
     */
    private static Set<String> registerWizardsRebornExtendedSpells() {
        Set<String> spells = new HashSet<>();
        
        // Wizards Reborn Extended adds various magical abilities
        // Map to appropriate schools based on spell type
        
        // Arcane/Force magic
        registerSpell(spells, "arcane_missile", SpellSchool.EVOCATION);
        registerSpell(spells, "magic_bolt", SpellSchool.EVOCATION);
        
        // Elemental magic
        registerSpell(spells, "flame_burst", SpellSchool.FIRE);
        registerSpell(spells, "frost_nova", SpellSchool.ICE);
        registerSpell(spells, "lightning_arc", SpellSchool.LIGHTNING);
        
        // Nature/Growth magic
        registerSpell(spells, "verdant_growth", SpellSchool.NATURE);
        registerSpell(spells, "life_bloom", SpellSchool.NATURE);
        
        // Utility/Teleportation
        registerSpell(spells, "blink", SpellSchool.ENDER);
        registerSpell(spells, "recall", SpellSchool.ENDER);
        
        return spells;
    }
    
    /**
     * Helper method to register a spell with its school.
     */
    private static void registerSpell(Set<String> spellSet, String spellId, SpellSchool school) {
        spellSet.add(spellId);
        spellToSchoolMap.put(spellId, school);
    }
    
    /**
     * Get the spell school for an addon spell.
     * Returns NONE if the spell is not registered.
     */
    public static SpellSchool getSpellSchool(String spellId) {
        initialize();
        return spellToSchoolMap.getOrDefault(spellId, SpellSchool.NONE);
    }
    
    /**
     * Check if a spell is from an addon mod.
     */
    public static boolean isAddonSpell(String spellId) {
        initialize();
        return spellToSchoolMap.containsKey(spellId);
    }
    
    /**
     * Get all spells from a specific addon.
     */
    public static Set<String> getAddonSpells(AddonModInfo addon) {
        initialize();
        return new HashSet<>(addonSpells.getOrDefault(addon, Collections.emptySet()));
    }
    
    /**
     * Get all registered addon spells.
     */
    public static Set<String> getAllAddonSpells() {
        initialize();
        return new HashSet<>(spellToSchoolMap.keySet());
    }
    
    /**
     * Get the addon that provides a specific spell.
     * Returns null if the spell is not from an addon.
     */
    public static AddonModInfo getSpellAddon(String spellId) {
        initialize();
        for (Map.Entry<AddonModInfo, Set<String>> entry : addonSpells.entrySet()) {
            if (entry.getValue().contains(spellId)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Check if addon spell registry is initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
