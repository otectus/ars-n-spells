package com.otectus.arsnspells.compat;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.progression.SpellSchool;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamically discovers spells from addon mods at runtime.
 * Uses reflection to access spell registries and automatically categorize spells.
 */
public class DynamicSpellDiscovery {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicSpellDiscovery.class);
    
    private static final Map<ResourceLocation, SpellInfo> discoveredSpells = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;
    
    /**
     * Initialize dynamic spell discovery for all detected addon mods.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        
        synchronized (DynamicSpellDiscovery.class) {
            if (initialized) {
                return;
            }
            
            LOGGER.info("[Ars 'n' Spells] Starting dynamic spell discovery...");
            
            List<AddonModInfo> presentAddons = AddonModDetector.getPresentAddons();
            
            if (presentAddons.isEmpty()) {
                LOGGER.info("[Ars 'n' Spells] No addons detected for spell discovery");
                initialized = true;
                return;
            }
            
            int totalSpells = 0;
            
            for (AddonModInfo addon : presentAddons) {
                int spellCount = discoverSpellsFromAddon(addon);
                totalSpells += spellCount;
            }
            
            LOGGER.info("[Ars 'n' Spells] Dynamic spell discovery complete");
            LOGGER.info("[Ars 'n' Spells] Discovered {} spells from {} addon(s)", 
                totalSpells, presentAddons.size());
            
            initialized = true;
        }
    }
    
    /**
     * Discover spells from a specific addon mod.
     */
    private static int discoverSpellsFromAddon(AddonModInfo addon) {
        try {
            LOGGER.info("[Ars 'n' Spells] Discovering spells from {}...", addon.getDisplayName());
            
            int spellCount = 0;
            
            // Try different discovery methods based on addon type
            switch (addon.getType()) {
                case IRONS_ADDON:
                    spellCount = discoverIronsSpells(addon);
                    break;
                case ARS_ADDON:
                    spellCount = discoverArsSpells(addon);
                    break;
                case OTHER:
                    spellCount = discoverGenericSpells(addon);
                    break;
            }
            
            if (spellCount > 0) {
                LOGGER.info("[Ars 'n' Spells] Discovered {} spells from {}", 
                    spellCount, addon.getDisplayName());
            } else {
                LOGGER.warn("[Ars 'n' Spells] No spells discovered from {} (may need manual configuration)", 
                    addon.getDisplayName());
            }
            
            return spellCount;
            
        } catch (Exception e) {
            LOGGER.warn("[Ars 'n' Spells] Failed to discover spells from {}: {}", 
                addon.getDisplayName(), e.getMessage());
            
            if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
                LOGGER.warn("[Ars 'n' Spells] [DEBUG] Full error:", e);
            }
            
            return 0;
        }
    }
    
    /**
     * Discover spells from Iron's Spellbooks addon mods.
     */
    private static int discoverIronsSpells(AddonModInfo addon) {
        try {
            // Iron's Spellbooks uses a spell registry
            Class<?> spellRegistryClass = Class.forName("io.redspace.ironsspellbooks.api.registry.SpellRegistry");
            
            // Get the registry instance
            Field registryField = findField(spellRegistryClass, "REGISTRY", "SPELLS");
            if (registryField == null) {
                LOGGER.warn("[Ars 'n' Spells] Could not find spell registry field");
                return 0;
            }
            
            registryField.setAccessible(true);
            Object registry = registryField.get(null);
            
            // Get all registered spells
            Method getEntriesMethod = findMethod(registry.getClass(), "getEntries", "entrySet", "values");
            if (getEntriesMethod == null) {
                LOGGER.warn("[Ars 'n' Spells] Could not find registry entries method");
                return 0;
            }
            
            getEntriesMethod.setAccessible(true);
            Object entries = getEntriesMethod.invoke(registry);
            
            int count = 0;
            
            // Iterate through entries
            if (entries instanceof Iterable) {
                for (Object entry : (Iterable<?>) entries) {
                    ResourceLocation spellId = extractSpellId(entry);
                    if (spellId != null && spellId.getNamespace().equals(addon.getModId())) {
                        SpellSchool school = categorizeSpellByName(spellId);
                        discoveredSpells.put(spellId, new SpellInfo(spellId, school, addon));
                        count++;
                        
                        logDebug("Discovered spell: {} -> {}", spellId, school);
                    }
                }
            }
            
            return count;
            
        } catch (ClassNotFoundException e) {
            logDebug("Iron's Spellbooks API not found (expected for non-Iron's addons)");
            return 0;
        } catch (Exception e) {
            LOGGER.warn("[Ars 'n' Spells] Error discovering Iron's spells: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Discover spells from Ars Nouveau addon mods.
     */
    private static int discoverArsSpells(AddonModInfo addon) {
        try {
            // Ars Nouveau uses a glyph registry
            Class<?> glyphRegistryClass = Class.forName("com.hollingsworth.arsnouveau.api.spell.SpellResolver");
            
            // Try to access the glyph registry
            // This is a simplified approach - actual implementation may vary
            logDebug("Attempting to discover Ars Nouveau glyphs from {}", addon.getDisplayName());
            
            // For now, return 0 and log that manual configuration may be needed
            LOGGER.info("[Ars 'n' Spells] Ars Nouveau addon detected: {} (using default integration)", 
                addon.getDisplayName());
            
            return 0;
            
        } catch (ClassNotFoundException e) {
            logDebug("Ars Nouveau API not found");
            return 0;
        } catch (Exception e) {
            LOGGER.warn("[Ars 'n' Spells] Error discovering Ars spells: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Discover spells from other compatible mods.
     */
    private static int discoverGenericSpells(AddonModInfo addon) {
        // Generic discovery for mods that don't fit the standard patterns
        LOGGER.info("[Ars 'n' Spells] Generic spell discovery for {} (may require manual configuration)", 
            addon.getDisplayName());
        return 0;
    }
    
    /**
     * Categorize a spell by its name/ID into an appropriate school.
     */
    private static SpellSchool categorizeSpellByName(ResourceLocation spellId) {
        String path = spellId.getPath().toLowerCase();
        
        // Fire-related keywords
        if (path.contains("fire") || path.contains("flame") || path.contains("burn") || 
            path.contains("ignite") || path.contains("blaze") || path.contains("inferno")) {
            return SpellSchool.FIRE;
        }
        
        // Ice-related keywords
        if (path.contains("ice") || path.contains("frost") || path.contains("freeze") || 
            path.contains("cold") || path.contains("snow") || path.contains("chill")) {
            return SpellSchool.ICE;
        }
        
        // Lightning-related keywords
        if (path.contains("lightning") || path.contains("thunder") || path.contains("shock") || 
            path.contains("electric") || path.contains("bolt") || path.contains("storm")) {
            return SpellSchool.LIGHTNING;
        }
        
        // Holy-related keywords
        if (path.contains("holy") || path.contains("divine") || path.contains("heal") || 
            path.contains("bless") || path.contains("sacred") || path.contains("sanctif") ||
            path.contains("purif") || path.contains("light") && !path.contains("lightning")) {
            return SpellSchool.HOLY;
        }
        
        // Ender-related keywords
        if (path.contains("ender") || path.contains("teleport") || path.contains("warp") || 
            path.contains("blink") || path.contains("portal") || path.contains("dimension")) {
            return SpellSchool.ENDER;
        }
        
        // Blood-related keywords
        if (path.contains("blood") || path.contains("life") || path.contains("drain") || 
            path.contains("vampir") || path.contains("sacrifice") || path.contains("soul")) {
            return SpellSchool.BLOOD;
        }
        
        // Nature-related keywords
        if (path.contains("nature") || path.contains("summon") || path.contains("growth") || 
            path.contains("plant") || path.contains("vine") || path.contains("root") ||
            path.contains("animal") || path.contains("beast")) {
            return SpellSchool.NATURE;
        }
        
        // Eldritch-related keywords
        if (path.contains("eldritch") || path.contains("void") || path.contains("dark") || 
            path.contains("curse") || path.contains("hex") || path.contains("corrupt") ||
            path.contains("chaos") || path.contains("forbidden")) {
            return SpellSchool.ELDRITCH;
        }
        
        // Evocation-related keywords (force, projectiles, etc.)
        if (path.contains("missile") || path.contains("arrow") || path.contains("projectile") || 
            path.contains("force") || path.contains("push") || path.contains("blast") ||
            path.contains("strike") || path.contains("slam")) {
            return SpellSchool.EVOCATION;
        }
        
        // Default to NONE if no keywords match
        return SpellSchool.NONE;
    }
    
    /**
     * Extract spell ID from a registry entry.
     */
    private static ResourceLocation extractSpellId(Object entry) {
        try {
            // Try to get the key/ID from the entry
            Method getKeyMethod = findMethod(entry.getClass(), "getKey", "getId", "getRegistryName");
            if (getKeyMethod != null) {
                getKeyMethod.setAccessible(true);
                Object key = getKeyMethod.invoke(entry);
                if (key instanceof ResourceLocation) {
                    return (ResourceLocation) key;
                }
            }
            
            // Try to get value and extract ID from it
            Method getValueMethod = findMethod(entry.getClass(), "getValue", "get");
            if (getValueMethod != null) {
                getValueMethod.setAccessible(true);
                Object value = getValueMethod.invoke(entry);
                
                Method getIdMethod = findMethod(value.getClass(), "getId", "getRegistryName", "getSpellId");
                if (getIdMethod != null) {
                    getIdMethod.setAccessible(true);
                    Object id = getIdMethod.invoke(value);
                    if (id instanceof ResourceLocation) {
                        return (ResourceLocation) id;
                    }
                }
            }
            
        } catch (Exception e) {
            logDebug("Failed to extract spell ID from entry: {}", e.getMessage());
        }
        
        return null;
    }
    
    // Helper methods
    
    private static Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Try next name
            }
        }
        return null;
    }
    
    private static Method findMethod(Class<?> clazz, String... names) {
        for (String name : names) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    return method;
                }
            }
        }
        return null;
    }
    
    private static void logDebug(String message, Object... args) {
        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[Ars 'n' Spells] [DEBUG] [DynamicDiscovery] " + message, args);
        }
    }
    
    // Public query methods
    
    public static SpellSchool getSpellSchool(ResourceLocation spellId) {
        initialize();
        SpellInfo info = discoveredSpells.get(spellId);
        return info != null ? info.school : SpellSchool.NONE;
    }
    
    public static boolean isAddonSpell(ResourceLocation spellId) {
        initialize();
        return discoveredSpells.containsKey(spellId);
    }
    
    public static Set<ResourceLocation> getAllDiscoveredSpells() {
        initialize();
        return new HashSet<>(discoveredSpells.keySet());
    }
    
    public static Map<AddonModInfo, Set<ResourceLocation>> getSpellsByAddon() {
        initialize();
        Map<AddonModInfo, Set<ResourceLocation>> result = new HashMap<>();
        
        for (Map.Entry<ResourceLocation, SpellInfo> entry : discoveredSpells.entrySet()) {
            result.computeIfAbsent(entry.getValue().addon, k -> new HashSet<>())
                  .add(entry.getKey());
        }
        
        return result;
    }
    
    /**
     * Information about a discovered spell.
     */
    private static class SpellInfo {
        final ResourceLocation id;
        final SpellSchool school;
        final AddonModInfo addon;
        
        SpellInfo(ResourceLocation id, SpellSchool school, AddonModInfo addon) {
            this.id = id;
            this.school = school;
            this.addon = addon;
        }
    }
}
