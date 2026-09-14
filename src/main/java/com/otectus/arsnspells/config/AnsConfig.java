package com.otectus.arsnspells.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class AnsConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // ========================================
    // SCHEMA MIGRATION
    // ========================================
    // 3.3.0: the config file now says which schema wrote it, so a key added in this release
    // can resolve a different default for an existing world than for a new install. Without
    // that distinction every migration is a forced choice between breaking existing worlds
    // and shipping the unsafe behaviour to everyone forever.
    public static final ForgeConfigSpec.IntValue CONFIG_SCHEMA_VERSION;
    public static final ForgeConfigSpec.ConfigValue<String> CONVERSION_POLICY;
    public static final ForgeConfigSpec.ConfigValue<String> PAYMENT_OPEN_FAILURE_POLICY;

    /** The schema this build writes. A file that reads back lower than this predates 3.3.0. */
    public static final int CURRENT_SCHEMA_VERSION = 2;

    /**
     * The {@code payment_open_failure_policy} value that means "let the migration decide".
     * Anything else in the file is an explicit choice and is never overridden.
     */
    public static final String PAYMENT_POLICY_AUTO = "auto";

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
    // ANS-MED-031: HYBRID_SYNC_RATE removed — had zero readers, modpack-tuning trap.
    public static final ForgeConfigSpec.ConfigValue<String> HYBRID_MANA_BAR;
    public static final ForgeConfigSpec.BooleanValue HIDE_MANA_BAR_WITH_RING;
    // ANS-MED-031: ALLOW_MANA_OVERFLOW removed — had zero readers.
    public static final ForgeConfigSpec.DoubleValue DUAL_COST_ARS_PERCENTAGE;
    public static final ForgeConfigSpec.DoubleValue DUAL_COST_ISS_PERCENTAGE;
    public static final ForgeConfigSpec.DoubleValue DEFAULT_MAX_MANA;
    public static final ForgeConfigSpec.BooleanValue respectArmorBonuses;
    public static final ForgeConfigSpec.BooleanValue respectEnchantments;
    public static final ForgeConfigSpec.ConfigValue<String> CROSS_SYSTEM_REGEN_CONVERSION;
    public static final ForgeConfigSpec.DoubleValue CROSS_SYSTEM_REGEN_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue CROSS_SYSTEM_REGEN_REFERENCE_POOL;

    // ANS-MED-044: the ARS GLYPH BONUSES section (AMPLIFY_DAMAGE_BONUS,
    // EXTEND_TIME_DURATION_BONUS, SPLIT_PROJECTILE_COUNT, PIERCE_ARMOR_PENETRATION,
    // SENSITIVE_CRIT_BONUS) and the IRON'S SPELLBOOKS SCHOOL BONUSES section
    // (ENABLE_SCHOOL_BONUSES, SCHOOL_BONUS_MULTIPLIER, and the ten
    // *_SCHOOL_*_PER_LEVEL values) were removed — zero readers in the production
    // source; users tuning them saw no effect. Re-add alongside a real
    // implementation, same policy as ANS-HIGH-027.

    // ========================================
    // RESONANCE SYSTEM
    // ========================================
    public static final ForgeConfigSpec.BooleanValue ENABLE_ARS_RESONANCE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_IRONS_RESONANCE;
    public static final ForgeConfigSpec.DoubleValue RESONANCE_STRENGTH;
    public static final ForgeConfigSpec.DoubleValue RESONANCE_THRESHOLD;
    public static final ForgeConfigSpec.IntValue RESONANCE_DURATION;
    public static final ForgeConfigSpec.DoubleValue MAX_DAMAGE_MULTIPLIER;
    // ANS-MED-044: MAX_DURATION_MULTIPLIER, MAX_PROJECTILE_SPLIT, MAX_CHAIN_CHANCE,
    // and MAX_AREA_MULTIPLIER removed — only MAX_DAMAGE_MULTIPLIER is enforced
    // (ResonanceManager); the other four caps were never read.

    // ========================================
    // COOLDOWN SYSTEM
    // ========================================
    public static final ForgeConfigSpec.BooleanValue ENABLE_UNIFIED_COOLDOWNS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CROSS_MOD_COOLDOWNS;
    public static final ForgeConfigSpec.IntValue COOLDOWN_CATEGORY_DURATION;
    public static final ForgeConfigSpec.DoubleValue CROSS_MOD_COOLDOWN_MULTIPLIER;
    // ANS-MED-044: ENABLE_CATEGORY_COOLDOWNS and COOLDOWN_REDUCTION_CAP removed —
    // never read; the unified cooldown path only honors ENABLE_UNIFIED_COOLDOWNS,
    // ENABLE_CROSS_MOD_COOLDOWNS, COOLDOWN_CATEGORY_DURATION, and the multiplier.

    // ========================================
    // PROGRESSION SYSTEM
    // ========================================
    // ANS-MED-031 (verified, 3.0.1 audit): PROGRESSION_XP_MULTIPLIER and
    // MAX_PROGRESSION_LEVEL removed — zero readers; full JUnit + gametest suites
    // pass post-removal. The progression curve lives in
    // ProgressionData.getBonusForSchool (min(0.25, casts * 0.001)); making it
    // configurable again is deliberate feature work, not remediation.
    public static final ForgeConfigSpec.BooleanValue ENABLE_CROSS_MOD_PROGRESSION;
    public static final ForgeConfigSpec.DoubleValue PROGRESSION_BONUS_PER_CAST;
    public static final ForgeConfigSpec.DoubleValue PROGRESSION_BONUS_CAP;

    // ========================================
    // AFFINITY SYSTEM
    // ========================================
    // ANS-MED-031 (verified, 3.0.1 audit): AFFINITY_BONUS_MULTIPLIER and
    // MAX_AFFINITY_BONUS removed — zero readers; suites pass post-removal. The
    // affinity curve lives in AffinityCalculator (0.5% per level).
    public static final ForgeConfigSpec.BooleanValue ENABLE_AFFINITY_DECAY;
    public static final ForgeConfigSpec.DoubleValue AFFINITY_DECAY_RATE;
    public static final ForgeConfigSpec.IntValue AFFINITY_DECAY_INTERVAL_TICKS;

    // ========================================
    // CURIO DISCOUNT SYSTEM
    // ========================================
    public static final ForgeConfigSpec.BooleanValue ENABLE_CURIO_DISCOUNTS;
    public static final ForgeConfigSpec.DoubleValue BLASPHEMY_DISCOUNT;
    public static final ForgeConfigSpec.DoubleValue BLASPHEMY_MATCHING_SCHOOL_BONUS;
    // ANS-MED-044: ALLOW_DISCOUNT_STACKING removed — never read. The Virtue Ring
    // moved to aura conversion (zeroes cost before CurioDiscountHandler runs), so
    // there is no second discount left to stack with Blasphemy's.
    public static final ForgeConfigSpec.BooleanValue READ_CURIO_ATTRIBUTE_MODIFIERS;
    
    // ========================================
    // CURSED RING LP SYSTEM
    // ========================================
    public static final ForgeConfigSpec.BooleanValue ENABLE_LP_SYSTEM;
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
    // VIRTUE RING (Ring of Seven Virtues) - aura cost knob
    // ========================================
    // Covenant of the Seven owns the aura state, regen, max pool, and HUD. We only
    // need to know how to map an Ars Nouveau mana cost to an aura cost when the
    // Virtue Ring is worn — Covenant has no native getAuraCost(ArsSpell) method.
    // Master on/off switch for the aura path; mirrors ENABLE_LP_SYSTEM for the Cursed Ring.
    public static final ForgeConfigSpec.BooleanValue ENABLE_VIRTUE_AURA_SYSTEM;
    public static final ForgeConfigSpec.DoubleValue ARS_VIRTUE_AURA_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<String> AURA_FAILURE_MODE;

    // ========================================
    // SCROLL COST SYSTEM
    // ========================================
    public static final ForgeConfigSpec.ConfigValue<String> SCROLL_COST_MODE;

    // ========================================
    // SPELL SCALING
    // ========================================
    public static final ForgeConfigSpec.DoubleValue SPELL_POWER_CAP;

    // ========================================
    // BLASPHEMY RING DISCOUNTS
    // ========================================
    public static final ForgeConfigSpec.DoubleValue BLASPHEMY_LP_DISCOUNT;

    // ========================================
    // SOURCE JAR SYNERGY
    // ========================================
    public static final ForgeConfigSpec.BooleanValue ENABLE_SOURCE_JAR_SYNERGY;
    public static final ForgeConfigSpec.IntValue SOURCE_JAR_SCAN_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue SOURCE_JAR_SCAN_RADIUS;
    public static final ForgeConfigSpec.DoubleValue SOURCE_JAR_SYNERGY_MULTIPLIER;

    // ========================================
    // RITUALS
    // ========================================
    public static final ForgeConfigSpec.DoubleValue RITUAL_MANA_INFUSION_AMOUNT;
    public static final ForgeConfigSpec.IntValue MANA_WELL_RANGE;
    public static final ForgeConfigSpec.DoubleValue MANA_WELL_REGEN_RATE;

    // ========================================
    // CROSS-CAST INSCRIPTION
    // ========================================
    public static final ForgeConfigSpec.DoubleValue CROSS_CAST_COST_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue NETWORK_REQUEST_RATE_PER_SECOND;
    public static final ForgeConfigSpec.IntValue NETWORK_REQUEST_BURST;
    public static final ForgeConfigSpec.BooleanValue ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS;
    public static final ForgeConfigSpec.IntValue MAX_ARS_CROSS_SPELLS_PER_IRONS_SPELLBOOK;
    // ANS-HIGH-027: ENABLE_PER_CAST_REAGENT removed — was a reserved hook with zero
    // readers; setting it had no effect. If the per-cast reagent feature ever lands,
    // re-add the key alongside the implementation.

    // ========================================
    // PERFORMANCE TUNING
    // ========================================
    public static final ForgeConfigSpec.DoubleValue SOURCE_JAR_CACHE_MOVE_THRESHOLD;
    // ANS-MED-044: MANA_SYNC_INTERVAL, ENABLE_CACHING, and CACHE_DURATION removed —
    // never read. Sync cadence and the equipment/curio caches use fixed internal
    // constants (e.g. EquipmentIntegration.CACHE_DURATION_MS).

    static {
        // ========================================
        // SCHEMA MIGRATION
        // ========================================
        BUILDER.push("Schema Migration");
        BUILDER.comment(
            "How this file is migrated between Ars 'n' Spells versions.",
            "Do not edit config_schema_version by hand: it is how the mod tells an existing",
            "world apart from a new install when it resolves a new key's default."
        );

        CONFIG_SCHEMA_VERSION = BUILDER
            .comment(
                "Schema version of this config file. 0 means the file was written before 3.3.0,",
                "when this key did not exist; a freshly generated file is stamped with the",
                "current version instead. Keys added in a later release read this to decide",
                "whether to preserve the old behaviour or adopt the new default."
            )
            .defineInRange("config_schema_version", 0, 0, Integer.MAX_VALUE);

        CONVERSION_POLICY = BUILDER
            .comment(
                "Which policy prices a cross-system mana leg:",
                "  flat_legacy   - Multiply the cross leg by the configured directional rate.",
                "                  The historical arithmetic (DEFAULT; preserves current pricing).",
                "  equal_percent - Each pool pays the same percentage of the spell's own price;",
                "                  the directional rates are not consulted.",
                "These are deliberately separate policies, not two spellings of one. They agree",
                "only while both conversion rates are 1.0."
            )
            .define("conversion_policy", "flat_legacy");

        PAYMENT_OPEN_FAILURE_POLICY = BUILDER
            .comment(
                "What happens when an alternative payment (Cursed Ring LP, Virtue Ring aura)",
                "cannot be confirmed because its backing mod's adapter did not verify:",
                "  legacy_open     - Log and cast anyway. The pre-3.3.0 behaviour, which made the",
                "                    spell free whenever the adapter was broken.",
                "  refuse          - Refuse the cast. The safe policy.",
                "  native_fallback - Charge the native mana cost instead.",
                "  auto            - Resolve at load time (DEFAULT): legacy_open for a config file",
                "                    written before 3.3.0, refuse for a freshly generated one.",
                "                    Existing worlds keep their behaviour; new installs get the",
                "                    safe policy."
            )
            .define("payment_open_failure_policy", PAYMENT_POLICY_AUTO);

        BUILDER.pop();

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
            .comment("Master toggle for all mana unification features.",
                "NOTE: when false, mana_unification_mode is forced to DISABLED regardless",
                "of its configured value. Prefer mana_unification_mode = \"disabled\" for",
                "the canonical 'off' state — this boolean is the master kill-switch.")
            .define("enable_mana_unification", true);
        // ANS-MED-033: comment expanded so modpack authors understand the precedence.
        
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
            // ANS-MED-032: tightened upper bound from 100 to 10 — a 100x conversion
            // combined with other multipliers could blow past Float.MAX_VALUE in
            // pathological stacking. 10x covers any reasonable rebalance.
            .defineInRange("conversion_rate_ars_to_iron", 1.0, 0.01, 10.0);

        CONVERSION_RATE_IRON_TO_ARS = BUILDER
            .comment("Conversion rate from Iron's mana to Ars mana (1.0 = 1:1)")
            .defineInRange("conversion_rate_iron_to_ars", 1.0, 0.01, 10.0);

        // ANS-MED-031: HYBRID_SYNC_RATE registration removed — no readers.

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

        // ANS-MED-031: ALLOW_MANA_OVERFLOW registration removed — no readers.

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
            // ANS-MED-029: comment corrected /arsnspells -> /ans (the actual command root).
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

        // ANS-MED-044: "Ars Glyph Bonuses" and "Iron's School Bonuses" sections
        // removed — see the field-declaration note above.

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

        // ANS-MED-044: max_duration_multiplier, max_projectile_split,
        // max_chain_chance, max_area_multiplier removed — never enforced.

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
        
        // ANS-MED-044: enable_category_cooldowns removed — never read.

        ENABLE_CROSS_MOD_COOLDOWNS = BUILDER
            .comment("CRITICAL: Enable cross-mod cooldown interference (false = each mod has independent cooldowns)")
            .define("enable_cross_mod_cooldowns", false);
        
        COOLDOWN_CATEGORY_DURATION = BUILDER
            .comment("Base category cooldown duration (ticks, 20 = 1 second)")
            .defineInRange("cooldown_category_duration", 100, 0, 10000);
        
        CROSS_MOD_COOLDOWN_MULTIPLIER = BUILDER
            .comment("Multiplier for cross-mod cooldowns (0.5 = 50% of normal)")
            .defineInRange("cross_mod_cooldown_multiplier", 0.5, 0.0, 10.0);
        
        // ANS-MED-044: cooldown_reduction_cap removed — never enforced.

        BUILDER.pop();

        // ========================================
        // PROGRESSION SYSTEM
        // ========================================
        BUILDER.push("Progression System");
        BUILDER.comment(
            "Cross-mod progression allows spell usage to grant XP in both systems.",
            "Requires ENABLE_PROGRESSION_SYSTEM master toggle."
        );
        
        // ANS-MED-031 (verified): PROGRESSION_XP_MULTIPLIER and MAX_PROGRESSION_LEVEL
        // registrations removed — see field-declaration comment above.


        ENABLE_CROSS_MOD_PROGRESSION = BUILDER
            .comment("Allow Ars spells to grant ISS XP and vice versa")
            .define("enable_cross_mod_progression", true);

        PROGRESSION_BONUS_PER_CAST = BUILDER
            .comment("Attribute bonus gained per cast in a school (audit F4 — was hardcoded 0.001).",
                     "0.001 = +0.1% per cast. The bonus is transient (derived from the persistent",
                     "cast count), so changing this immediately rescales every player's bonus.")
            .defineInRange("progression_bonus_per_cast", 0.001, 0.0, 0.1);

        PROGRESSION_BONUS_CAP = BUILDER
            .comment("Cap on the per-school progression attribute bonus (audit F4 — was hardcoded 0.25).",
                     "0.25 = +25% maximum, reached after bonus_cap / bonus_per_cast casts",
                     "(250 casts at defaults).")
            .defineInRange("progression_bonus_cap", 0.25, 0.0, 2.0);

        BUILDER.pop();

        // ========================================
        // AFFINITY SYSTEM
        // ========================================
        BUILDER.push("Affinity System");
        BUILDER.comment(
            "Affinity system tracks spell school preferences and grants bonuses.",
            "Requires ENABLE_AFFINITY_SYSTEM master toggle."
        );
        
        // ANS-MED-031 (verified): AFFINITY_BONUS_MULTIPLIER and MAX_AFFINITY_BONUS
        // registrations removed — see field-declaration comment above.


        ENABLE_AFFINITY_DECAY = BUILDER
            .comment("Enable affinity decay when not casting matching-school spells.",
                     "Default changed to false in 1.9.0 — the previous true default was a no-op",
                     "(decay was never implemented), so flipping it on by default would surprise",
                     "existing players. Existing config files retain their previous value.")
            .define("enable_affinity_decay", false);

        AFFINITY_DECAY_RATE = BUILDER
            .comment("Fraction of current affinity to lose per Minecraft day (24000 ticks).",
                     "0.01 = lose 1% of each school's affinity per in-game day; with the default",
                     "interval (1200 ticks = 60s), each tick window decays roughly 0.05% of current.")
            .defineInRange("affinity_decay_rate", 0.01, 0.0, 1.0);

        AFFINITY_DECAY_INTERVAL_TICKS = BUILDER
            .comment("How often (in ticks) the decay handler ticks each player. 1200 = once per minute.")
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
            .comment("Enable mana cost discounts from Blasphemy curios.",
                     "Note: the Ring of Virtue is no longer a mana discount — it converts mana to",
                     "aura via VirtueRingHandler. The legacy virtue_ring_discount key was removed in 1.10.0.")
            .define("enable_curio_discounts", true);

        BLASPHEMY_DISCOUNT = BUILDER
            .comment("Base mana cost discount from Blasphemy curios (0.15 = 15% reduction)")
            .defineInRange("blasphemy_discount", 0.15, 0.0, 1.0);
        
        BLASPHEMY_MATCHING_SCHOOL_BONUS = BUILDER
            .comment("Additional discount when Blasphemy school matches spell school (0.10 = 10% extra)")
            .defineInRange("blasphemy_matching_school_bonus", 0.10, 0.0, 1.0);
        
        // ANS-MED-044: allow_discount_stacking removed — never read (the Virtue
        // Ring's aura conversion leaves no second discount to stack).

        READ_CURIO_ATTRIBUTE_MODIFIERS = BUILDER
            .comment("Read max-mana / mana-regen attribute modifiers from worn Curios (rings, amulets,",
                     "belts) and mirror them across the unified mana pool, the same way armor/weapon",
                     "modifiers are handled. This is what lets Apotheosis (Apothic Curios) affixes and",
                     "sockets, as well as other curio mana gear (Magical Jewelry, Jewelcraft, etc.),",
                     "feed the Ars <-> Iron's bridge. Disable if a curio affix balance proves overpowered.")
            .define("read_curio_attribute_modifiers", true);
        
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
        // VIRTUE RING (Ring of Seven Virtues)
        // ========================================
        // The parallel aura subsystem (max pool, regen, HUD, rarity/tier tables) was
        // removed because Covenant of the Seven already owns all of those. The only
        // knob we still need is the Ars-mana → aura-cost mapping, since Covenant has
        // no native ars-spell cost function.
        BUILDER.push("Virtue Ring");
        BUILDER.comment(
            "Cost-mapping knob for Ars Nouveau spells cast while wearing the Ring of",
            "Seven Virtues. Covenant of the Seven owns the actual aura pool, regen,",
            "max, and HUD — we only decide how to translate an Ars mana cost into an",
            "aura cost. Iron's Spellbooks spells are handled entirely by Covenant's",
            "native integration and do not use this knob."
        );

        ENABLE_VIRTUE_AURA_SYSTEM = BUILDER
            .comment("Master toggle for the Virtue Ring (Ring of Seven Virtues) aura-cost path.",
                     "When false, Ars Nouveau spells cast while wearing the Virtue Ring use normal",
                     "mana instead of Covenant aura. Mirrors enable_lp_system for the Cursed Ring,",
                     "giving server owners a way to disable the aura path entirely.")
            .define("enable_virtue_aura_system", true);

        ARS_VIRTUE_AURA_MULTIPLIER = BUILDER
            .comment("Multiplier applied to Ars Nouveau mana cost to derive the Covenant-aura cost.",
                     "1.0 = aura cost equals mana cost. Tune up to make the Virtue Ring path more",
                     "expensive than vanilla casting, or down to make it cheaper.")
            .defineInRange("ars_virtue_aura_multiplier", 1.0, 0.1, 10.0);

        AURA_FAILURE_MODE = BUILDER
            .comment("What happens to Virtue Ring aura-cost checks when the Covenant/Nature's Aura",
                     "reflection bridge is unavailable (e.g. an untested Covenant update changed",
                     "its internals):",
                     "  open   - allow the cast without an aura check (availability over economy;",
                     "           casts are effectively free while degraded — pre-3.0.2 behavior)",
                     "  closed - block Virtue Ring casts until the bridge works again",
                     "Either way the first degraded decision per session is logged at WARN.")
            .define("aura_failure_mode", "open",
                o -> o instanceof String s
                    && (s.equalsIgnoreCase("open") || s.equalsIgnoreCase("closed")));

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
                "  full - Scrolls cost the same as casting the spell normally",
                "  lp_only - Scrolls are free of mana cost but still consume LP if Cursed Ring equipped",
                "  free - Scrolls have no resource cost (LP from Cursed Ring still applies)",
                "Note: wearing the Cursed Ring (with enable_lp_system on) makes LP REPLACE mana,",
                "exactly as it does for normal casting - a ring wearer is never charged both."
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

        // Aura Blasphemy discount removed alongside the parallel aura subsystem.
        // Covenant of the Seven applies its own Blasphemy-based aura discount natively.

        BUILDER.pop();

        // ========================================
        // SOURCE JAR SYNERGY
        // ========================================
        BUILDER.push("Source Jar Synergy");
        BUILDER.comment("Controls the passive mana regen bonus when near Ars Nouveau Source Jars.");

        ENABLE_SOURCE_JAR_SYNERGY = BUILDER
            .comment("Master kill switch for the Source Jar proximity regen synergy.",
                     "Set false to disable the periodic block scan entirely (zero per-tick cost).",
                     "This is the supported way to turn the feature off;",
                     "source_jar_synergy_multiplier keeps its 0.1 minimum. (ANS-CRIT-005 follow-up)")
            .define("enable_source_jar_synergy", true);

        SOURCE_JAR_SCAN_INTERVAL_TICKS = BUILDER
            .comment("Ticks between Source Jar proximity checks per player (20 = once per second).",
                     "Raise on busy servers to reduce scan cost.")
            .defineInRange("source_jar_scan_interval_ticks", 20, 1, 200);

        SOURCE_JAR_SCAN_RADIUS = BUILDER
            .comment("Horizontal scan radius in blocks around the player.",
                     "Any value up to the hard cap of 8 covers at most a 2x2 chunk area.",
                     "The scan never loads chunks - cycles near unloaded chunks are skipped and retried.")
            .defineInRange("source_jar_scan_radius", 4, 1, 8);

        SOURCE_JAR_SYNERGY_MULTIPLIER = BUILDER
            .comment("Multiplier for Source Jar proximity regen bonus.",
                     "Higher values = stronger regen when standing near Source Jars.",
                     "Final bonus = CONVERSION_RATE_ARS_TO_IRON * this value per second.",
                     "To disable the feature use enable_source_jar_synergy = false, not a zero multiplier.")
            .defineInRange("source_jar_synergy_multiplier", 5.0, 0.01, 2000.0);

        BUILDER.pop();

        // ========================================
        // RITUALS
        // ========================================
        BUILDER.push("Rituals");
        BUILDER.comment("Configuration for Ars 'n' Spells custom rituals.");

        RITUAL_MANA_INFUSION_AMOUNT = BUILDER
            .comment("Amount of mana added by the Ritual of Mana Infusion.",
                "Capped at 10000 to prevent trivial infinite-mana from repeat casts.")
            // ANS-MED-034: upper bound tightened from 100000 to 10000.
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

        NETWORK_REQUEST_RATE_PER_SECOND = BUILDER
            .comment("Maximum sustained cross-cast/cycle requests per player per second. Server-owned; live reload.",
                "Repeated request IDs are ignored independently for 60 seconds. Does not limit native casts.")
            .defineInRange("network_request_rate_per_second", 10, 1, 20);
        NETWORK_REQUEST_BURST = BUILDER
            .comment("Immediate cross-cast/cycle request burst per player. Allows ordinary double clicks and latency bursts.")
            .defineInRange("network_request_burst", 4, 1, 20);

        ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS = BUILDER
            .comment(
                "Allow Ars Nouveau spells to be bound onto Iron's Spellbooks spellbooks, where",
                "they appear as their own entries in Iron's native spell wheel and cast through",
                "Ars 'n Spells' cross-cast pipeline. Disabling rejects new binds with a clear",
                "message; spells already bound keep working. No effect without Iron's installed."
            )
            .define("allow_ars_spells_in_irons_spellbooks", true);

        MAX_ARS_CROSS_SPELLS_PER_IRONS_SPELLBOOK = BUILDER
            .comment(
                "Maximum number of Ars spells that may be bound onto a single Iron's spellbook.",
                "-1 = no cap (default). Whatever the value, it is bounded at runtime by the",
                "native-wheel proxy pool size (" + com.otectus.arsnspells.spell.CrossCastNbt.PROXY_POOL_SIZE + "): only that many",
                "Ars entries can be shown as distinct entries in Iron's spell wheel per book."
            )
            .defineInRange("max_ars_cross_spells_per_irons_spellbook", -1, -1, 64);

        // ANS-HIGH-027: ENABLE_PER_CAST_REAGENT registration removed — had no readers,
        // setting it had zero effect. Re-add alongside an implementation if/when shipped.

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
        
        // ANS-MED-044: mana_sync_interval, enable_caching, and cache_duration
        // removed — never read; sync cadence and cache TTLs are fixed internal
        // constants.

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
            case "virtue":
            case "aura": return ENABLE_VIRTUE_AURA_SYSTEM.get();
            default: return false;
        }
    }
    
    // ========================================
    // SCHEMA MIGRATION (3.3.0)
    // ========================================

    private static final org.slf4j.Logger MIGRATION_LOG =
        org.slf4j.LoggerFactory.getLogger(AnsConfig.class);

    private static final java.util.concurrent.atomic.AtomicBoolean MIGRATION_REPORTED =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    private static volatile com.otectus.arsnspells.contract.ConversionKind EFFECTIVE_CONVERSION_KIND =
        com.otectus.arsnspells.contract.ConversionKind.FLAT_LEGACY;

    private static volatile com.otectus.arsnspells.contract.PaymentOpenFailurePolicy EFFECTIVE_PAYMENT_POLICY =
        com.otectus.arsnspells.contract.PaymentOpenFailurePolicy.LEGACY_OPEN;

    /** The conversion policy in force, resolved at config load. */
    public static com.otectus.arsnspells.contract.ConversionKind getConversionKind() {
        return EFFECTIVE_CONVERSION_KIND;
    }

    /** The alternative-payment failure policy in force, resolved at config load. */
    public static com.otectus.arsnspells.contract.PaymentOpenFailurePolicy getPaymentOpenFailurePolicy() {
        return EFFECTIVE_PAYMENT_POLICY;
    }

    /**
     * Parse {@code conversion_policy}. An unrecognised value falls back to the shipped default
     * rather than to whichever enum constant happens to be first, so a typo cannot silently
     * reprice every spell in the pack.
     */
    public static com.otectus.arsnspells.contract.ConversionKind parseConversionPolicy(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if ("equal_percent".equals(value)) {
            return com.otectus.arsnspells.contract.ConversionKind.EQUAL_PERCENT;
        }
        if (!"flat_legacy".equals(value)) {
            MIGRATION_LOG.warn(
                "Unknown conversion_policy '{}', falling back to flat_legacy. "
                    + "Valid values: flat_legacy, equal_percent.", raw);
        }
        return com.otectus.arsnspells.contract.ConversionKind.FLAT_LEGACY;
    }

    /**
     * Resolve {@code payment_open_failure_policy}.
     *
     * <p>An explicit value in the file always wins. {@link #PAYMENT_POLICY_AUTO} is the
     * migration case: a file written before 3.3.0 keeps {@code legacy_open} so an existing
     * world behaves exactly as it did, and a freshly generated file gets {@code refuse} so a
     * new install is not shipped the free-cast behaviour.
     *
     * <p>Pure, so the migration decision is unit-testable without a mod loading context.
     *
     * @param raw          the value read from the file
     * @param schemaRead   the {@code config_schema_version} read from the file
     * @param freshlyGenerated whether the file was created by this load
     */
    public static com.otectus.arsnspells.contract.PaymentOpenFailurePolicy resolvePaymentOpenFailurePolicy(
            String raw, int schemaRead, boolean freshlyGenerated) {
        String value = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if ("legacy_open".equals(value)) {
            return com.otectus.arsnspells.contract.PaymentOpenFailurePolicy.LEGACY_OPEN;
        }
        if ("refuse".equals(value)) {
            return com.otectus.arsnspells.contract.PaymentOpenFailurePolicy.REFUSE;
        }
        if ("native_fallback".equals(value)) {
            return com.otectus.arsnspells.contract.PaymentOpenFailurePolicy.NATIVE_FALLBACK;
        }
        if (!PAYMENT_POLICY_AUTO.equals(value)) {
            MIGRATION_LOG.warn(
                "Unknown payment_open_failure_policy '{}', resolving it as 'auto'. "
                    + "Valid values: legacy_open, refuse, native_fallback, auto.", raw);
        }
        boolean writtenByThisSchema = freshlyGenerated || schemaRead >= 1;
        return writtenByThisSchema
            ? com.otectus.arsnspells.contract.PaymentOpenFailurePolicy.REFUSE
            : com.otectus.arsnspells.contract.PaymentOpenFailurePolicy.LEGACY_OPEN;
    }

    /**
     * Whether {@code configFile} was created by the load that is running right now.
     *
     * <p>This is the one thing Forge does not tell us. A SERVER config lives in the world's
     * {@code serverconfig} directory, which does not exist until the server is already starting,
     * so no mod code runs between "the path is knowable" and "Forge has written the file". By
     * the time {@code ModConfigEvent.Loading} fires, a brand-new file and a corrected 3.2.x file
     * look identical: both contain {@code config_schema_version = 0}, one because the spec
     * default was written and one because the missing key was corrected to the spec default.
     *
     * <p>The file's creation timestamp does separate them: a pre-existing file was created in an
     * earlier session, even though the correction pass just rewrote its contents.
     *
     * <p>Every failure mode resolves to "not fresh", which is the direction that preserves an
     * existing world's behaviour. A filesystem with no birth time (some ext4 configurations)
     * reports the modification time instead; in that case the answer may be wrong once, on the
     * first 3.3.0 load, and a pack author who cares should set
     * {@code payment_open_failure_policy} explicitly.
     */
    public static boolean isFreshlyGeneratedConfig(java.nio.file.Path configFile) {
        if (configFile == null) {
            return false;
        }
        try {
            java.nio.file.attribute.BasicFileAttributes attrs =
                java.nio.file.Files.readAttributes(configFile, java.nio.file.attribute.BasicFileAttributes.class);
            long created = attrs.creationTime().toMillis();
            long jvmStart = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
            return created >= jvmStart;
        } catch (Exception unreadable) {
            return false;
        }
    }

    /**
     * Resolve the migrated keys and log the migration report once per game session.
     *
     * <p>Called from the mod's {@code ModConfigEvent.Loading} listener (MOD bus). The report
     * names only the keys whose effective value came from migration rather than from the file,
     * with the previous and the new effective value, so a server owner reading the log can see
     * exactly what the upgrade decided on their behalf.
     *
     * @param configFile the path Forge loaded, used only for the freshness probe
     */
    public static void onConfigLoaded(java.nio.file.Path configFile) {
        int schemaRead = AnsConfig.CONFIG_SCHEMA_VERSION.get();
        boolean fresh = schemaRead < CURRENT_SCHEMA_VERSION && isFreshlyGeneratedConfig(configFile);

        String rawConversion = AnsConfig.CONVERSION_POLICY.get();
        String rawPayment = AnsConfig.PAYMENT_OPEN_FAILURE_POLICY.get();
        EFFECTIVE_CONVERSION_KIND = parseConversionPolicy(rawConversion);
        EFFECTIVE_PAYMENT_POLICY = resolvePaymentOpenFailurePolicy(rawPayment, schemaRead, fresh);

        java.util.List<String> migrated = new java.util.ArrayList<>();
        if (schemaRead < CURRENT_SCHEMA_VERSION) {
            migrated.add("  conversion_policy: (absent) -> " + rawConversion
                + " (effective " + EFFECTIVE_CONVERSION_KIND + "; preserves pre-3.3.0 pricing)");
        }
        if (PAYMENT_POLICY_AUTO.equalsIgnoreCase(rawPayment)) {
            migrated.add("  payment_open_failure_policy: " + PAYMENT_POLICY_AUTO + " -> "
                + EFFECTIVE_PAYMENT_POLICY + (fresh
                    ? " (freshly generated config: the safe policy)"
                    : " (config written before 3.3.0: existing behaviour preserved)"));
        }
        if (schemaRead < CURRENT_SCHEMA_VERSION) {
            if (!fresh && configFile != null && java.nio.file.Files.exists(configFile)) {
                java.nio.file.Path backup = configFile.resolveSibling(configFile.getFileName() + ".pre-3.3.0.bak");
                try {
                    if (!java.nio.file.Files.exists(backup)) java.nio.file.Files.copy(configFile, backup);
                } catch (java.io.IOException error) {
                    MIGRATION_LOG.error("Config backup failed; preserving the existing source rate and schema for retry: {}", backup, error);
                    return;
                }
                double oldMultiplier = SOURCE_JAR_SYNERGY_MULTIPLIER.get();
                double migratedMultiplier = com.otectus.arsnspells.util.SourceSynergyPolicy.migratePerScanMultiplier(
                    oldMultiplier, SOURCE_JAR_SCAN_INTERVAL_TICKS.get());
                SOURCE_JAR_SYNERGY_MULTIPLIER.set(migratedMultiplier);
                migrated.add("  source_jar_synergy_multiplier: " + oldMultiplier + " per scan -> "
                    + migratedMultiplier + " per second (average income preserved; backup " + backup + ")");
            }
            if (PAYMENT_POLICY_AUTO.equalsIgnoreCase(rawPayment)) {
                PAYMENT_OPEN_FAILURE_POLICY.set(EFFECTIVE_PAYMENT_POLICY.name().toLowerCase(java.util.Locale.ROOT));
            }
            // Stamp the schema so the next load reads it from the file instead of probing the
            // filesystem again. Without this the same world would resolve differently on its
            // second start, when the file is no longer new.
            migrated.add("  config_schema_version: " + schemaRead + " -> " + CURRENT_SCHEMA_VERSION
                + " (migration completed; subsequent loads do not repeat it)");
            AnsConfig.CONFIG_SCHEMA_VERSION.set(CURRENT_SCHEMA_VERSION);
            safeSave();
        }

        if (migrated.isEmpty() || !MIGRATION_REPORTED.compareAndSet(false, true)) {
            return;
        }
        MIGRATION_LOG.info("Ars 'n' Spells config migration report (schema {} -> {}):",
            schemaRead, CURRENT_SCHEMA_VERSION);
        for (String line : migrated) {
            MIGRATION_LOG.info(line);
        }
    }

    /**
     * ANS-HIGH-017: async config save.
     *
     * <p>The previous implementation did up to {@code 100 + 200 + 400 = 700 ms} of
     * {@code Thread.sleep} retries plus three blocking {@code SPEC.save()} calls on the
     * caller's thread. Callers include {@code /ans} command handlers (server main thread)
     * and the in-game config screen (render thread). Under file-lock contention (antivirus,
     * OneDrive, simultaneous editor session) this froze the server tick for almost a
     * full second.
     *
     * <p>Now we dispatch the save to a single-thread daemon executor. The caller never
     * blocks; success is logged async and failures are logged with full stack at WARN
     * (not retried — NightConfig already serialises writes internally).
     */
    private static final java.util.concurrent.ExecutorService SAVE_EXEC =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AnsConfig-Save");
            t.setDaemon(true);
            return t;
        });

    public static void safeSave() {
        SAVE_EXEC.submit(() -> {
            try {
                SPEC.save();
                org.slf4j.LoggerFactory.getLogger(AnsConfig.class).info("OK Config saved successfully");
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(AnsConfig.class).warn(
                    "Config save failed (will not retry to avoid stalling the caller thread): {}",
                    e.getMessage(), e);
            }
        });
        // Audit D5: void, not boolean. A prior version returned true unconditionally
        // ("save scheduled"), which read as "save succeeded" at call sites. The async
        // WARN above is the only source of truth for whether the file write landed;
        // the in-memory config value is already live either way.
    }
}
