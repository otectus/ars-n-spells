package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.AbstractFilter;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.cooldown.CooldownCategory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central spell analysis utility. Correctly identifies the first effect glyph
 * in an Ars Nouveau spell recipe by skipping cast methods and augments.
 *
 * All systems that need to classify, categorize, or derive a school from an
 * Ars spell should use this utility instead of reading recipe.get(0) directly.
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

        /** The derived school id: "fire", "ice", "holy", etc., or "generic" if unknown. */
        public String dominantSchool() { return dominantSchool; }

        /** Enum-typed companion to {@link #dominantSchool()}; prefer this at new call sites. */
        public SpellSchoolId school() { return SpellSchoolId.fromId(dominantSchool); }

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
        if (spell == null || spell.recipe == null || spell.recipe.isEmpty()) {
            return EMPTY;
        }
        return analyzeRecipe(spell.recipe);
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
        AbstractSpellPart firstFilter = null;
        List<AbstractSpellPart> allEffects = new ArrayList<>();

        for (AbstractSpellPart part : recipe) {
            if (part == null) continue;

            if (part instanceof AbstractCastMethod) {
                if (castMethod == null) {
                    castMethod = part;
                }
            } else if (part instanceof AbstractFilter) {
                // AbstractFilter EXTENDS AbstractEffect in Ars Nouveau, so the plain
                // `instanceof AbstractEffect` test below used to claim filter glyphs as the
                // spell's first effect. A recipe like Projectile -> Sensitive -> Ignite was
                // then classified by Sensitive, giving the wrong school, the wrong cooldown
                // category, and the wrong elemental scaling. Filters select targets; they are
                // not what the spell *does*, so they are remembered only as a fallback for a
                // recipe that contains nothing else.
                if (firstFilter == null) {
                    firstFilter = part;
                }
            } else if (part instanceof AbstractEffect) {
                allEffects.add(part);
                if (firstEffect == null) {
                    firstEffect = part;
                }
            }
            // AbstractAugment parts are intentionally skipped
        }

        // A filter-only recipe still needs a representative part rather than nothing.
        AbstractSpellPart representative = firstEffect != null ? firstEffect : firstFilter;

        String school = deriveSchool(representative);
        CooldownCategory category = deriveCategory(representative);

        return new Result(representative, castMethod, allEffects, school, category);
    }

    /**
     * Derive the spell school from a glyph.
     *
     * <p>Delegates entirely to {@link SchoolResolver}, which is the single authority every
     * subsystem consults. This method survives only as the legacy string-shaped façade.
     *
     * <p>What used to live here — a hardcoded {@code ars_nouveau:}-only table plus a substring
     * fallback — had two structural problems. It ignored
     * {@code AbstractSpellPart.spellSchools}, the real metadata that vanilla Ars populates in
     * its constructor and Ars Elemental populates explicitly, so addon glyphs could only ever
     * be supported by adding more Java. And it could return {@code aqua}, {@code geo} or
     * {@code wind} — values with no {@code AffinityType} and no Iron's attribute — so those
     * spells silently received neither affinity nor elemental scaling.
     */
    public static String deriveSchool(@Nullable AbstractSpellPart effect) {
        return SchoolResolver.resolve(effect).id();
    }

    /** Enum-typed companion to {@link #deriveSchool}; prefer this at new call sites. */
    public static SpellSchoolId resolveSchool(@Nullable AbstractSpellPart effect) {
        return SchoolResolver.resolve(effect);
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
