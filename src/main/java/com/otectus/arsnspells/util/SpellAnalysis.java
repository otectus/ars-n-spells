package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.cooldown.CooldownCategory;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * STUB — the 1.20.1 implementation walked AN's {@code Spell.recipe} list and
 * inspected {@code AbstractSpellPart.getRegistryName()} to derive a school
 * and cooldown category. AN 5.x reshaped the spell representation (recipe
 * is no longer a public field; spell parts use {@code Holder<AbstractSpellPart>}
 * registries; the registry name lookup goes through the new BuiltInRegistries
 * shape). Restoring full classification is Phase 11 work.
 *
 * Until then: every spell maps to "generic" school + UTILITY cooldown
 * category. Affinity, progression, and cooldown subsystems still operate
 * but never see school-specific traffic, so their per-school bonuses stay
 * at zero.
 */
public final class SpellAnalysis {

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

        @Nullable public AbstractSpellPart firstEffect() { return firstEffect; }
        @Nullable public AbstractSpellPart castMethod() { return castMethod; }
        public List<AbstractSpellPart> allEffects() { return allEffects; }
        public String dominantSchool() { return dominantSchool; }
        public CooldownCategory category() { return category; }
    }

    private static final Result EMPTY = new Result(
        null, null, Collections.emptyList(), "generic", CooldownCategory.UTILITY);

    public static Result analyze(@Nullable Spell spell) {
        // TODO(Phase 11): walk AN 5.x spell representation, derive school + category
        return EMPTY;
    }

    public static Result analyze(@Nullable List<AbstractSpellPart> recipe) {
        // TODO(Phase 11): same
        return EMPTY;
    }

    public static String deriveSchool(@Nullable AbstractSpellPart effect) {
        // TODO(Phase 11): keyword analysis on AN 5.x registry path
        return "generic";
    }

    public static CooldownCategory deriveCategory(@Nullable AbstractSpellPart effect) {
        return CooldownCategory.UTILITY;
    }

    private SpellAnalysis() {}
}
