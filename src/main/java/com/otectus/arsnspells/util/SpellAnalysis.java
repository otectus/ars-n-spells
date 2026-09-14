package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.AbstractFilter;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.cooldown.CooldownCategory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        private final Set<String> schools;
        private final CooldownCategory category;
        private final List<String> schoolKeys;
        private final String mappingDigest;
        private final List<String> allSchoolKeys;

        Result(@Nullable AbstractSpellPart firstEffect,
               @Nullable AbstractSpellPart castMethod,
               List<AbstractSpellPart> allEffects,
               String dominantSchool,
               Set<String> schools,
               CooldownCategory category) {
            this.firstEffect = firstEffect;
            this.castMethod = castMethod;
            this.allEffects = Collections.unmodifiableList(allEffects);
            this.dominantSchool = dominantSchool;
            this.schools = Collections.unmodifiableSet(schools);
            this.category = category;
            this.schoolKeys = SchoolResolver.resolveKeys(firstEffect);
            this.mappingDigest = SchoolMappings.get().digest();
            this.allSchoolKeys = allEffects.stream().flatMap(effect -> SchoolResolver.resolveKeys(effect).stream())
                .filter(key -> !SchoolKeys.GENERIC.equals(key)).distinct().toList();
        }

        /** The first AbstractEffect glyph in the recipe, or null if none found. */
        @Nullable
        public AbstractSpellPart firstEffect() { return firstEffect; }

        /** The cast method (projectile, touch, self, etc.), or null. */
        @Nullable
        public AbstractSpellPart castMethod() { return castMethod; }

        /** All AbstractEffect parts found in the recipe. */
        public List<AbstractSpellPart> allEffects() { return allEffects; }

        /**
         * The derived primary school: "fire", "ice", "holy", etc., or "generic" if unknown.
         *
         * <p>One school, deterministically chosen, and the value affinity and progression
         * credit. It is unaffected by {@link #schools()}: a multi-school spell still has exactly
         * one primary school and always has had.
         */
        public String dominantSchool() { return dominantSchool; }
        public List<String> schoolKeys() { return schoolKeys; }
        public String schoolKey() { return schoolKeys.get(0); }
        public String mappingDigest() { return mappingDigest; }
        public List<String> allSchoolKeys() { return allSchoolKeys; }

        /**
         * Every canonical school resolved across the recipe's effect glyphs, in the order they
         * were resolved, with "generic" excluded — so an empty set is the normal answer for a
         * spell with no school at all.
         *
         * <p>Exists because a single dominant school silently discarded the rest: addon glyphs
         * declare dual and compound elements, and a recipe can chain effects from different
         * schools. Damage scaling aggregates over this set under the configured
         * {@code multi_school_power_policy}; everything else still uses
         * {@link #dominantSchool()}.
         */
        public Set<String> schools() { return schools; }

        /** The cooldown category for this spell. */
        public CooldownCategory category() { return category; }
    }

    private static final Result EMPTY = new Result(
            null, null, Collections.emptyList(), "generic", Collections.emptySet(),
            CooldownCategory.UTILITY);

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
                // recipe that has no real effect at all.
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

        // A filter-only recipe still has to classify as something; fall back to the filter
        // rather than to GENERIC so such a spell keeps a cooldown category.
        AbstractSpellPart classifier = firstEffect != null ? firstEffect : firstFilter;

        String school = deriveSchool(classifier);
        Set<String> schools = deriveSchools(allEffects);
        CooldownCategory category = deriveCategory(classifier);

        return new Result(firstEffect, castMethod, allEffects, school, schools, category);
    }

    /**
     * Derive every canonical school the recipe's effect glyphs resolve to, in recipe order,
     * with "generic" excluded.
     *
     * <p>Union rather than "the classifier's schools" because both halves of the problem are
     * real: one glyph can declare several schools (dual and compound elements), and a recipe can
     * chain effects that belong to different ones. The primary school from
     * {@link #deriveSchool} is unchanged and still comes from the classifier alone.
     */
    public static Set<String> deriveSchools(@Nullable List<AbstractSpellPart> effects) {
        if (effects == null || effects.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> schools = new LinkedHashSet<>();
        for (AbstractSpellPart effect : effects) {
            for (SpellSchoolId school : SchoolResolver.resolveAll(effect)) {
                schools.add(school.id());
            }
        }
        return schools;
    }

    /**
     * Derive the spell school from the first effect glyph using keyword analysis
     * of the registry path. Consolidates logic formerly duplicated in
     * SanctifiedLegacyCompat.determineSpellSchool and SpellScalingUtil.
     */
    public static String deriveSchool(@Nullable AbstractSpellPart effect) {
        // 3.1.0: resolution moved to SchoolResolver, which consults, in order, the datapack
        // glyph override, Ars Nouveau's own AbstractSpellPart.spellSchools metadata, and only
        // then the registry-path keyword heuristic that used to be the whole implementation.
        // The heuristic alone classified Firework as fire and could not see an addon glyph's
        // declared school at all.
        return SchoolResolver.resolve(effect).id();
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
