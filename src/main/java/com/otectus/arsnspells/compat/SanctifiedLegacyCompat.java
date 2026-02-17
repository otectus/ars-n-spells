package com.otectus.arsnspells.compat;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.util.CuriosUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compatibility layer for Sanctified Legacy (Covenant of the Seven) mod.
 * 
 * Sanctified Legacy adds the Cursed Ring which replaces mana costs with Blood Magic LP.
 * This class detects when the player is wearing the Cursed Ring and applies LP costs
 * to Ars Nouveau spells (which Sanctified Legacy doesn't natively support).
 */
public class SanctifiedLegacyCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(SanctifiedLegacyCompat.class);
    private static final String MOD_ID = "covenant_of_the_seven";
    private static final String ENIGMATIC_LEGACY_MOD_ID = "enigmaticlegacy";
    private static final String BLOOD_MAGIC_MOD_ID = "bloodmagic";

    private static boolean isLoaded = false;
    private static boolean isEnigmaticLegacyLoaded = false;
    private static boolean isBloodMagicLoaded = false;
    private static boolean initialized = false;

    // Blood Magic reflection cache
    private static Class<?> bloodMagicNetworkClass = null;
    private static java.lang.reflect.Method getSoulNetworkMethod = null;
    private static java.lang.reflect.Method getCurrentEssenceMethod = null;
    private static java.lang.reflect.Method syphonMethod = null;

    /**
     * LP Source modes for config.
     */
    public enum LPSourceMode {
        BLOOD_MAGIC_PRIORITY,  // Use Blood Magic if available, fall back to health
        HEALTH_ONLY,           // Always use health
        BLOOD_MAGIC_ONLY       // Only use Blood Magic, fail if not installed
    }

    /**
     * Initialize the compatibility layer.
     * Detects Blood Magic for Soul Network support, with health fallback.
     */
    public static void init() {
        if (initialized) {
            return;
        }

        isLoaded = ModList.get().isLoaded(MOD_ID);
        isEnigmaticLegacyLoaded = ModList.get().isLoaded(ENIGMATIC_LEGACY_MOD_ID);
        isBloodMagicLoaded = ModList.get().isLoaded(BLOOD_MAGIC_MOD_ID);

        if (isLoaded || isEnigmaticLegacyLoaded) {
            LOGGER.info("Initializing Sanctified Legacy compatibility...");
            LOGGER.info("  Covenant of the Seven loaded: {}", isLoaded);
            LOGGER.info("  Enigmatic Legacy loaded: {}", isEnigmaticLegacyLoaded);
            LOGGER.info("  Blood Magic loaded: {}", isBloodMagicLoaded);
            LOGGER.info("  Using Curios API for curio detection");

            if (isBloodMagicLoaded) {
                initBloodMagicReflection();
            }

            // Warn if LP_SOURCE_MODE is BLOOD_MAGIC_ONLY but Blood Magic isn't installed
            LPSourceMode lpMode = getLPSourceMode();
            if (lpMode == LPSourceMode.BLOOD_MAGIC_ONLY && !isBloodMagicLoaded) {
                LOGGER.warn("  LP_SOURCE_MODE is BLOOD_MAGIC_ONLY but Blood Magic is not installed!");
                LOGGER.warn("  Cursed Ring LP costs will ALWAYS fail. Change to BLOOD_MAGIC_PRIORITY or HEALTH_ONLY.");
            }

            LOGGER.info("Sanctified Legacy compatibility layer initialized successfully");
        } else {
            LOGGER.info("Sanctified Legacy compatibility skipped: covenant_of_the_seven={}, enigmaticlegacy={}",
                isLoaded, isEnigmaticLegacyLoaded);
        }

        initialized = true;
    }

    /**
     * Initialize Blood Magic reflection for Soul Network access.
     */
    private static void initBloodMagicReflection() {
        try {
            // Try to find Blood Magic's NetworkHelper class
            bloodMagicNetworkClass = Class.forName("wayoftime.bloodmagic.util.helper.NetworkHelper");
            getSoulNetworkMethod = bloodMagicNetworkClass.getMethod("getSoulNetwork", java.util.UUID.class);

            // Get SoulNetwork class methods
            Class<?> soulNetworkClass = Class.forName("wayoftime.bloodmagic.core.data.SoulNetwork");
            getCurrentEssenceMethod = soulNetworkClass.getMethod("getCurrentEssence");
            syphonMethod = soulNetworkClass.getMethod("syphon", int.class);

            LOGGER.info("  ✅ Blood Magic Soul Network API initialized via reflection");
        } catch (ClassNotFoundException e) {
            LOGGER.warn("  ⚠️ Blood Magic classes not found - will use health fallback");
            isBloodMagicLoaded = false;
        } catch (NoSuchMethodException e) {
            LOGGER.warn("  ⚠️ Blood Magic API methods not found - will use health fallback");
            isBloodMagicLoaded = false;
        } catch (Exception e) {
            LOGGER.error("  ❌ Failed to initialize Blood Magic reflection", e);
            isBloodMagicLoaded = false;
        }
    }

    /**
     * Check if Blood Magic is available for LP consumption.
     */
    public static boolean isBloodMagicAvailable() {
        return isBloodMagicLoaded && bloodMagicNetworkClass != null;
    }

    /**
     * Check if Sanctified Legacy or Enigmatic Legacy is loaded.
     * Note: We only need one of these mods for the Cursed Ring to work.
     */
    public static boolean isAvailable() {
        return (isLoaded || isEnigmaticLegacyLoaded) && initialized;
    }
    
    /**
     * Check if the player is wearing the Cursed Ring (and not the Virtue Ring).
     * When wearing both rings, they cancel out and normal mana is used.
     * The Cursed Ring is from Enigmatic Legacy mod.
     */
    public static boolean isWearingCursedRing(Player player) {
        if (!isEnigmaticLegacyLoaded && !isLoaded) {
            return false;
        }

        try {
            boolean hasCursed = hasCurio(player, "enigmaticlegacy:cursed_ring");
            boolean hasVirtue = isLoaded && hasCurio(player, "covenant_of_the_seven:virtue_ring");

            // Cursed Ring only active if wearing it WITHOUT Virtue Ring
            return hasCursed && !hasVirtue;
        } catch (Exception e) {
            LOGGER.error("Failed to check for Cursed Ring", e);
            return false;
        }
    }

    /**
     * Check if the player is wearing the Virtue Ring (and not the Cursed Ring).
     * The Virtue Ring is from Covenant of the Seven mod.
     */
    public static boolean isWearingVirtueRing(Player player) {
        if (!isLoaded) {
            return false;
        }

        try {
            boolean hasCursed = isEnigmaticLegacyLoaded && hasCurio(player, "enigmaticlegacy:cursed_ring");
            boolean hasVirtue = hasCurio(player, "covenant_of_the_seven:virtue_ring");

            // Virtue Ring only active if wearing it WITHOUT Cursed Ring
            return hasVirtue && !hasCursed;
        } catch (Exception e) {
            LOGGER.error("Failed to check for Virtue Ring", e);
            return false;
        }
    }
    
    /**
     * Check if a player has a specific curio equipped using Ars Nouveau's CuriosUtil.
     * 
     * @param player The player
     * @param curioId The curio item ID (e.g., "enigmaticlegacy:cursed_ring")
     * @return true if the curio is equipped
     */
    private static boolean hasCurio(Player player, String curioId) {
        try {
            ResourceLocation itemId = new ResourceLocation(curioId);

            return CuriosUtil.getAllWornItems(player).map(handler -> {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        ResourceLocation stackId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                        if (stackId != null && stackId.equals(itemId)) {
                            return true;
                        }
                    }
                }
                return false;
            }).orElse(false);
        } catch (Exception e) {
            LOGGER.error("Failed to check for curio {}: {}", curioId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Calculate LP cost for an Ars Nouveau spell using configurable formula.
     * 
     * @param manaCost The base mana cost of the spell
     * @param spellPart The spell part (for tier info)
     * @return The LP cost
     */
    public static int calculateLPCost(int manaCost, AbstractSpellPart spellPart) {
        if (!isAvailable()) {
            return 0;
        }
        
        // Get base multiplier from config
        double baseMultiplier = com.otectus.arsnspells.config.AnsConfig.ARS_LP_BASE_MULTIPLIER.get();
        double baseLPCost = manaCost * baseMultiplier;
        
        // Get tier multiplier from config
        int tier = spellPart != null ? spellPart.getConfigTier().value : 1;
        double tierMultiplier;
        
        switch (tier) {
            case 1:
                tierMultiplier = com.otectus.arsnspells.config.AnsConfig.ARS_LP_TIER1_MULTIPLIER.get();
                break;
            case 2:
                tierMultiplier = com.otectus.arsnspells.config.AnsConfig.ARS_LP_TIER2_MULTIPLIER.get();
                break;
            case 3:
                tierMultiplier = com.otectus.arsnspells.config.AnsConfig.ARS_LP_TIER3_MULTIPLIER.get();
                break;
            default:
                tierMultiplier = 1.0;
                break;
        }
        
        int finalCost = (int) Math.round(baseLPCost * tierMultiplier);
        
        // Apply minimum cost from config
        int minimumCost = com.otectus.arsnspells.config.AnsConfig.ARS_LP_MINIMUM_COST.get();
        return Math.max(minimumCost, finalCost);
    }
    
    /**
     * Calculate LP cost for an Iron's Spellbooks spell using configurable formula.
     * 
     * @param manaCost The base mana cost of the spell
     * @param spellLevel The spell level
     * @param rarity The spell rarity (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY)
     * @return The LP cost
     */
    public static int calculateIronsLPCost(int manaCost, int spellLevel, String rarity) {
        if (!isAvailable()) {
            return 0;
        }
        
        // Get base multiplier from config
        double baseMultiplier = com.otectus.arsnspells.config.AnsConfig.IRONS_LP_BASE_MULTIPLIER.get();
        double baseLPCost = manaCost * baseMultiplier;
        
        // Apply level scaling
        double levelMultiplier = com.otectus.arsnspells.config.AnsConfig.IRONS_LP_PER_LEVEL_MULTIPLIER.get();
        double levelScaling = 1.0 + (spellLevel * levelMultiplier);
        baseLPCost *= levelScaling;
        
        // Apply rarity multiplier
        double rarityMultiplier = getRarityMultiplier(rarity);
        int finalCost = (int) Math.round(baseLPCost * rarityMultiplier);
        
        // Apply minimum cost from config
        int minimumCost = com.otectus.arsnspells.config.AnsConfig.IRONS_LP_MINIMUM_COST.get();
        return Math.max(minimumCost, finalCost);
    }
    
    /**
     * Get the rarity multiplier for Iron's Spellbooks spells.
     * 
     * @param rarity The spell rarity
     * @return The multiplier
     */
    private static double getRarityMultiplier(String rarity) {
        if (rarity == null) {
            return 1.0;
        }
        
        switch (rarity.toUpperCase()) {
            case "COMMON":
                return com.otectus.arsnspells.config.AnsConfig.IRONS_LP_COMMON_MULTIPLIER.get();
            case "UNCOMMON":
                return com.otectus.arsnspells.config.AnsConfig.IRONS_LP_UNCOMMON_MULTIPLIER.get();
            case "RARE":
                return com.otectus.arsnspells.config.AnsConfig.IRONS_LP_RARE_MULTIPLIER.get();
            case "EPIC":
                return com.otectus.arsnspells.config.AnsConfig.IRONS_LP_EPIC_MULTIPLIER.get();
            case "LEGENDARY":
                return com.otectus.arsnspells.config.AnsConfig.IRONS_LP_LEGENDARY_MULTIPLIER.get();
            default:
                return 1.0;
        }
    }
    
    /**
     * Get the configured LP source mode.
     */
    public static LPSourceMode getLPSourceMode() {
        String mode = com.otectus.arsnspells.config.AnsConfig.LP_SOURCE_MODE.get();
        try {
            return LPSourceMode.valueOf(mode.toUpperCase());
        } catch (Exception e) {
            return LPSourceMode.BLOOD_MAGIC_PRIORITY;
        }
    }

    /**
     * Check if player has enough LP for a spell.
     * Checks Blood Magic Soul Network first (if available and configured), then health.
     *
     * @param player The player
     * @param lpCost The LP cost
     * @return true if player has enough LP
     */
    public static boolean hasEnoughLP(Player player, int lpCost) {
        if (player == null) {
            return false;
        }

        LPSourceMode mode = getLPSourceMode();

        // Try Blood Magic first if configured
        if (mode == LPSourceMode.BLOOD_MAGIC_PRIORITY || mode == LPSourceMode.BLOOD_MAGIC_ONLY) {
            if (isBloodMagicAvailable()) {
                int currentLP = getBloodMagicLP(player);
                if (currentLP >= lpCost) {
                    return true;
                }
                // If Blood Magic only mode and not enough LP, fail
                if (mode == LPSourceMode.BLOOD_MAGIC_ONLY) {
                    return false;
                }
                // Fall through to health check
            } else if (mode == LPSourceMode.BLOOD_MAGIC_ONLY) {
                LOGGER.warn("Blood Magic not available but LP_SOURCE_MODE is BLOOD_MAGIC_ONLY");
                return false;
            }
        }

        // Health-based check (fallback or HEALTH_ONLY mode)
        float healthCost = lpCost / 10.0f; // 100 LP = 10 health = 5 hearts
        float currentHealth = player.getHealth();
        return currentHealth > healthCost + 1.0f;
    }

    /**
     * Attempt to consume LP from the player.
     * Uses Blood Magic Soul Network first (if available and configured), then health.
     *
     * @param player The player
     * @param lpCost The LP cost
     * @return true if LP was successfully consumed, false otherwise
     */
    public static boolean consumeLP(Player player, int lpCost) {
        if (player == null) {
            LOGGER.warn("Cannot consume LP: player is null");
            return false;
        }

        LPSourceMode mode = getLPSourceMode();

        // Try Blood Magic first if configured
        if (mode == LPSourceMode.BLOOD_MAGIC_PRIORITY || mode == LPSourceMode.BLOOD_MAGIC_ONLY) {
            if (isBloodMagicAvailable()) {
                int currentLP = getBloodMagicLP(player);
                LOGGER.debug("Attempting to consume {} LP from {}'s Soul Network (has {} LP)",
                    lpCost, player.getName().getString(), currentLP);

                if (currentLP >= lpCost) {
                    boolean success = consumeBloodMagicLP(player, lpCost);
                    if (success) {
                        LOGGER.debug("Successfully consumed {} LP from Soul Network", lpCost);
                        return true;
                    }
                }

                // If Blood Magic only mode and failed, don't fall back to health
                if (mode == LPSourceMode.BLOOD_MAGIC_ONLY) {
                    LOGGER.debug("Insufficient LP in Soul Network: need {} but only have {}", lpCost, currentLP);
                    return false;
                }

                LOGGER.debug("Insufficient Soul Network LP, falling back to health");
            } else if (mode == LPSourceMode.BLOOD_MAGIC_ONLY) {
                LOGGER.warn("Blood Magic not available but LP_SOURCE_MODE is BLOOD_MAGIC_ONLY");
                return false;
            }
        }

        // Health-based consumption (fallback or HEALTH_ONLY mode)
        float healthCost = lpCost / 10.0f; // 100 LP = 10 health = 5 hearts
        float currentHealth = player.getHealth();
        float maxHealth = player.getMaxHealth();

        LOGGER.debug("Attempting to consume {} LP ({} health) from {}'s health",
            lpCost, healthCost, player.getName().getString());
        LOGGER.debug("Current health: {}/{}", currentHealth, maxHealth);

        if (currentHealth <= healthCost + 1.0f) {
            LOGGER.debug("Insufficient health: need {} but only have {} (keeping 1 HP buffer)",
                healthCost, currentHealth);
            return false;
        }

        float newHealth = currentHealth - healthCost;
        player.setHealth(newHealth);

        LOGGER.debug("Successfully consumed {} LP ({} health) - new health: {}",
            lpCost, healthCost, newHealth);

        return true;
    }

    /**
     * Apply a silent health loss without triggering damage events.
     * Used for LP penalties when spells are cancelled in safe mode.
     *
     * @param player The player
     * @param healthLoss The amount of health to subtract
     */
    public static void applySilentHealthLoss(Player player, float healthLoss) {
        if (player == null) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        if (healthLoss <= 0.0f) {
            return;
        }
        float newHealth = Math.max(0.0f, player.getHealth() - healthLoss);
        player.setHealth(newHealth);
    }

    /**
     * Get player's current LP from Blood Magic Soul Network.
     */
    public static int getBloodMagicLP(Player player) {
        if (!isBloodMagicAvailable() || player == null) {
            return 0;
        }

        try {
            Object soulNetwork = getSoulNetworkMethod.invoke(null, player.getUUID());
            if (soulNetwork != null) {
                Object essence = getCurrentEssenceMethod.invoke(soulNetwork);
                if (essence instanceof Number) {
                    return ((Number) essence).intValue();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get Blood Magic LP", e);
        }
        return 0;
    }

    /**
     * Consume LP from Blood Magic Soul Network.
     */
    public static boolean consumeBloodMagicLP(Player player, int amount) {
        if (!isBloodMagicAvailable() || player == null) {
            return false;
        }

        try {
            Object soulNetwork = getSoulNetworkMethod.invoke(null, player.getUUID());
            if (soulNetwork != null) {
                // The syphon method returns the amount actually syphoned
                Object result = syphonMethod.invoke(soulNetwork, amount);
                if (result instanceof Number) {
                    int syphoned = ((Number) result).intValue();
                    return syphoned >= amount;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to consume Blood Magic LP", e);
        }
        return false;
    }

    /**
     * Check if the player has a Blasphemy curio that matches the spell school.
     * Blasphemy curios reduce LP costs by 85% for matching schools.
     * 
     * @param player The player
     * @param schoolType The spell school (e.g., "fire", "ice", "lightning")
     * @return true if player has matching Blasphemy curio
     */
    public static boolean hasMatchingBlasphemy(Player player, String schoolType) {
        if (!ModList.get().isLoaded(MOD_ID) || schoolType == null) {
            return false;
        }
        
        // Check for Blasphemy curio matching the school type
        String blasphemyId = "covenant_of_the_seven:" + schoolType.toLowerCase() + "_blasphemy";
        return hasCurio(player, blasphemyId);
    }
    
    /**
     * Get the LP cost multiplier based on Blasphemy curios.
     * 
     * @param player The player
     * @param schoolType The spell school
     * @return 0.15 if has matching Blasphemy (85% discount), 1.0 otherwise
     */
    public static double getBlasphemyMultiplier(Player player, String schoolType) {
        return hasMatchingBlasphemy(player, schoolType) ? 0.15 : 1.0;
    }
    
    // ========================================
    // MANA DISCOUNT SUPPORT (Ring of Virtue & Blasphemy)
    // ========================================
    
    /**
     * Check if the player is wearing the Ring of the Seven Virtues.
     * This is the standalone check (doesn't care about Cursed Ring).
     * 
     * @param player The player
     * @return true if wearing Ring of Virtue
     */
    public static boolean hasVirtueRing(Player player) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return false;
        }
        
        return hasCurio(player, "covenant_of_the_seven:virtue_ring");
    }
    
    /**
     * Check if the player has any Blasphemy curio equipped.
     * 
     * @param player The player
     * @return true if wearing any Blasphemy curio
     */
    public static boolean hasAnyBlasphemy(Player player) {
        if (!isAvailable()) {
            return false;
        }
        
        // Check all 13 Blasphemy variants
        String[] blasphemyTypes = {
            "fire_blasphemy", "ice_blasphemy", "lightning_blasphemy", "holy_blasphemy",
            "ender_blasphemy", "blood_blasphemy", "evocation_blasphemy", "nature_blasphemy",
            "eldritch_blasphemy", "aqua_blasphemy", "geo_blasphemy", "wind_blasphemy",
            "dormant_blasphemy"
        };
        
        for (String type : blasphemyTypes) {
            if (hasBlasphemyType(player, type)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if the player has a specific Blasphemy curio type.
     * 
     * @param player The player
     * @param blasphemyType The Blasphemy type (e.g., "fire_blasphemy")
     * @return true if wearing that specific Blasphemy
     */
    public static boolean hasBlasphemyType(Player player, String blasphemyType) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return false;
        }
        
        String curioId = "covenant_of_the_seven:" + blasphemyType;
        return hasCurio(player, curioId);
    }
    
    /**
     * Get the Blasphemy type that matches the given spell school.
     * 
     * @param spellSchool The spell school (e.g., "fire", "ice", "lightning")
     * @return The matching Blasphemy type, or null if no match
     */
    public static String getMatchingBlasphemyType(String spellSchool) {
        if (spellSchool == null) {
            return null;
        }
        
        String school = spellSchool.toLowerCase();
        
        // Direct mapping for most schools
        if (school.contains("fire") || school.contains("flame")) {
            return "fire_blasphemy";
        }
        if (school.contains("ice") || school.contains("frost") || school.contains("cold")) {
            return "ice_blasphemy";
        }
        if (school.contains("lightning") || school.contains("shock") || school.contains("storm")) {
            return "lightning_blasphemy";
        }
        if (school.contains("holy") || school.contains("light") || school.contains("heal")) {
            return "holy_blasphemy";
        }
        if (school.contains("ender") || school.contains("void") || school.contains("teleport")) {
            return "ender_blasphemy";
        }
        if (school.contains("blood") || school.contains("essence") || school.contains("drain")) {
            return "blood_blasphemy";
        }
        if (school.contains("evocation") || school.contains("machina") || school.contains("projectile")) {
            return "evocation_blasphemy";
        }
        if (school.contains("nature") || school.contains("wilds") || school.contains("earth") || school.contains("grow")) {
            return "nature_blasphemy";
        }
        if (school.contains("eldritch") || school.contains("anomaly") || school.contains("dark")) {
            return "eldritch_blasphemy";
        }
        if (school.contains("aqua") || school.contains("ocean") || school.contains("water")) {
            return "aqua_blasphemy";
        }
        if (school.contains("geo") || school.contains("stone") || school.contains("rock")) {
            return "geo_blasphemy";
        }
        if (school.contains("wind") || school.contains("air") || school.contains("sky") || school.contains("gust")) {
            return "wind_blasphemy";
        }
        
        return null;
    }
    
    /**
     * Determine the spell school from an Ars Nouveau spell part.
     * 
     * @param spellPart The spell part
     * @return The spell school identifier, or "generic" if unknown
     */
    public static String determineSpellSchool(AbstractSpellPart spellPart) {
        if (spellPart == null || spellPart.getRegistryName() == null) {
            return "generic";
        }
        
        String path = spellPart.getRegistryName().getPath().toLowerCase();
        
        // Analyze glyph name to determine school
        if (path.contains("fire") || path.contains("ignite") || path.contains("flare") || path.contains("burn")) {
            return "fire";
        }
        if (path.contains("ice") || path.contains("freeze") || path.contains("frost") || path.contains("cold")) {
            return "ice";
        }
        if (path.contains("lightning") || path.contains("shock") || path.contains("storm")) {
            return "lightning";
        }
        if (path.contains("heal") || path.contains("holy") || path.contains("light") && !path.contains("lightning")) {
            return "holy";
        }
        if (path.contains("ender") || path.contains("blink") || path.contains("warp") || path.contains("teleport")) {
            return "ender";
        }
        if (path.contains("blood") || path.contains("drain") || path.contains("life")) {
            return "blood";
        }
        if (path.contains("projectile") || path.contains("fang") || path.contains("evocation")) {
            return "evocation";
        }
        if (path.contains("grow") || path.contains("nature") || path.contains("plant") || path.contains("harvest")) {
            return "nature";
        }
        if (path.contains("wither") || path.contains("dark") || path.contains("hex")) {
            return "eldritch";
        }
        if (path.contains("water") || path.contains("conjure_water")) {
            return "aqua";
        }
        if (path.contains("earth") || path.contains("stone") || path.contains("crush")) {
            return "geo";
        }
        if (path.contains("wind") || path.contains("gust") || path.contains("air")) {
            return "wind";
        }
        
        return "generic";
    }
}
