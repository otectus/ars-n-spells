package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.cooldown.CooldownCategory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Central spell analysis utility. Correctly identifies the first effect glyph
 * in an Ars Nouveau spell recipe by skipping cast methods and augments.
 *
 * <p>All systems that need to classify, categorize, or derive a school from an
 * Ars spell should use this utility instead of reading recipe.get(0) directly.
 *
 * <p>Ars Nouveau 5.x note: the spell recipe is no longer a public {@code recipe}
 * field. The effect list is read via {@link Spell#unsafeList()} (a
 * {@code List<AbstractSpellPart>} in registry order); {@link Spell#isEmpty()}
 * guards the empty case. The per-part {@code instanceof} classification and the
 * keyword heuristic on {@link AbstractSpellPart#getRegistryName()} are unchanged
 * from the Forge 1.20.1 implementation.
 */
public final class SpellAnalysis {

    /**
     * Immutable result of analyzing a spell recipe.
     */
    public static final class Result {
        private final @Nullable AbstractSpellPart firstEffect;
        private final @Nullable AbstractSpellPart castMethod;
        private final List<AbstractSpellPart> allEffects;
        private final String dominantSchool;
        private final CooldownCategory category;

        Result(@Nullable AbstractSpellPart firstEffect,
               @Nullable AbstractSpellPart castMethod,
               List<AbstractSpellPart> allEffects,
               String dominantSchool,
               CooldownCategory category) {
            this.firstEffect = firstEffect;
            this.castMethod = castMethod;
            this.allEffects = Collections.unmodifiableList(allEffects);
            this.dominantSchool = dominantSchool;
            this.category = category;
        }

        /** The first AbstractEffect glyph in the recipe, or null if none found. */
        @Nullable
        public AbstractSpellPart firstEffect() { return firstEffect; }

        /** The cast method (projectile, touch, self, etc.), or null. */
        @Nullable
        public AbstractSpellPart castMethod() { return castMethod; }

        /** All AbstractEffect parts found in the recipe. */
        public List<AbstractSpellPart> allEffects() { return allEffects; }

        /** The derived school: "fire", "ice", "holy", etc., or "generic" if unknown. */
        public String dominantSchool() { return dominantSchool; }

        /** The cooldown category for this spell. */
        public CooldownCategory category() { return category; }
    }

    private static final Result EMPTY = new Result(
            null, null, Collections.emptyList(), "generic", CooldownCategory.UTILITY);

    /**
     * Analyze an Ars Nouveau spell and return structured information about its
     * first effect glyph, school, and cooldown category.
     */
    public static Result analyze(@Nullable Spell spell) {
        if (spell == null || spell.isEmpty()) {
            return EMPTY;
        }
        return analyzeRecipe(spell.unsafeList());
    }

    /**
     * Analyze a raw spell recipe list.
     */
    public static Result analyze(@Nullable List<AbstractSpellPart> recipe) {
        if (recipe == null || recipe.isEmpty()) {
            return EMPTY;
        }
        return analyzeRecipe(recipe);
    }

    private static Result analyzeRecipe(List<AbstractSpellPart> recipe) {
        AbstractSpellPart castMethod = null;
        AbstractSpellPart firstEffect = null;
        List<AbstractSpellPart> allEffects = new ArrayList<>();

        for (AbstractSpellPart part : recipe) {
            if (part == null) continue;

            if (part instanceof AbstractCastMethod) {
                if (castMethod == null) {
                    castMethod = part;
                }
            } else if (part instanceof AbstractEffect) {
                allEffects.add(part);
                if (firstEffect == null) {
                    firstEffect = part;
                }
            }
            // AbstractAugment parts are intentionally skipped
        }

        String school = deriveSchool(firstEffect);
        CooldownCategory category = deriveCategory(firstEffect);

        return new Result(firstEffect, castMethod, allEffects, school, category);
    }

    /**
     * Derive the spell school from the first effect glyph using keyword analysis
     * of the registry path. Consolidates logic formerly duplicated in
     * SanctifiedLegacyCompat.determineSpellSchool and SpellScalingUtil.
     */
    public static String deriveSchool(@Nullable AbstractSpellPart effect) {
        if (effect == null || effect.getRegistryName() == null) {
            return "generic";
        }

        String path = effect.getRegistryName().getPath().toLowerCase(Locale.ROOT);

        if (path.contains("fire") || path.contains("ignite") || path.contains("flare")
                || path.contains("burn") || path.contains("plasma")) {
            return "fire";
        }
        if (path.contains("ice") || path.contains("freeze") || path.contains("frost")
                || path.contains("cold")) {
            return "ice";
        }
        if (path.contains("lightning") || path.contains("shock") || path.contains("storm")) {
            return "lightning";
        }
        if (path.contains("heal") || path.contains("holy")
                || (path.contains("light") && !path.contains("lightning"))) {
            return "holy";
        }
        if (path.contains("ender") || path.contains("blink") || path.contains("warp")
                || path.contains("teleport") || path.contains("rift")) {
            return "ender";
        }
        if (path.contains("blood") || path.contains("drain") || path.contains("vampire")) {
            return "blood";
        }
        if (path.contains("fang") || path.contains("evocation")) {
            return "evocation";
        }
        if (path.contains("grow") || path.contains("nature") || path.contains("plant")
                || path.contains("harvest")) {
            return "nature";
        }
        if (path.contains("wither") || path.contains("dark") || path.contains("hex")
                || path.contains("eldritch") || path.contains("void")) {
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

    /**
     * Derive cooldown category from the spell's first effect glyph using a
     * string-based heuristic on the glyph's registry path.
     *
     * <p>ANS-OPT-003: the previous design also consulted a hardcoded
     * {@code SpellCategoryMapper} lookup table as a primary source. The mapper
     * has been removed because (a) the heuristic covers the same ground via
     * path keywords and (b) the hardcoded table silently desynced from
     * upstream Iron's / Ars updates when new glyphs were added.
     */
    public static CooldownCategory deriveCategory(@Nullable AbstractSpellPart effect) {
        if (effect == null || effect.getRegistryName() == null) {
            return CooldownCategory.UTILITY;
        }

        // Heuristic based on glyph path
        String path = effect.getRegistryName().getPath().toLowerCase(Locale.ROOT);

        if (path.contains("damage") || path.contains("dmg") || path.contains("harm")
                || path.contains("crush") || path.contains("wither") || path.contains("ignite")
                || path.contains("burn") || path.contains("flare") || path.contains("explosion")
                || path.contains("pierce") || path.contains("knockback")) {
            return CooldownCategory.OFFENSIVE;
        }
        if (path.contains("shield") || path.contains("heal") || path.contains("barrier")
                || path.contains("protect") || path.contains("ward") || path.contains("regen")
                || path.contains("summon") || path.contains("absorb")) {
            return CooldownCategory.DEFENSIVE;
        }
        if (path.contains("leap") || path.contains("blink") || path.contains("speed")
                || path.contains("teleport") || path.contains("fly") || path.contains("flight")
                || path.contains("glide") || path.contains("phase") || path.contains("launch")) {
            return CooldownCategory.MOVEMENT;
        }

        return CooldownCategory.UTILITY;
    }

    private SpellAnalysis() {} // non-instantiable
}
