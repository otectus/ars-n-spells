package com.otectus.arsnspells.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class AnsConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // ========================================
    // MASTER TOGGLES
    // ========================================
    public static final ForgeConfigSpec.ConfigValue<String> MANA_UNIFICATION_MODE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_MANA_UNIFICATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_RESONANCE_SYSTEM;
    public static final ForgeConfigSpec.BooleanValue ENABLE_COOLDOWN_SYSTEM;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PROGRESSION_SYSTEM;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AFFINITY_SYSTEM;
    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE;

    // ========================================
    // MANA UNIFICATION SETTINGS
    // ========================================
    public static final ForgeConfigSpec.DoubleValue CONVERSION_RATE_ARS_TO_IRON;
    public static final ForgeConfigSpec.DoubleValue CONVERSION_RATE_IRON_TO_ARS;
    public static final ForgeConfigSpec.DoubleValue HYBRID_SYNC_RATE;
    public static final ForgeConfigSpec.ConfigValue<String> HYBRID_MANA_BAR;
    public static final ForgeConfigSpec.BooleanValue ALLOW_MANA_OVERFLOW;
    public static final ForgeConfigSpec.DoubleValue DUAL_COST_ARS_PERCENTAGE;
    public static final ForgeConfigSpec.DoubleValue DUAL_COST_ISS_PERCENTAGE;
    public static final ForgeConfigSpec.BooleanValue respectArmorBonuses;
    public static final ForgeConfigSpec.BooleanValue respectEnchantments;

    // ========================================
    // ARS GLYPH BONUSES
    // ========================================
    public static final ForgeConfigSpec.DoubleValue AMPLIFY_DAMAGE_BONUS;
    public static final ForgeConfigSpec.DoubleValue EXTEND_TIME_DURATION_BONUS;
    public static final ForgeConfigSpec.IntValue SPLIT_PROJECTILE_COUNT;
    public static final ForgeConfigSpec.DoubleValue PIERCE_ARMOR_PENETRATION;
    public static final ForgeConfigSpec.DoubleValue SENSITIVE_CRIT_BONUS;

    // ========================================
    // IRON'S SPELLBOOKS SCHOOL BONUSES
    // ========================================
    public static final ForgeConfigSpec.BooleanValue ENABLE_SCHOOL_BONUSES;
    public static final ForgeConfigSpec.DoubleValue SCHOOL_BONUS_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue FIRE_SCHOOL_DAMAGE_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ICE_SCHOOL_DURATION_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SCHOOL_CHAIN_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue EVOCATION_SCHOOL_SPEED_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue HOLY_SCHOOL_HEALING_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ENDER_SCHOOL_TELEPORT_RANGE_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue BLOOD_SCHOOL_LIFESTEAL_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue NATURE_SCHOOL_GROWTH_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ELDRITCH_SCHOOL_DEBUFF_PER_LEVEL;

    // ========================================
    // RESONANCE SYSTEM
    // ========================================
    public static final ForgeConfigSpec.BooleanValue ENABLE_ARS_RESONANCE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_IRONS_RESONANCE;
    public static final ForgeConfigSpec.DoubleValue RESONANCE_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue RESONANCE_THRESHOLD;
    public static final ForgeConfigSpec.IntValue RESONANCE_DURATION;
    public static final ForgeConfigSpec.DoubleValue MAX_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAX_DURATION_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAX_PROJECTILE_SPLIT;
    public static final ForgeConfigSpec.DoubleValue MAX_CHAIN_CHANCE;
    public static final ForgeConfigSpec.DoubleValue MAX_AREA_MULTIPLIER;

    // ========================================
    // COOLDOWN SYSTEM
    // ========================================
    public static final ForgeConfigSpec.BooleanValue ENABLE_UNIFIED_COOLDOWNS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CATEGORY_COOLDOWNS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CROSS_MOD_COOLDOWNS;
    public static final ForgeConfigSpec.IntValue COOLDOWN_CATEGORY_DURATION;
    public static final ForgeConfigSpec.DoubleValue CROSS_MOD_COOLDOWN_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue COOLDOWN_REDUCTION_CAP;

    // ========================================
    // PROGRESSION SYSTEM
    // ========================================
    public static final ForgeConfigSpec.DoubleValue PROGRESSION_XP_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MAX_PROGRESSION_LEVEL;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CROSS_MOD_PROGRESSION;

    // ========================================
    // AFFINITY SYSTEM
    // ========================================
    public static final ForgeConfigSpec.DoubleValue AFFINITY_BONUS_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAX_AFFINITY_BONUS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AFFINITY_DECAY;
    public static final ForgeConfigSpec.DoubleValue AFFINITY_DECAY_RATE;

    // ========================================
    // CURIO DISCOUNT SYSTEM
    // ========================================
    public static final ForgeConfigSpec.BooleanValue ENABLE_CURIO_DISCOUNTS;
    public static final ForgeConfigSpec.DoubleValue VIRTUE_RING_DISCOUNT;
    public static final ForgeConfigSpec.DoubleValue BLASPHEMY_DISCOUNT;
    public static final ForgeConfigSpec.DoubleValue BLASPHEMY_MATCHING_SCHOOL_BONUS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_DISCOUNT_STACKING;
    
    // ========================================
    // CURSED RING LP SYSTEM
    // ========================================
    public static final ForgeConfigSpec.ConfigValue<String> LP_SOURCE_MODE;
    public static final ForgeConfigSpec.BooleanValue DEATH_ON_INSUFFICIENT_LP;
    public static final ForgeConfigSpec.BooleanValue SHOW_LP_COST_MESSAGES;
    
    // LP Calculation for Ars Nouveau Spells
    public static final ForgeConfigSpec.DoubleValue ARS_LP_BASE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue ARS_LP_TIER1_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue ARS_LP_TIER2_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue ARS_LP_TIER3_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue ARS_LP_MINIMUM_COST;
    
    // LP Calculation for Iron's Spellbooks Spells
    public static final ForgeConfigSpec.DoubleValue IRONS_LP_BASE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue IRONS_LP_PER_LEVEL_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue IRONS_LP_MINIMUM_COST;
    
    // LP Rarity Multipliers for Iron's Spells
    public static final ForgeConfigSpec.DoubleValue IRONS_LP_COMMON_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue IRONS_LP_UNCOMMON_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue IRONS_LP_RARE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue IRONS_LP_EPIC_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue IRONS_LP_LEGENDARY_MULTIPLIER;

    // ========================================
    // PERFORMANCE TUNING
    // ========================================
    public static final ForgeConfigSpec.IntValue MANA_SYNC_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CACHING;
    public static final ForgeConfigSpec.IntValue CACHE_DURATION;

    static {
        // ========================================
        // MASTER TOGGLES
        // ========================================
        BUILDER.push("Master Toggles");
        BUILDER.comment(
            "Master control switches for major systems.",
            "Disabling a master toggle will disable all related features."
        );
        
        MANA_UNIFICATION_MODE = BUILDER
            .comment(
                "Mana Unification Mode - Controls how Ars Nouveau and Iron's Spellbooks mana systems interact:",
                "  iss_primary - Iron's Spellbooks mana is the single source (DEFAULT)",
                "  ars_primary - Ars Nouveau mana is the single source",
                "  hybrid - Both systems share a unified mana pool",
                "  separate - Separate pools with dual-cost mechanics",
                "  disabled - No mana integration at all"
            )
            .define("mana_unification_mode", "iss_primary");
        
        ENABLE_MANA_UNIFICATION = BUILDER
            .comment("Master toggle for all mana unification features")
            .define("enable_mana_unification", true);
        
        ENABLE_RESONANCE_SYSTEM = BUILDER
            .comment("Master toggle for the Resonance system (full mana bonuses)")
            .define("enable_resonance_system", true);
        
        ENABLE_COOLDOWN_SYSTEM = BUILDER
            .comment("Master toggle for unified cooldown system")
            .define("enable_cooldown_system", false);
        
        ENABLE_PROGRESSION_SYSTEM = BUILDER
            .comment("Master toggle for cross-mod progression system")
            .define("enable_progression_system", true);
        
        ENABLE_AFFINITY_SYSTEM = BUILDER
            .comment("Master toggle for affinity/attunement system")
            .define("enable_affinity_system", true);
        
        DEBUG_MODE = BUILDER
            .comment("Enable debug logging for troubleshooting")
            .define("debug_mode", false);
        
        BUILDER.pop();

        // ========================================
        // MANA UNIFICATION SETTINGS
        // ========================================
        BUILDER.push("Mana Unification");
        BUILDER.comment(
            "Fine-tune how mana conversion and synchronization works.",
            "These settings only apply when mana unification is enabled."
        );
        
        CONVERSION_RATE_ARS_TO_IRON = BUILDER
            .comment("Conversion rate from Ars mana to Iron's mana (1.0 = 1:1)")
            .defineInRange("conversion_rate_ars_to_iron", 1.0, 0.01, 100.0);
        
        CONVERSION_RATE_IRON_TO_ARS = BUILDER
            .comment("Conversion rate from Iron's mana to Ars mana (1.0 = 1:1)")
            .defineInRange("conversion_rate_iron_to_ars", 1.0, 0.01, 100.0);
        
        HYBRID_SYNC_RATE = BUILDER
            .comment("How often hybrid pools sync (in ticks, 20 = 1 second)")
            .defineInRange("hybrid_sync_rate", 1.0, 0.1, 10.0);
        
        HYBRID_MANA_BAR = BUILDER
            .comment(
                "Which mana bar to display in HYBRID mode:",
                "  irons - Show Iron's Spellbooks mana bar",
                "  ars - Show Ars Nouveau mana bar",
                "Only applies when mana_unification_mode is set to 'hybrid'"
            )
            .define("hybrid_mana_bar", "irons");

        ALLOW_MANA_OVERFLOW = BUILDER
            .comment("Allow mana to overflow max capacity during conversion")
            .define("allow_mana_overflow", false);
        
        DUAL_COST_ARS_PERCENTAGE = BUILDER
            .comment("Percentage of Ars mana cost in SEPARATE mode (0.5 = 50%)")
            .defineInRange("dual_cost_ars_percentage", 0.5, 0.0, 1.0);
        
        DUAL_COST_ISS_PERCENTAGE = BUILDER
            .comment("Percentage of ISS mana cost in SEPARATE mode (0.5 = 50%)")
            .defineInRange("dual_cost_iss_percentage", 0.5, 0.0, 1.0);
        
        respectArmorBonuses = BUILDER
            .comment("Include armor bonuses in unified mana calculations")
            .define("respect_armor_bonuses", true);
        
        respectEnchantments = BUILDER
            .comment("Include enchantment bonuses in unified mana calculations")
            .define("respect_enchantments", true);
        
        BUILDER.pop();

        // ========================================
        // ARS GLYPH BONUSES
        // ========================================
        BUILDER.push("Ars Glyph Bonuses");
        BUILDER.comment("Bonuses applied by Ars Nouveau augment glyphs");
        
        AMPLIFY_DAMAGE_BONUS = BUILDER
            .comment("Damage multiplier per Amplify glyph")
            .defineInRange("amplify_damage_bonus", 0.2, 0.0, 10.0);
        
        EXTEND_TIME_DURATION_BONUS = BUILDER
            .comment("Duration multiplier per Extend Time glyph")
            .defineInRange("extend_time_duration_bonus", 1.5, 0.0, 10.0);
        
        SPLIT_PROJECTILE_COUNT = BUILDER
            .comment("Additional projectiles per Split glyph")
            .defineInRange("split_projectile_count", 1, 0, 10);
        
        PIERCE_ARMOR_PENETRATION = BUILDER
            .comment("Armor penetration per Pierce glyph")
            .defineInRange("pierce_armor_penetration", 0.1, 0.0, 1.0);
        
        SENSITIVE_CRIT_BONUS = BUILDER
            .comment("Critical damage multiplier per Sensitive glyph")
            .defineInRange("sensitive_crit_bonus", 0.25, 0.0, 10.0);
        
        BUILDER.pop();

        // ========================================
        // IRON'S SPELLBOOKS SCHOOL BONUSES
        // ========================================
        BUILDER.push("Iron's School Bonuses");
        BUILDER.comment("Bonuses from Iron's Spellbooks school levels");
        
        ENABLE_SCHOOL_BONUSES = BUILDER
            .comment("Enable school level bonuses for Ars spells")
            .define("enable_school_bonuses", true);
        
        SCHOOL_BONUS_MULTIPLIER = BUILDER
            .comment("Global multiplier for all school bonuses")
            .defineInRange("school_bonus_multiplier", 1.0, 0.0, 10.0);
        
        FIRE_SCHOOL_DAMAGE_PER_LEVEL = BUILDER
            .comment("Fire damage bonus per school level")
            .defineInRange("fire_school_damage", 0.02, 0.0, 1.0);
        
        ICE_SCHOOL_DURATION_PER_LEVEL = BUILDER
            .comment("Ice effect duration bonus per school level")
            .defineInRange("ice_school_duration", 0.02, 0.0, 1.0);
        
        LIGHTNING_SCHOOL_CHAIN_PER_LEVEL = BUILDER
            .comment("Lightning chain chance bonus per school level")
            .defineInRange("lightning_school_chain", 0.02, 0.0, 1.0);
        
        EVOCATION_SCHOOL_SPEED_PER_LEVEL = BUILDER
            .comment("Evocation projectile speed bonus per school level")
            .defineInRange("evocation_school_speed", 0.02, 0.0, 1.0);
        
        HOLY_SCHOOL_HEALING_PER_LEVEL = BUILDER
            .comment("Holy healing bonus per school level")
            .defineInRange("holy_school_healing", 0.02, 0.0, 1.0);
        
        ENDER_SCHOOL_TELEPORT_RANGE_PER_LEVEL = BUILDER
            .comment("Ender teleport range bonus per school level")
            .defineInRange("ender_school_range", 0.02, 0.0, 1.0);
        
        BLOOD_SCHOOL_LIFESTEAL_PER_LEVEL = BUILDER
            .comment("Blood lifesteal bonus per school level")
            .defineInRange("blood_school_lifesteal", 0.02, 0.0, 1.0);
        
        NATURE_SCHOOL_GROWTH_PER_LEVEL = BUILDER
            .comment("Nature growth effect bonus per school level")
            .defineInRange("nature_school_growth", 0.02, 0.0, 1.0);
        
        ELDRITCH_SCHOOL_DEBUFF_PER_LEVEL = BUILDER
            .comment("Eldritch debuff strength bonus per school level")
            .defineInRange("eldritch_school_debuff", 0.02, 0.0, 1.0);
        
        BUILDER.pop();

        // ========================================
        // RESONANCE SYSTEM
        // ========================================
        BUILDER.push("Resonance System");
        BUILDER.comment(
            "Resonance grants bonuses when mana is at maximum.",
            "Requires ENABLE_RESONANCE_SYSTEM master toggle."
        );
        
        ENABLE_ARS_RESONANCE = BUILDER
            .comment("Enable resonance bonuses for Ars Nouveau spells")
            .define("enable_ars_resonance", true);
        
        ENABLE_IRONS_RESONANCE = BUILDER
            .comment("Enable resonance bonuses for Iron's Spellbooks spells")
            .define("enable_irons_resonance", true);
        
        RESONANCE_STRENGTH = BUILDER
            .comment("Global multiplier for all resonance bonuses")
            .defineInRange("resonance_strength", 1.0, 0.0, 10.0);
        
        RESONANCE_THRESHOLD = BUILDER
            .comment("Mana percentage required to trigger resonance (0.95 = 95%)")
            .defineInRange("resonance_threshold", 0.95, 0.0, 1.0);
        
        RESONANCE_DURATION = BUILDER
            .comment("How long resonance lasts after dropping below threshold (ticks)")
            .defineInRange("resonance_duration", 100, 0, 1200);
        
        MAX_DAMAGE_MULTIPLIER = BUILDER
            .comment("Maximum damage multiplier from resonance")
            .defineInRange("max_damage_multiplier", 5.0, 1.0, 100.0);
        
        MAX_DURATION_MULTIPLIER = BUILDER
            .comment("Maximum duration multiplier from resonance")
            .defineInRange("max_duration_multiplier", 5.0, 1.0, 100.0);
        
        MAX_PROJECTILE_SPLIT = BUILDER
            .comment("Maximum projectile split from resonance")
            .defineInRange("max_projectile_split", 5.0, 1.0, 100.0);
        
        MAX_CHAIN_CHANCE = BUILDER
            .comment("Maximum chain chance from resonance (1.0 = 100%)")
            .defineInRange("max_chain_chance", 1.0, 0.0, 1.0);
        
        MAX_AREA_MULTIPLIER = BUILDER
            .comment("Maximum area of effect multiplier from resonance")
            .defineInRange("max_area_multiplier", 5.0, 1.0, 100.0);
        
        BUILDER.pop();

        // ========================================
        // COOLDOWN SYSTEM
        // ========================================
        BUILDER.push("Cooldown System");
        BUILDER.comment(
            "Unified cooldown system prevents spell spam across mods.",
            "Requires ENABLE_COOLDOWN_SYSTEM master toggle."
        );
        
        ENABLE_UNIFIED_COOLDOWNS = BUILDER
            .comment("Enable cross-mod cooldown sharing")
            .define("enable_unified_cooldowns", false);
        
        ENABLE_CATEGORY_COOLDOWNS = BUILDER
            .comment("Enable category-based cooldowns (offensive, defensive, etc.)")
            .define("enable_category_cooldowns", false);
        
        ENABLE_CROSS_MOD_COOLDOWNS = BUILDER
            .comment("CRITICAL: Enable cross-mod cooldown interference (false = each mod has independent cooldowns)")
            .define("enable_cross_mod_cooldowns", false);
        
        COOLDOWN_CATEGORY_DURATION = BUILDER
            .comment("Base category cooldown duration (ticks, 20 = 1 second)")
            .defineInRange("cooldown_category_duration", 100, 0, 10000);
        
        CROSS_MOD_COOLDOWN_MULTIPLIER = BUILDER
            .comment("Multiplier for cross-mod cooldowns (0.5 = 50% of normal)")
            .defineInRange("cross_mod_cooldown_multiplier", 0.5, 0.0, 10.0);
        
        COOLDOWN_REDUCTION_CAP = BUILDER
            .comment("Maximum cooldown reduction from all sources (0.8 = 80% max reduction)")
            .defineInRange("cooldown_reduction_cap", 0.8, 0.0, 1.0);
        
        BUILDER.pop();

        // ========================================
        // PROGRESSION SYSTEM
        // ========================================
        BUILDER.push("Progression System");
        BUILDER.comment(
            "Cross-mod progression allows spell usage to grant XP in both systems.",
            "Requires ENABLE_PROGRESSION_SYSTEM master toggle."
        );
        
        PROGRESSION_XP_MULTIPLIER = BUILDER
            .comment("Multiplier for cross-mod XP gains")
            .defineInRange("progression_xp_multiplier", 1.0, 0.0, 10.0);
        
        MAX_PROGRESSION_LEVEL = BUILDER
            .comment("Maximum level for cross-mod progression bonuses")
            .defineInRange("max_progression_level", 100, 1, 1000);
        
        ENABLE_CROSS_MOD_PROGRESSION = BUILDER
            .comment("Allow Ars spells to grant ISS XP and vice versa")
            .define("enable_cross_mod_progression", true);
        
        BUILDER.pop();

        // ========================================
        // AFFINITY SYSTEM
        // ========================================
        BUILDER.push("Affinity System");
        BUILDER.comment(
            "Affinity system tracks spell school preferences and grants bonuses.",
            "Requires ENABLE_AFFINITY_SYSTEM master toggle."
        );
        
        AFFINITY_BONUS_MULTIPLIER = BUILDER
            .comment("Multiplier for affinity bonuses")
            .defineInRange("affinity_bonus_multiplier", 1.0, 0.0, 10.0);
        
        MAX_AFFINITY_BONUS = BUILDER
            .comment("Maximum bonus from affinity (0.25 = 25% max bonus)")
            .defineInRange("max_affinity_bonus", 0.25, 0.0, 10.0);
        
        ENABLE_AFFINITY_DECAY = BUILDER
            .comment("Enable affinity decay when not using a school")
            .define("enable_affinity_decay", true);
        
        AFFINITY_DECAY_RATE = BUILDER
            .comment("Rate of affinity decay per day (in-game)")
            .defineInRange("affinity_decay_rate", 0.01, 0.0, 1.0);
        
        BUILDER.pop();

        // ========================================
        // CURIO DISCOUNT SYSTEM
        // ========================================
        BUILDER.push("Curio Discount System");
        BUILDER.comment(
            "Mana cost discounts from Covenant of the Seven curios.",
            "Ring of Virtue and Blasphemy curios can reduce Ars Nouveau spell costs."
        );
        
        ENABLE_CURIO_DISCOUNTS = BUILDER
            .comment("Enable mana cost discounts from Ring of Virtue and Blasphemy curios")
            .define("enable_curio_discounts", true);
        
        VIRTUE_RING_DISCOUNT = BUILDER
            .comment("Mana cost discount from Ring of Virtue (0.20 = 20% reduction)")
            .defineInRange("virtue_ring_discount", 0.20, 0.0, 1.0);
        
        BLASPHEMY_DISCOUNT = BUILDER
            .comment("Base mana cost discount from Blasphemy curios (0.15 = 15% reduction)")
            .defineInRange("blasphemy_discount", 0.15, 0.0, 1.0);
        
        BLASPHEMY_MATCHING_SCHOOL_BONUS = BUILDER
            .comment("Additional discount when Blasphemy school matches spell school (0.10 = 10% extra)")
            .defineInRange("blasphemy_matching_school_bonus", 0.10, 0.0, 1.0);
        
        ALLOW_DISCOUNT_STACKING = BUILDER
            .comment("Allow Ring of Virtue and Blasphemy discounts to stack multiplicatively")
            .define("allow_discount_stacking", true);
        
        BUILDER.pop();

        // ========================================
        // CURSED RING LP SYSTEM
        // ========================================
        BUILDER.push("Cursed Ring LP System");
        BUILDER.comment(
            "Controls behavior when casting spells with insufficient LP (Cursed Ring).",
            "Applies to both Ars Nouveau and Iron's Spellbooks spells."
        );

        LP_SOURCE_MODE = BUILDER
            .comment(
                "Where to consume LP from when wearing the Cursed Ring:",
                "  BLOOD_MAGIC_ONLY - Only use Blood Magic Soul Network (authentic behavior)",
                "  BLOOD_MAGIC_PRIORITY - Use Blood Magic if available, fall back to health",
                "  HEALTH_ONLY - Always use player health (100 LP = 10 health = 5 hearts)",
                "Default: BLOOD_MAGIC_ONLY - Requires Blood Magic for Cursed Ring to function"
            )
            .define("lp_source_mode", "BLOOD_MAGIC_ONLY");

        DEATH_ON_INSUFFICIENT_LP = BUILDER
            .comment(
                "If enabled: Spell casts but player dies when LP is insufficient",
                "If disabled: Spell is cancelled and player takes minor damage (1 heart)"
            )
            .define("death_on_insufficient_lp", false);

        SHOW_LP_COST_MESSAGES = BUILDER
            .comment("Show LP cost messages in action bar when casting spells")
            .define("show_lp_cost_messages", true);

        BUILDER.pop();
        
        // ========================================
        // LP CALCULATION - ARS NOUVEAU SPELLS
        // ========================================
        BUILDER.push("LP Calculation - Ars Nouveau");
        BUILDER.comment(
            "Configure how LP costs are calculated for Ars Nouveau spells.",
            "Formula: LP = (Mana Cost × Base Multiplier) × Tier Multiplier",
            "Health cost = LP / 10 (so 10 LP = 1 health = 0.5 hearts)",
            "Minimum LP cost is enforced after all calculations."
        );

        ARS_LP_BASE_MULTIPLIER = BUILDER
            .comment("Base LP multiplier (Mana × this value = base LP cost)",
                "Default 1.0: A 20 mana spell costs ~30 LP = 3 health (1.5 hearts)")
            .defineInRange("ars_lp_base_multiplier", 1.0, 0.1, 100.0);

        ARS_LP_TIER1_MULTIPLIER = BUILDER
            .comment("LP multiplier for Tier 1 glyphs")
            .defineInRange("ars_lp_tier1_multiplier", 1.5, 0.1, 10.0);

        ARS_LP_TIER2_MULTIPLIER = BUILDER
            .comment("LP multiplier for Tier 2 glyphs")
            .defineInRange("ars_lp_tier2_multiplier", 2.0, 0.1, 10.0);

        ARS_LP_TIER3_MULTIPLIER = BUILDER
            .comment("LP multiplier for Tier 3 glyphs")
            .defineInRange("ars_lp_tier3_multiplier", 2.5, 0.1, 10.0);

        ARS_LP_MINIMUM_COST = BUILDER
            .comment("Minimum LP cost for any Ars Nouveau spell (10 LP = 1 health)")
            .defineInRange("ars_lp_minimum_cost", 10, 1, 10000);

        BUILDER.pop();

        // ========================================
        // LP CALCULATION - IRON'S SPELLBOOKS SPELLS
        // ========================================
        BUILDER.push("LP Calculation - Iron's Spellbooks");
        BUILDER.comment(
            "Configure how LP costs are calculated for Iron's Spellbooks spells.",
            "Formula: LP = (Mana Cost × Base Multiplier) × (1 + Level × Level Multiplier) × Rarity Multiplier",
            "Health cost = LP / 10 (so 10 LP = 1 health = 0.5 hearts)",
            "Minimum LP cost is enforced after all calculations."
        );

        IRONS_LP_BASE_MULTIPLIER = BUILDER
            .comment("Base LP multiplier (Mana × this value = base LP cost)",
                "Default 0.5: Iron's spells have higher base mana costs, so use lower multiplier")
            .defineInRange("irons_lp_base_multiplier", 0.5, 0.1, 100.0);

        IRONS_LP_PER_LEVEL_MULTIPLIER = BUILDER
            .comment("Additional LP cost per spell level (0.1 = 10% per level)")
            .defineInRange("irons_lp_per_level_multiplier", 0.1, 0.0, 10.0);

        IRONS_LP_MINIMUM_COST = BUILDER
            .comment("Minimum LP cost for any Iron's Spellbooks spell (10 LP = 1 health)")
            .defineInRange("irons_lp_minimum_cost", 10, 1, 10000);
        
        BUILDER.pop();
        
        // ========================================
        // LP RARITY MULTIPLIERS - IRON'S SPELLS
        // ========================================
        BUILDER.push("LP Rarity Multipliers - Iron's Spells");
        BUILDER.comment(
            "Multipliers based on spell rarity for Iron's Spellbooks.",
            "Higher rarity = higher LP cost."
        );
        
        IRONS_LP_COMMON_MULTIPLIER = BUILDER
            .comment("LP multiplier for COMMON rarity spells")
            .defineInRange("irons_lp_common_multiplier", 1.0, 0.1, 100.0);
        
        IRONS_LP_UNCOMMON_MULTIPLIER = BUILDER
            .comment("LP multiplier for UNCOMMON rarity spells")
            .defineInRange("irons_lp_uncommon_multiplier", 1.5, 0.1, 100.0);
        
        IRONS_LP_RARE_MULTIPLIER = BUILDER
            .comment("LP multiplier for RARE rarity spells")
            .defineInRange("irons_lp_rare_multiplier", 2.0, 0.1, 100.0);
        
        IRONS_LP_EPIC_MULTIPLIER = BUILDER
            .comment("LP multiplier for EPIC rarity spells")
            .defineInRange("irons_lp_epic_multiplier", 3.0, 0.1, 100.0);
        
        IRONS_LP_LEGENDARY_MULTIPLIER = BUILDER
            .comment("LP multiplier for LEGENDARY rarity spells")
            .defineInRange("irons_lp_legendary_multiplier", 5.0, 0.1, 100.0);
        
        BUILDER.pop();

        // ========================================
        // PERFORMANCE TUNING
        // ========================================
        BUILDER.push("Performance");
        BUILDER.comment("Performance optimization settings");
        
        MANA_SYNC_INTERVAL = BUILDER
            .comment("Mana synchronization interval (ticks, lower = more responsive but higher CPU)")
            .defineInRange("mana_sync_interval", 1, 1, 100);
        
        ENABLE_CACHING = BUILDER
            .comment("Enable caching for expensive calculations")
            .define("enable_caching", true);
        
        CACHE_DURATION = BUILDER
            .comment("Cache duration (ticks)")
            .defineInRange("cache_duration", 20, 1, 200);
        
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
    
    // ========================================
    // HELPER METHODS
    // ========================================
    
    /**
     * Get the current mana unification mode
     */
    public static ManaUnificationMode getManaMode() {
        if (!ENABLE_MANA_UNIFICATION.get()) {
            return ManaUnificationMode.DISABLED;
        }
        return ManaUnificationMode.fromString(MANA_UNIFICATION_MODE.get());
    }
    
    /**
     * Check if a specific system is enabled
     */
    public static boolean isSystemEnabled(String systemName) {
        switch (systemName.toLowerCase()) {
            case "mana": return ENABLE_MANA_UNIFICATION.get();
            case "resonance": return ENABLE_RESONANCE_SYSTEM.get();
            case "cooldown": return ENABLE_COOLDOWN_SYSTEM.get();
            case "progression": return ENABLE_PROGRESSION_SYSTEM.get();
            case "affinity": return ENABLE_AFFINITY_SYSTEM.get();
            default: return false;
        }
    }
    
    /**
     * Safe config save with retry logic to handle file locks
     */
    public static boolean safeSave() {
        int maxRetries = 3;
        int retryDelay = 100; // ms
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                SPEC.save();
                org.apache.logging.log4j.LogManager.getLogger().info("OK Config saved successfully");
                return true;
            } catch (Exception e) {
                org.apache.logging.log4j.LogManager.getLogger().warn(
                    "Config save attempt {} of {} failed: {}", i + 1, maxRetries, e.getMessage());
                
                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(retryDelay);
                        retryDelay *= 2; // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        org.apache.logging.log4j.LogManager.getLogger().error("Config save retry interrupted");
                        return false;
                    }
                } else {
                    org.apache.logging.log4j.LogManager.getLogger().error(
                        "FAILED to save config after {} attempts", maxRetries, e);
                }
            }
        }
        
        return false;
    }
}