package com.otectus.arsnspells.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AnsConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // ========================================
    // MASTER TOGGLES
    // ========================================
    public static final ModConfigSpec.ConfigValue<String> MANA_UNIFICATION_MODE;
    public static final ModConfigSpec.BooleanValue ENABLE_MANA_UNIFICATION;
    public static final ModConfigSpec.BooleanValue ENABLE_RESONANCE_SYSTEM;
    public static final ModConfigSpec.BooleanValue ENABLE_COOLDOWN_SYSTEM;
    public static final ModConfigSpec.BooleanValue ENABLE_PROGRESSION_SYSTEM;
    public static final ModConfigSpec.BooleanValue ENABLE_AFFINITY_SYSTEM;
    public static final ModConfigSpec.BooleanValue DEBUG_MODE;

    // ========================================
    // MANA UNIFICATION SETTINGS
    // ========================================
    public static final ModConfigSpec.DoubleValue CONVERSION_RATE_ARS_TO_IRON;
    public static final ModConfigSpec.DoubleValue CONVERSION_RATE_IRON_TO_ARS;
    public static final ModConfigSpec.DoubleValue HYBRID_SYNC_RATE;
    public static final ModConfigSpec.ConfigValue<String> HYBRID_MANA_BAR;
    public static final ModConfigSpec.BooleanValue HIDE_MANA_BAR_WITH_RING;
    public static final ModConfigSpec.BooleanValue ALLOW_MANA_OVERFLOW;
    public static final ModConfigSpec.DoubleValue DUAL_COST_ARS_PERCENTAGE;
    public static final ModConfigSpec.DoubleValue DUAL_COST_ISS_PERCENTAGE;
    public static final ModConfigSpec.DoubleValue DEFAULT_MAX_MANA;
    public static final ModConfigSpec.BooleanValue respectArmorBonuses;
    public static final ModConfigSpec.BooleanValue respectEnchantments;
    public static final ModConfigSpec.ConfigValue<String> CROSS_SYSTEM_REGEN_CONVERSION;
    public static final ModConfigSpec.DoubleValue CROSS_SYSTEM_REGEN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CROSS_SYSTEM_REGEN_REFERENCE_POOL;

    // ========================================
    // ARS GLYPH BONUSES
    // ========================================
    public static final ModConfigSpec.DoubleValue AMPLIFY_DAMAGE_BONUS;
    public static final ModConfigSpec.DoubleValue EXTEND_TIME_DURATION_BONUS;
    public static final ModConfigSpec.IntValue SPLIT_PROJECTILE_COUNT;
    public static final ModConfigSpec.DoubleValue PIERCE_ARMOR_PENETRATION;
    public static final ModConfigSpec.DoubleValue SENSITIVE_CRIT_BONUS;

    // ========================================
    // IRON'S SPELLBOOKS SCHOOL BONUSES
    // ========================================
    public static final ModConfigSpec.BooleanValue ENABLE_SCHOOL_BONUSES;
    public static final ModConfigSpec.DoubleValue SCHOOL_BONUS_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue FIRE_SCHOOL_DAMAGE_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue ICE_SCHOOL_DURATION_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue LIGHTNING_SCHOOL_CHAIN_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue EVOCATION_SCHOOL_SPEED_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue HOLY_SCHOOL_HEALING_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue ENDER_SCHOOL_TELEPORT_RANGE_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue BLOOD_SCHOOL_LIFESTEAL_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue NATURE_SCHOOL_GROWTH_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue ELDRITCH_SCHOOL_DEBUFF_PER_LEVEL;

    // ========================================
    // RESONANCE SYSTEM
    // ========================================
    public static final ModConfigSpec.BooleanValue ENABLE_ARS_RESONANCE;
    public static final ModConfigSpec.BooleanValue ENABLE_IRONS_RESONANCE;
    public static final ModConfigSpec.DoubleValue RESONANCE_STRENGTH;
    public static final ModConfigSpec.DoubleValue RESONANCE_THRESHOLD;
    public static final ModConfigSpec.IntValue RESONANCE_DURATION;
    public static final ModConfigSpec.DoubleValue MAX_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_DURATION_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_PROJECTILE_SPLIT;
    public static final ModConfigSpec.DoubleValue MAX_CHAIN_CHANCE;
    public static final ModConfigSpec.DoubleValue MAX_AREA_MULTIPLIER;

    // ========================================
    // COOLDOWN SYSTEM
    // ========================================
    public static final ModConfigSpec.BooleanValue ENABLE_UNIFIED_COOLDOWNS;
    public static final ModConfigSpec.BooleanValue ENABLE_CATEGORY_COOLDOWNS;
    public static final ModConfigSpec.BooleanValue ENABLE_CROSS_MOD_COOLDOWNS;
    public static final ModConfigSpec.IntValue COOLDOWN_CATEGORY_DURATION;
    public static final ModConfigSpec.DoubleValue CROSS_MOD_COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue COOLDOWN_REDUCTION_CAP;

    // ========================================
    // PROGRESSION SYSTEM
    // ========================================
    public static final ModConfigSpec.BooleanValue ENABLE_CROSS_MOD_PROGRESSION;

    // ========================================
    // AFFINITY SYSTEM
    // ========================================
    public static final ModConfigSpec.BooleanValue ENABLE_AFFINITY_DECAY;
    public static final ModConfigSpec.DoubleValue AFFINITY_DECAY_RATE;
    public static final ModConfigSpec.IntValue AFFINITY_DECAY_INTERVAL_TICKS;

    // ========================================
    // CURIO DISCOUNT SYSTEM
    // ========================================
    public static final ModConfigSpec.BooleanValue ENABLE_CURIO_DISCOUNTS;
    public static final ModConfigSpec.DoubleValue VIRTUE_RING_DISCOUNT;
    public static final ModConfigSpec.DoubleValue BLASPHEMY_DISCOUNT;
    public static final ModConfigSpec.DoubleValue BLASPHEMY_MATCHING_SCHOOL_BONUS;
    public static final ModConfigSpec.BooleanValue ALLOW_DISCOUNT_STACKING;
    
    // ========================================
    // CURSED RING LP SYSTEM
    // ========================================
    public static final ModConfigSpec.BooleanValue ENABLE_LP_SYSTEM;
    public static final ModConfigSpec.ConfigValue<String> LP_SOURCE_MODE;
    public static final ModConfigSpec.BooleanValue DEATH_ON_INSUFFICIENT_LP;
    public static final ModConfigSpec.BooleanValue SHOW_LP_COST_MESSAGES;
    
    // LP Calculation for Ars Nouveau Spells
    public static final ModConfigSpec.DoubleValue ARS_LP_BASE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARS_LP_TIER1_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARS_LP_TIER2_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARS_LP_TIER3_MULTIPLIER;
    public static final ModConfigSpec.IntValue ARS_LP_MINIMUM_COST;
    
    // LP Calculation for Iron's Spellbooks Spells
    public static final ModConfigSpec.DoubleValue IRONS_LP_BASE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue IRONS_LP_PER_LEVEL_MULTIPLIER;
    public static final ModConfigSpec.IntValue IRONS_LP_MINIMUM_COST;
    
    // LP Rarity Multipliers for Iron's Spells
    public static final ModConfigSpec.DoubleValue IRONS_LP_COMMON_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue IRONS_LP_UNCOMMON_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue IRONS_LP_RARE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue IRONS_LP_EPIC_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue IRONS_LP_LEGENDARY_MULTIPLIER;

    // ========================================
    // AURA SYSTEM (Ring of Seven Virtues)
    // ========================================
    public static final ModConfigSpec.IntValue AURA_MAX_DEFAULT;
    public static final ModConfigSpec.DoubleValue AURA_REGEN_RATE;
    public static final ModConfigSpec.DoubleValue AURA_BASE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue AURA_TIER1_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue AURA_TIER2_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue AURA_TIER3_MULTIPLIER;
    public static final ModConfigSpec.IntValue AURA_MINIMUM_COST;
    public static final ModConfigSpec.BooleanValue SHOW_AURA_MESSAGES;

    // ========================================
    // SCROLL COST SYSTEM
    // ========================================
    public static final ModConfigSpec.ConfigValue<String> SCROLL_COST_MODE;

    // ========================================
    // SPELL SCALING
    // ========================================
    public static final ModConfigSpec.DoubleValue SPELL_POWER_CAP;

    // ========================================
    // BLASPHEMY RING DISCOUNTS
    // ========================================
    public static final ModConfigSpec.DoubleValue BLASPHEMY_LP_DISCOUNT;
    public static final ModConfigSpec.DoubleValue BLASPHEMY_AURA_DISCOUNT;

    // ========================================
    // SOURCE JAR SYNERGY
    // ========================================
    public static final ModConfigSpec.DoubleValue SOURCE_JAR_SYNERGY_MULTIPLIER;

    // ========================================
    // RITUALS
    // ========================================
    public static final ModConfigSpec.DoubleValue RITUAL_MANA_INFUSION_AMOUNT;
    public static final ModConfigSpec.IntValue MANA_WELL_RANGE;
    public static final ModConfigSpec.DoubleValue MANA_WELL_REGEN_RATE;

    // ========================================
    // CROSS-CAST INSCRIPTION
    // ========================================
    public static final ModConfigSpec.DoubleValue CROSS_CAST_COST_MULTIPLIER;

    // ========================================
    // PERFORMANCE TUNING
    // ========================================
    public static final ModConfigSpec.DoubleValue SOURCE_JAR_CACHE_MOVE_THRESHOLD;
    public static final ModConfigSpec.IntValue MANA_SYNC_INTERVAL;
    public static final ModConfigSpec.BooleanValue ENABLE_CACHING;
    public static final ModConfigSpec.IntValue CACHE_DURATION;

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
                "  disabled - No mana integration at all",
                "NOTE: Changing this value requires a game restart to take effect."
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
            .defineInRange("conversion_rate_ars_to_iron", 1.0, 0.01, 10.0);
        
        CONVERSION_RATE_IRON_TO_ARS = BUILDER
            .comment("Conversion rate from Iron's mana to Ars mana (1.0 = 1:1)")
            .defineInRange("conversion_rate_iron_to_ars", 1.0, 0.01, 10.0);
        
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

        HIDE_MANA_BAR_WITH_RING = BUILDER
            .comment(
                "Hide the Ars Nouveau and Iron's Spellbooks mana bars while wearing the",
                "Ring of the Seven Curses (LP) or Ring of the Seven Virtues (Aura).",
                "Spells consume LP/Aura instead of mana when these rings are equipped,",
                "so displaying a mana bar is misleading."
            )
            .define("hide_mana_bar_with_ring", true);

        ALLOW_MANA_OVERFLOW = BUILDER
            .comment("Allow mana to overflow max capacity during conversion")
            .define("allow_mana_overflow", false);
        
        DUAL_COST_ARS_PERCENTAGE = BUILDER
            .comment("Percentage of Ars mana cost in SEPARATE mode (0.5 = 50%)")
            .defineInRange("dual_cost_ars_percentage", 0.5, 0.0, 1.0);
        
        DUAL_COST_ISS_PERCENTAGE = BUILDER
            .comment("Percentage of ISS mana cost in SEPARATE mode (0.5 = 50%)")
            .defineInRange("dual_cost_iss_percentage", 0.5, 0.0, 1.0);
        
        DEFAULT_MAX_MANA = BUILDER
            .comment("Default maximum mana fallback when the native system returns no value.",
                     "Can be changed at runtime with /ans mana setdefault <value>",
                     "Applies to both Ars Nouveau and Iron's Spellbooks bridge fallbacks.")
            .defineInRange("default_max_mana", 100.0, 1.0, 100000.0);

        respectArmorBonuses = BUILDER
            .comment("Include armor bonuses in unified mana calculations")
            .define("respect_armor_bonuses", true);
        
        respectEnchantments = BUILDER
            .comment("Include enchantment bonuses in unified mana calculations")
            .define("respect_enchantments", true);

        CROSS_SYSTEM_REGEN_CONVERSION = BUILDER
            .comment(
                "How mana regen values are translated when crossing the Ars / Iron's boundary.",
                "Iron's regen is a percentage-of-pool multiplier; Ars regen is absolute mana/sec.",
                "Without conversion, an Ars enchantment like Mana Regen III applied to Iron's regen",
                "will produce hundreds of mana/sec because the absolute value is misread as a percentage.",
                "  EQUAL_EFFECT  - Convert by the target system's current max pool (DEFAULT, recommended).",
                "                  Preserves equivalent mana/sec on both sides at any pool size.",
                "  REFERENCE_POOL - Convert using a fixed reference pool (see cross_system_regen_reference_pool).",
                "                   Predictable but ignores the wearer's actual pool size.",
                "  DISABLED      - Do not translate cross-system regen at all. Mana Regen enchantments",
                "                  on Ars only affect Ars; Iron's gear regen only affects Iron's."
            )
            .define("cross_system_regen_conversion", "EQUAL_EFFECT");

        CROSS_SYSTEM_REGEN_MULTIPLIER = BUILDER
            .comment(
                "Global dampener applied to every cross-system regen translation.",
                "Use to tone down or boost the strength of cross-mod regen bonuses without",
                "disabling them entirely. 1.0 = full strength, 0.5 = half, 2.0 = double."
            )
            .defineInRange("cross_system_regen_multiplier", 1.0, 0.0, 100.0);

        CROSS_SYSTEM_REGEN_REFERENCE_POOL = BUILDER
            .comment(
                "Reference pool size used by REFERENCE_POOL conversion mode.",
                "Has no effect when cross_system_regen_conversion is EQUAL_EFFECT or DISABLED."
            )
            .defineInRange("cross_system_regen_reference_pool", 100.0, 1.0, 100000.0);

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
        
        ENABLE_AFFINITY_DECAY = BUILDER
            .comment("Enable affinity decay when not using a school. Default off for fresh installs (1.9.0).")
            .define("enable_affinity_decay", false);

        AFFINITY_DECAY_RATE = BUILDER
            .comment("Rate of affinity decay per day (in-game)")
            .defineInRange("affinity_decay_rate", 0.01, 0.0, 1.0);

        AFFINITY_DECAY_INTERVAL_TICKS = BUILDER
            .comment("Ticks between AffinityDecayHandler runs (20 = 1s, 1200 = 60s). Decay per run is prorated from AFFINITY_DECAY_RATE.")
            .defineInRange("affinity_decay_interval_ticks", 1200, 20, 24000);

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

        ENABLE_LP_SYSTEM = BUILDER
            .comment("Master toggle for the Cursed Ring LP system.",
                     "When disabled, spells use normal mana even with Cursed Ring equipped.")
            .define("enable_lp_system", true);

        LP_SOURCE_MODE = BUILDER
            .comment(
                "Where to consume LP from when wearing the Cursed Ring:",
                "  BLOOD_MAGIC_PRIORITY - Use Blood Magic if available, fall back to health (DEFAULT)",
                "  BLOOD_MAGIC_ONLY - Only use Blood Magic Soul Network (fails if Blood Magic not installed)",
                "  HEALTH_ONLY - Always use player health (100 LP = 10 health = 5 hearts)"
            )
            .define("lp_source_mode", "BLOOD_MAGIC_PRIORITY");

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
        // AURA SYSTEM (Ring of Seven Virtues)
        // ========================================
        BUILDER.push("Aura System");
        BUILDER.comment(
            "Aura resource system for Ring of Seven Virtues.",
            "When wearing the Virtue Ring, spell mana costs are replaced with aura costs.",
            "Aura regenerates passively over time."
        );

        AURA_MAX_DEFAULT = BUILDER
            .comment("Default maximum aura pool")
            .defineInRange("aura_max_default", 1000, 100, 100000);

        AURA_REGEN_RATE = BUILDER
            .comment("Aura regenerated per tick (20 ticks = 1 second). 0.5 = 10 aura/sec")
            .defineInRange("aura_regen_rate", 0.5, 0.0, 100.0);

        AURA_BASE_MULTIPLIER = BUILDER
            .comment("Base conversion from mana cost to aura cost (mana x this = base aura)")
            .defineInRange("aura_base_multiplier", 1.0, 0.1, 100.0);

        AURA_TIER1_MULTIPLIER = BUILDER
            .comment("Aura cost multiplier for Tier 1 glyphs")
            .defineInRange("aura_tier1_multiplier", 1.0, 0.1, 10.0);

        AURA_TIER2_MULTIPLIER = BUILDER
            .comment("Aura cost multiplier for Tier 2 glyphs")
            .defineInRange("aura_tier2_multiplier", 1.5, 0.1, 10.0);

        AURA_TIER3_MULTIPLIER = BUILDER
            .comment("Aura cost multiplier for Tier 3 glyphs")
            .defineInRange("aura_tier3_multiplier", 2.0, 0.1, 10.0);

        AURA_MINIMUM_COST = BUILDER
            .comment("Minimum aura cost for any spell")
            .defineInRange("aura_minimum_cost", 5, 1, 10000);

        SHOW_AURA_MESSAGES = BUILDER
            .comment("Show aura cost messages in action bar when casting spells")
            .define("show_aura_messages", true);

        BUILDER.pop();

        // ========================================
        // SCROLL COST SYSTEM
        // ========================================
        BUILDER.push("Scroll Cost System");
        BUILDER.comment(
            "Controls resource costs when casting Iron's Spellbooks spells from scrolls."
        );

        SCROLL_COST_MODE = BUILDER
            .comment(
                "Cost mode for Iron's Spellbooks scroll usage:",
                "  full - Scrolls consume mana and LP just like normal casting",
                "  lp_only - Scrolls are free of mana cost but still consume LP if Cursed Ring equipped",
                "  free - Scrolls have no resource cost (LP from Cursed Ring still applies)"
            )
            .define("scroll_cost_mode", "full");

        BUILDER.pop();

        // ========================================
        // SPELL SCALING
        // ========================================
        BUILDER.push("Spell Scaling");
        BUILDER.comment("Controls how spell power from Iron's attributes scales Ars spells.");

        SPELL_POWER_CAP = BUILDER
            .comment("Maximum total spell power multiplier from Iron's attributes.",
                     "Prevents stacking from exceeding this value. Set higher to allow more scaling.")
            .defineInRange("spell_power_cap", 3.0, 1.0, 10.0);

        BUILDER.pop();

        // ========================================
        // BLASPHEMY RING DISCOUNTS
        // ========================================
        BUILDER.push("Blasphemy Ring Discounts");
        BUILDER.comment(
            "Independent discount rates for LP and Aura costs when Blasphemy curios are equipped.",
            "These are separate from the mana discount (configured in Curio Discount System)."
        );

        BLASPHEMY_LP_DISCOUNT = BUILDER
            .comment("LP cost discount when matching Blasphemy is equipped (0.85 = 85% discount, pay only 15%)")
            .defineInRange("blasphemy_lp_discount", 0.85, 0.0, 1.0);

        BLASPHEMY_AURA_DISCOUNT = BUILDER
            .comment("Aura cost discount when matching Blasphemy is equipped (0.85 = 85% discount, pay only 15%)")
            .defineInRange("blasphemy_aura_discount", 0.85, 0.0, 1.0);

        BUILDER.pop();

        // ========================================
        // SOURCE JAR SYNERGY
        // ========================================
        BUILDER.push("Source Jar Synergy");
        BUILDER.comment("Controls the passive mana regen bonus when near Ars Nouveau Source Jars.");

        SOURCE_JAR_SYNERGY_MULTIPLIER = BUILDER
            .comment("Multiplier for Source Jar proximity regen bonus.",
                     "Higher values = stronger regen when standing near Source Jars.",
                     "Final bonus = CONVERSION_RATE_ARS_TO_IRON * this value per second.")
            .defineInRange("source_jar_synergy_multiplier", 5.0, 0.1, 100.0);

        BUILDER.pop();

        // ========================================
        // RITUALS
        // ========================================
        BUILDER.push("Rituals");
        BUILDER.comment("Configuration for Ars 'n' Spells custom rituals.");

        RITUAL_MANA_INFUSION_AMOUNT = BUILDER
            .comment("Amount of mana added by the Ritual of Mana Infusion")
            .defineInRange("ritual_mana_infusion_amount", 500.0, 1.0, 10000.0);

        MANA_WELL_RANGE = BUILDER
            .comment("Radius in blocks for Mana Well ritual effect")
            .defineInRange("mana_well_range", 8, 1, 64);

        MANA_WELL_REGEN_RATE = BUILDER
            .comment("Mana per tick granted to players within Mana Well range")
            .defineInRange("mana_well_regen_rate", 2.0, 0.1, 100.0);

        BUILDER.pop();

        // ========================================
        // CROSS-CAST INSCRIPTION
        // ========================================
        BUILDER.push("Cross-Cast Inscription");
        BUILDER.comment(
            "Controls spells cast from items inscribed via the Spell Transcription ritual.",
            "The inscription mechanic itself lives in the datapack recipe under",
            "data/ars_n_spells/recipes/apparatus/ -- pack authors can swap ingredients there."
        );

        CROSS_CAST_COST_MULTIPLIER = BUILDER
            .comment(
                "Multiplier applied to the base mana cost of a spell cast from an inscribed item.",
                "Represents the overhead of casting through a foreign mod's form. 1.0 = no overhead,",
                "1.25 = 25% extra cost (default). Applied once, after base cost calculation and",
                "before mana deduction. Routed through BridgeManager, so it composes with the",
                "active mana unification mode and SEPARATE-mode dual-cost splitting."
            )
            .defineInRange("cross_cast_cost_multiplier", 1.25, 0.5, 5.0);

        BUILDER.pop();

        // ========================================
        // PERFORMANCE TUNING
        // ========================================
        BUILDER.push("Performance");
        BUILDER.comment("Performance optimization settings");

        SOURCE_JAR_CACHE_MOVE_THRESHOLD = BUILDER
            .comment("Distance in blocks a player must move before re-scanning for Source Jars.",
                     "Higher values = less scanning but slower detection of jar changes.")
            .defineInRange("source_jar_cache_move_threshold", 4.0, 1.0, 32.0);
        
        MANA_SYNC_INTERVAL = BUILDER
            .comment("Mana synchronization interval (ticks, lower = more responsive but higher CPU)")
            .defineInRange("mana_sync_interval", 5, 2, 100);
        
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
            case "lp": return ENABLE_LP_SYSTEM.get();
            default: return false;
        }
    }
    
    /** Daemon executor so config writes never block the caller (render / server thread). */
    private static final java.util.concurrent.ExecutorService SAVE_EXEC =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ans-config-save");
            t.setDaemon(true);
            return t;
        });

    /**
     * Schedule an off-thread config save. Returns true once the write is queued
     * (the log is the source of truth for completion). Async because the in-game
     * config screen and {@code /ans} commands call this from the render / server
     * thread, where a synchronous file write under lock contention would stall the
     * tick — the old synchronous {@code Thread.sleep} retry loop (ANS-HIGH-017).
     */
    public static boolean safeSave() {
        SAVE_EXEC.submit(() -> {
            try {
                SPEC.save();
                org.slf4j.LoggerFactory.getLogger(AnsConfig.class).info("OK Config saved successfully");
            } catch (Exception e) {
                // No retry: retrying on the save thread risks stalling under lock
                // contention, and the next mutation saves again anyway.
                org.slf4j.LoggerFactory.getLogger(AnsConfig.class).warn(
                    "Config save failed (not retried): {}", e.getMessage(), e);
            }
        });
        return true;
    }
}