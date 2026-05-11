package com.otectus.arsnspells.cooldown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps spells from both Ars Nouveau and Iron's Spells 'n Spellbooks to cooldown categories.
 * This centralizes spell categorization for the unified cooldown system.
 */
public class SpellCategoryMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpellCategoryMapper.class);

    // Maps for spell identification to categories
    private static final Map<String, CooldownCategory> arsSpellMap = new HashMap<>();
    private static final Map<String, CooldownCategory> ironsSpellMap = new HashMap<>();

    static {
        initializeArsMappings();
        initializeIronsMappings();
    }

    /**
     * Initialize Ars Nouveau spell mappings.
     * Based on glyph effects and spell behaviors.
     */
    private static void initializeArsMappings() {
        // Offensive spells - damage dealing
        arsSpellMap.put("ars_nouveau:glyph_amplify", CooldownCategory.OFFENSIVE);
        arsSpellMap.put("ars_nouveau:glyph_damage", CooldownCategory.OFFENSIVE);
        arsSpellMap.put("ars_nouveau:glyph_fire_damage", CooldownCategory.OFFENSIVE);
        arsSpellMap.put("ars_nouveau:glyph_cold_damage", CooldownCategory.OFFENSIVE);
        arsSpellMap.put("ars_nouveau:glyph_lightning_damage", CooldownCategory.OFFENSIVE);
        arsSpellMap.put("ars_nouveau:glyph_explosion", CooldownCategory.OFFENSIVE);
        arsSpellMap.put("ars_nouveau:glyph_knockback", CooldownCategory.OFFENSIVE);
        arsSpellMap.put("ars_nouveau:glyph_pierce", CooldownCategory.OFFENSIVE);

        // Defensive spells - protection and healing
        arsSpellMap.put("ars_nouveau:glyph_shield", CooldownCategory.DEFENSIVE);
        arsSpellMap.put("ars_nouveau:glyph_heal", CooldownCategory.DEFENSIVE);
        arsSpellMap.put("ars_nouveau:glyph_regeneration", CooldownCategory.DEFENSIVE);
        arsSpellMap.put("ars_nouveau:glyph_absorb", CooldownCategory.DEFENSIVE);
        arsSpellMap.put("ars_nouveau:glyph_summon_vex", CooldownCategory.DEFENSIVE);
        arsSpellMap.put("ars_nouveau:glyph_summon_wolves", CooldownCategory.DEFENSIVE);

        // Utility spells - information and manipulation
        arsSpellMap.put("ars_nouveau:glyph_intangible", CooldownCategory.UTILITY);
        arsSpellMap.put("ars_nouveau:glyph_light", CooldownCategory.UTILITY);
        arsSpellMap.put("ars_nouveau:glyph_gravity", CooldownCategory.UTILITY);
        arsSpellMap.put("ars_nouveau:glyph_harvest", CooldownCategory.UTILITY);
        arsSpellMap.put("ars_nouveau:glyph_grow", CooldownCategory.UTILITY);
        arsSpellMap.put("ars_nouveau:glyph_fortune", CooldownCategory.UTILITY);
        arsSpellMap.put("ars_nouveau:glyph_silk_touch", CooldownCategory.UTILITY);
        arsSpellMap.put("ars_nouveau:glyph_extract", CooldownCategory.UTILITY);
        arsSpellMap.put("ars_nouveau:glyph_place_block", CooldownCategory.UTILITY);
        arsSpellMap.put("ars_nouveau:glyph_break", CooldownCategory.UTILITY);
        arsSpellMap.put("ars_nouveau:glyph_dispel", CooldownCategory.UTILITY);

        // Movement spells - teleportation and speed
        arsSpellMap.put("ars_nouveau:glyph_blink", CooldownCategory.MOVEMENT);
        arsSpellMap.put("ars_nouveau:glyph_leap", CooldownCategory.MOVEMENT);
        arsSpellMap.put("ars_nouveau:glyph_slowfall", CooldownCategory.MOVEMENT);
        arsSpellMap.put("ars_nouveau:glyph_flight", CooldownCategory.MOVEMENT);
        arsSpellMap.put("ars_nouveau:glyph_pull", CooldownCategory.MOVEMENT);
        arsSpellMap.put("ars_nouveau:glyph_launch", CooldownCategory.MOVEMENT);

        LOGGER.info("[Cooldown] Initialized {} Ars Nouveau spell mappings", arsSpellMap.size());
    }

    /**
     * Initialize Iron's Spells 'n Spellbooks mappings.
     * Based on spell schools and effects.
     */
    private static void initializeIronsMappings() {
        // Offensive spells - damage dealing
        ironsSpellMap.put("irons_spellbooks:fireball", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:fire_bolt", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:fire_breath", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:blaze_storm", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:ice_spike", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:icicle", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:cone_of_cold", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:frost_step", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:lightning_bolt", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:chain_lightning", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:lightning_lance", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:electric_orb", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:poison_arrow", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:poison_spray", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:acid_orb", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:spider_aspect", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:blood_slash", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:ray_of_siphoning", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:eldritch_blast", CooldownCategory.OFFENSIVE);
        ironsSpellMap.put("irons_spellbooks:void_tentacles", CooldownCategory.OFFENSIVE);

        // Defensive spells - protection and healing
        ironsSpellMap.put("irons_spellbooks:shield", CooldownCategory.DEFENSIVE);
        ironsSpellMap.put("irons_spellbooks:greater_heal", CooldownCategory.DEFENSIVE);
        ironsSpellMap.put("irons_spellbooks:heal", CooldownCategory.DEFENSIVE);
        ironsSpellMap.put("irons_spellbooks:fortify", CooldownCategory.DEFENSIVE);
        ironsSpellMap.put("irons_spellbooks:heartstop", CooldownCategory.DEFENSIVE);
        ironsSpellMap.put("irons_spellbooks:blessing_of_life", CooldownCategory.DEFENSIVE);
        ironsSpellMap.put("irons_spellbooks:evasion", CooldownCategory.DEFENSIVE);
        ironsSpellMap.put("irons_spellbooks:summon_polar_bear", CooldownCategory.DEFENSIVE);
        ironsSpellMap.put("irons_spellbooks:summon_horse", CooldownCategory.DEFENSIVE);
        ironsSpellMap.put("irons_spellbooks:summon_villager", CooldownCategory.DEFENSIVE);

        // Utility spells - information and manipulation
        ironsSpellMap.put("irons_spellbooks:invisibility", CooldownCategory.UTILITY);
        ironsSpellMap.put("irons_spellbooks:telekinesis", CooldownCategory.UTILITY);
        ironsSpellMap.put("irons_spellbooks:wall_of_fire", CooldownCategory.UTILITY);
        ironsSpellMap.put("irons_spellbooks:wall_of_ice", CooldownCategory.UTILITY);
        ironsSpellMap.put("irons_spellbooks:wall_of_thorns", CooldownCategory.UTILITY);
        ironsSpellMap.put("irons_spellbooks:raise_dead", CooldownCategory.UTILITY);
        ironsSpellMap.put("irons_spellbooks:construct", CooldownCategory.UTILITY);
        ironsSpellMap.put("irons_spellbooks:fangs", CooldownCategory.UTILITY);
        ironsSpellMap.put("irons_spellbooks:counterspell", CooldownCategory.UTILITY);
        ironsSpellMap.put("irons_spellbooks:black_hole", CooldownCategory.UTILITY);
        ironsSpellMap.put("irons_spellbooks:mirror", CooldownCategory.UTILITY);

        // Movement spells - teleportation and speed
        ironsSpellMap.put("irons_spellbooks:teleport", CooldownCategory.MOVEMENT);
        ironsSpellMap.put("irons_spellbooks:blink", CooldownCategory.MOVEMENT);
        ironsSpellMap.put("irons_spellbooks:speed", CooldownCategory.MOVEMENT);
        ironsSpellMap.put("irons_spellbooks:feather_fall", CooldownCategory.MOVEMENT);
        ironsSpellMap.put("irons_spellbooks:ascended_blade", CooldownCategory.MOVEMENT);
        ironsSpellMap.put("irons_spellbooks:planar_sight", CooldownCategory.MOVEMENT);

        LOGGER.info("[Cooldown] Initialized {} Iron's Spells mappings", ironsSpellMap.size());
    }

    /**
     * Get the cooldown category for an Ars Nouveau spell.
     * @param spellId The spell identifier (glyph registry name)
     * @return The cooldown category, or null if not mapped
     */
    public static CooldownCategory getArsCategory(String spellId) {
        return arsSpellMap.get(spellId);
    }

    /**
     * Get the cooldown category for an Iron's Spells spell.
     * @param spellId The spell identifier
     * @return The cooldown category, or null if not mapped
     */
    public static CooldownCategory getIronsCategory(String spellId) {
        return ironsSpellMap.get(spellId);
    }

    /**
     * Check if a spell is mapped to any category.
     * @param spellId The spell identifier
     * @param isArsSpell True if this is an Ars Nouveau spell, false for Iron's Spells
     * @return True if the spell is mapped to a category
     */
    public static boolean isSpellMapped(String spellId, boolean isArsSpell) {
        return isArsSpell ? arsSpellMap.containsKey(spellId) : ironsSpellMap.containsKey(spellId);
    }

    /**
     * Get statistics about the spell mappings.
     * @return A formatted string with mapping statistics
     */
    public static String getStats() {
        return String.format("Spell Mappings - Ars: %d, Iron's: %d, Total: %d",
                           arsSpellMap.size(), ironsSpellMap.size(),
                           arsSpellMap.size() + ironsSpellMap.size());
    }
}
