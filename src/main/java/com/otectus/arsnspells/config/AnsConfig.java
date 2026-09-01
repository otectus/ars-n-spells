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
    public static final ModConfigSpec.ConfigValue<String> HYBRID_MANA_BAR;
    public static final ModConfigSpec.DoubleValue DUAL_COST_ARS_PERCENTAGE;
    public static final ModConfigSpec.DoubleValue DUAL_COST_ISS_PERCENTAGE;
    public static final ModConfigSpec.DoubleValue DEFAULT_MAX_MANA;
    public static final ModConfigSpec.BooleanValue respectArmorBonuses;
    public static final ModConfigSpec.BooleanValue respectEnchantments;
    public static final ModConfigSpec.ConfigValue<String> CROSS_SYSTEM_REGEN_CONVERSION;
    public static final ModConfigSpec.DoubleValue CROSS_SYSTEM_REGEN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CROSS_SYSTEM_REGEN_REFERENCE_POOL;

    // ========================================
    // RESONANCE SYSTEM
    // ========================================
    public static final ModConfigSpec.BooleanValue ENABLE_ARS_RESONANCE;
    public static final ModConfigSpec.BooleanValue ENABLE_IRONS_RESONANCE;
    public static final ModConfigSpec.DoubleValue RESONANCE_STRENGTH;
    // These two describe a threshold-gated, lingering resonance and were read by nothing in
    // either line - the value has always scaled linearly from 0% mana with no gate at all.
    // The gate and the curve are orthogonal, though, so rather than removing the keys or
    // rewriting the curve, the gate is now layered on top: the existing proportional bonus
    // applies only while the pool is at or above resonance_threshold, or within
    // resonance_duration ticks of last having been. The default threshold of 0 leaves the
    // gate permanently open, which reproduces the historical behaviour exactly.
    public static final ModConfigSpec.DoubleValue RESONANCE_THRESHOLD;
    public static final ModConfigSpec.IntValue RESONANCE_DURATION;
    public static final ModConfigSpec.DoubleValue MAX_DAMAGE_MULTIPLIER;

    // ========================================
    // COOLDOWN SYSTEM
    // ========================================
    public static final ModConfigSpec.BooleanValue ENABLE_UNIFIED_COOLDOWNS;
    public static final ModConfigSpec.BooleanValue ENABLE_CROSS_MOD_COOLDOWNS;
    public static final ModConfigSpec.IntValue COOLDOWN_CATEGORY_DURATION;
    public static final ModConfigSpec.DoubleValue CROSS_MOD_COOLDOWN_MULTIPLIER;

    // ========================================
    // PROGRESSION SYSTEM
    // ========================================
    public static final ModConfigSpec.BooleanValue ENABLE_CROSS_MOD_PROGRESSION;
    public static final ModConfigSpec.DoubleValue PROGRESSION_BONUS_PER_CAST;
    public static final ModConfigSpec.DoubleValue PROGRESSION_BONUS_CAP;

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
    public static final ModConfigSpec.DoubleValue MAX_TOTAL_CURIO_DISCOUNT;
    // read_curio_attribute_modifiers was removed in the 1.21.1 audit. It was added during the
    // port for config-surface parity and read by nothing. The behaviour it described is not
    // missing - it is unconditional and cannot be separated out: Curios applies its modifiers
    // to the PLAYER's attributes, and EquipmentIntegration mirrors the aggregate
    // PerkAttributes.MAX_MANA / MANA_REGEN_BONUS, so curio-sourced mana gear (Apotheosis
    // affixes, Magical Jewelry, ...) already feeds the bridge with no per-item scan and no
    // toggle to honour.
    public static final ModConfigSpec.ConfigValue<String> SCROLL_COST_MODE;


    // ========================================
    // SPELL SCALING
    // ========================================
    public static final ModConfigSpec.DoubleValue SPELL_POWER_CAP;

    // ========================================
    // SOURCE JAR SYNERGY
    // ========================================
    public static final ModConfigSpec.BooleanValue ENABLE_SOURCE_JAR_SYNERGY;
    public static final ModConfigSpec.IntValue SOURCE_JAR_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue SOURCE_JAR_SCAN_RADIUS;
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
    public static final ModConfigSpec.BooleanValue ALLOW_ARS_SPELLS_IN_IRONS_SPELLBOOKS;
    public static final ModConfigSpec.IntValue MAX_ARS_CROSS_SPELLS_PER_IRONS_SPELLBOOK;

    // ========================================
    // PERFORMANCE TUNING
    // ========================================
    public static final ModConfigSpec.DoubleValue SOURCE_JAR_CACHE_MOVE_THRESHOLD;

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
        
        HYBRID_MANA_BAR = BUILDER
            .comment(
                "Which mana bar to display in HYBRID mode:",
                "  irons - Show Iron's Spellbooks mana bar",
                "  ars - Show Ars Nouveau mana bar",
                "Only applies when mana_unification_mode is set to 'hybrid'"
            )
            // Validated, not free-form: the HUD reduces this to a two-way choice, so any other
            // string silently resolved to one of the two bars with nothing in the log to say
            // the key was wrong - and which one it resolved to differed from the 1.20.1 line.
            // A validator makes NeoForge reject the bad value at load and fall back to the
            // default, which is the documented behaviour.
            .define("hybrid_mana_bar", "irons",
                o -> o instanceof String s
                    && ("irons".equalsIgnoreCase(s) || "ars".equalsIgnoreCase(s)));

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

        // ANS-MED-044 / audit F4 tombstones — sections removed because no code
        // ever read them (setting them had zero effect):
        //   "Ars Glyph Bonuses" (amplify/extend/split/pierce/sensitive bonuses),
        //   "Iron's School Bonuses" (enable_school_bonuses + per-school keys),
        //   resonance caps (max_duration_multiplier, max_projectile_split,
        //     max_chain_chance, max_area_multiplier),
        //   category cooldowns (enable_category_cooldowns, cooldown_reduction_cap),
        //   allow_discount_stacking, "Performance" keys (mana_sync_interval,
        //     enable_caching, cache_duration), hybrid_sync_rate, allow_mana_overflow.
        // Also removed on the NeoForge 1.21.1 line only — the backing subsystems
        // (Blood Magic LP, Covenant of the Seven aura/rings) have no 1.21.1 build:
        //   "Cursed Ring LP System" (lp_source_mode + all *_lp_* keys),
        //   "Aura System", "Blasphemy Ring Discounts", hide_mana_bar_with_ring,
        //   scroll_cost_mode (ANS-MED-043: reader mixin was never ported; re-add
        //     key + MixinScrollItem together if the feature returns).
        // Re-add any of these alongside an implementation if/when shipped.

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
            .comment("Mana fraction at or above which resonance applies (0.95 = 95% full).",
                     "The bonus itself still scales with how full the pool is; this only",
                     "gates whether it applies at all.",
                     "0.0 (the default) leaves the gate open at any mana level, which is how",
                     "resonance has always behaved. Raise it to make resonance a burst window",
                     "you top the pool off for, rather than a passive trickle.")
            .defineInRange("resonance_threshold", 0.0, 0.0, 1.0);

        RESONANCE_DURATION = BUILDER
            .comment("How long resonance keeps applying after the pool drops below",
                     "resonance_threshold, in ticks (20 = 1 second).",
                     "This is what makes a raised threshold playable: spending mana to cast",
                     "necessarily drops you below it, so without a linger the bonus would",
                     "switch off on the very cast that earned it.",
                     "Has no effect while resonance_threshold is 0.",
                     "Resolution is the recompute interval (40 ticks), so the effective",
                     "linger is this value give or take one interval.")
            .defineInRange("resonance_duration", 100, 0, 1200);

        MAX_DAMAGE_MULTIPLIER = BUILDER
            .comment("Maximum damage multiplier from resonance")
            .defineInRange("max_damage_multiplier", 5.0, 1.0, 100.0);
        
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
        
        ENABLE_CROSS_MOD_COOLDOWNS = BUILDER
            .comment("CRITICAL: Enable cross-mod cooldown interference (false = each mod has independent cooldowns)")
            .define("enable_cross_mod_cooldowns", false);
        
        COOLDOWN_CATEGORY_DURATION = BUILDER
            .comment("Base category cooldown duration (ticks, 20 = 1 second)")
            .defineInRange("cooldown_category_duration", 100, 0, 10000);
        
        CROSS_MOD_COOLDOWN_MULTIPLIER = BUILDER
            .comment("Multiplier for cross-mod cooldowns (0.5 = 50% of normal)")
            .defineInRange("cross_mod_cooldown_multiplier", 0.5, 0.0, 10.0);
        
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

        PROGRESSION_BONUS_PER_CAST = BUILDER
            .comment("Attribute bonus gained per cast in a school (audit F4 - was hardcoded 0.001).",
                     "0.001 = +0.1% per cast. The bonus is transient (derived from the persistent",
                     "cast count), so changing this immediately rescales every player's bonus.")
            .defineInRange("progression_bonus_per_cast", 0.001, 0.0, 0.1);

        PROGRESSION_BONUS_CAP = BUILDER
            .comment("Cap on the per-school progression attribute bonus (audit F4 - was hardcoded 0.25).",
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
            .comment("Enable mana cost discounts from curios tagged #ars_n_spells:curio_spell_discount.")
            .define("enable_curio_discounts", true);
        
        // The key name is a leftover from when this was Covenant of the Seven's Ring of Virtue.
        // It is now the generic per-curio discount and is renamed only in the comment: renaming
        // the key itself would silently reset the setting on every server that has tuned it.
        VIRTUE_RING_DISCOUNT = BUILDER
            .comment("Per-curio mana cost discount for items in #ars_n_spells:curio_spell_discount",
                     "(0.20 = each tagged curio multiplies cost by 0.80). Applies to both Ars and",
                     "Iron's Spellbooks casts.")
            .defineInRange("virtue_ring_discount", 0.20, 0.0, 1.0);

        MAX_TOTAL_CURIO_DISCOUNT = BUILDER
            .comment("Hard cap on the COMBINED tagged-curio discount across all worn curios",
                     "(0.50 = spells never cost less than 50% after curio discounts). Prevents",
                     "stacked discount curios from trivialising mana cost.")
            .defineInRange("max_total_curio_discount", 0.50, 0.0, 1.0);

        BUILDER.pop();

        // ========================================
        // SCROLL COST SYSTEM
        // ========================================
        BUILDER.push("Scroll Cost System");
        BUILDER.comment("Controls what an Iron's Spellbooks scroll costs to use.");

        SCROLL_COST_MODE = BUILDER
            .comment(
                "Cost mode for Iron's Spellbooks scroll usage:",
                "  full - Scrolls cost the same as casting the spell normally",
                "  lp_only - Scrolls are free of mana cost but still consume LP if Cursed Ring equipped",
                "  free - Scrolls have no resource cost (LP from Cursed Ring still applies)",
                "Note: wearing the Cursed Ring (with enable_lp_system on) makes LP REPLACE mana,",
                "exactly as it does for normal casting - a ring wearer is never charged both.",
                "The LP half of this setting is inert on 1.21.1: Covenant of the Seven has no",
                "1.21.1 release, so lp_only behaves as free until that integration returns."
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
                "native-wheel proxy pool size ("
                    + com.otectus.arsnspells.spell.CrossModSpellComponents.PROXY_POOL_SIZE + "): only that many",
                "Ars entries can be shown as distinct entries in Iron's spell wheel per book."
            )
            .defineInRange("max_ars_cross_spells_per_irons_spellbook", -1, -1, 64);

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
            // "lp" removed: the LP system (Blood Magic / Cursed Ring) has no
            // NeoForge 1.21.1 build; enable_lp_system was deleted with it.
            default: return false;
        }
    }
    
    /**
     * The dual-cost split as fractions that sum to exactly 1.
     *
     * <p>{@code dual_cost_ars_percentage} and {@code dual_cost_iss_percentage} are
     * independently range-checked [0,1], so a pair summing to 1.2 is a perfectly valid
     * config and the init-time sum check only WARNs about it. Every consumer previously
     * multiplied the raw percentages straight into the cost, so such a config silently
     * overcharged by 20% on every cast (and an under-1.0 pair undercharged). Normalising in
     * one place keeps the two spend paths - {@code BridgeManager.consumeManaForMode} and the
     * cross-cast cost-calc handler - from disagreeing about what a cast costs.
     *
     * @return {@code {arsShare, issShare}}, summing to 1.0
     */
    public static double[] dualCostSplit() {
        double arsPct;
        double issPct;
        try {
            arsPct = DUAL_COST_ARS_PERCENTAGE.get();
            issPct = DUAL_COST_ISS_PERCENTAGE.get();
        } catch (IllegalStateException configNotLoaded) {
            return new double[] {0.5, 0.5};
        }
        double total = arsPct + issPct;
        if (!(total > 0.0)) {
            // Degenerate config (both zero, or NaN): charge the whole cost Ars-side rather
            // than making every spell free.
            return new double[] {1.0, 0.0};
        }
        return new double[] {arsPct / total, issPct / total};
    }

    /**
     * Read a boolean key without ever throwing.
     *
     * <p>{@code ModConfigSpec.ConfigValue#get()} throws {@link IllegalStateException} when the
     * owning config has not been loaded. That is reachable in normal play, not just in
     * pathological cases: the SERVER spec is not loaded on a client sitting at the main menu
     * or during a world transition, and several of this mod's reads happen on the client
     * render thread (the spell wheel and the inscription table both call into spell-power
     * calculation). A throw there is a hard crash of the render loop.
     *
     * <p>The stale {@code value != null} idiom this replaces does not help: the field is
     * assigned at class-init, so it is non-null long before the config is loaded, and the
     * check passes right up to the {@code get()} that throws.
     */
    public static boolean flag(ModConfigSpec.BooleanValue value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return value.get();
        } catch (IllegalStateException configNotLoaded) {
            return fallback;
        }
    }

    /** {@link #flag} for the debug toggle, whose fallback is always "off". */
    public static boolean debugEnabled() {
        return flag(DEBUG_MODE, false);
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
     *
     * <p>Returns void by design (audit D5): it only SCHEDULES the write, so any boolean it
     * could return would describe the queueing, not the save, and would read at call sites as
     * "the config was saved". The log is the source of truth for completion.
     */
    public static void safeSave() {
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
    }
}