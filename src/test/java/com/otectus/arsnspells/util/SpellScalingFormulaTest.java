package com.otectus.arsnspells.util;

import com.otectus.arsnspells.config.MultiSchoolPowerPolicy;
import com.otectus.arsnspells.util.SpellScalingUtil.SchoolAggregate;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the arithmetic of {@link SpellScalingUtil#combine}.
 *
 * <p>{@code combine} is the whole scaling formula with its inputs already resolved, so it can
 * be exercised without a {@code Player}, an attribute map, a loaded config or a Minecraft
 * bootstrap. The rule under test - additive school stacking rather than multiplicative - is the
 * one that is easiest to "fix" into exponential stacking by accident.
 */
class SpellScalingFormulaTest {

    /** Floats: the formula is three multiplies deep, so exact equality is not safe. */
    private static final float DELTA = 1.0e-5f;

    @Test
    void neutralInputsLeaveDamageUnchanged() {
        assertEquals(1.0f, SpellScalingUtil.combine(1.0f, 1.0f, 1.0f, 1.0f, 3.0f), DELTA);
    }

    @Test
    void genericSpellPowerAppliesOnItsOwn() {
        assertEquals(1.20f, SpellScalingUtil.combine(1.20f, 1.0f, 1.0f, 1.0f, 3.0f), DELTA);
    }

    @Test
    void matchingSchoolAddsItsBonusRatherThanMultiplyingIt() {
        // 1.20 + (1.40 - 1.0) = 1.60, not 1.20 * 1.40 = 1.68.
        assertEquals(1.60f, SpellScalingUtil.combine(1.20f, 1.40f, 1.0f, 1.0f, 3.0f), DELTA);
    }

    @Test
    void nonMatchingSchoolContributesNothing() {
        // The caller passes schoolPower = 1.0 when no school matched, so the additive term
        // is zero and only the generic power survives.
        float result = SpellScalingUtil.combine(1.20f, 1.0f, 1.0f, 1.0f, 3.0f);
        assertEquals(1.20f, result, DELTA);
        assertNotEquals(1.60f, result, DELTA);
    }

    @Test
    void capClampsTheCombinedMultiplier() {
        // 2.0 + (3.0 - 1.0) = 4.0, clamped down to the 3.0 cap.
        assertEquals(3.0f, SpellScalingUtil.combine(2.0f, 3.0f, 1.0f, 1.0f, 3.0f), DELTA);
    }

    // --- multi-school aggregation -----------------------------------------------------------
    //
    // aggregateSchoolPower is the other half of the formula: it decides *which* elemental value
    // reaches combine() when a spell resolves to several schools. Like combine() it is pure, so
    // the balance rule it enforces - that matching bonuses are never summed - is pinned here
    // without a Player or a config.

    /** Insertion-ordered, exactly as SpellScalingUtil collects it from the resolved schools. */
    private static Map<String, Float> powers(Object... schoolThenValue) {
        Map<String, Float> values = new LinkedHashMap<>();
        for (int i = 0; i < schoolThenValue.length; i += 2) {
            values.put((String) schoolThenValue[i], (Float) schoolThenValue[i + 1]);
        }
        return values;
    }

    @Test
    void maxTakesTheSingleBestSchoolRatherThanTheSum() {
        SchoolAggregate result = SpellScalingUtil.aggregateSchoolPower(
            MultiSchoolPowerPolicy.MAX, powers("fire", 1.40f, "lightning", 1.20f), "fire");
        assertEquals(1.40f, result.schoolPower(), DELTA,
            "MAX takes the strongest matching bonus; summing would give 2.60 and make a spell "
                + "stronger purely for carrying a second school label");
        assertEquals("fire", result.matchedSchool());
        // The elemental contribution is combine()'s additive term: 0.40, not 0.60.
        assertEquals(0.40f, result.schoolPower() - 1.0f, DELTA);
    }

    @Test
    void maxOverFourElementsIsStillOneElement() {
        SchoolAggregate result = SpellScalingUtil.aggregateSchoolPower(
            MultiSchoolPowerPolicy.MAX,
            powers("fire", 1.10f, "ice", 1.50f, "lightning", 1.20f, "nature", 1.30f), "fire");
        assertEquals(1.50f, result.schoolPower(), DELTA,
            "an all-element spell must not stack four bonuses - that is the exact failure mode "
                + "the policy exists to prevent");
        assertEquals("ice", result.matchedSchool());
    }

    @Test
    void averageIsTheMeanOfTheMatchedSchools() {
        SchoolAggregate result = SpellScalingUtil.aggregateSchoolPower(
            MultiSchoolPowerPolicy.AVERAGE, powers("fire", 1.40f, "lightning", 1.20f), "fire");
        assertEquals(1.30f, result.schoolPower(), DELTA);
    }

    @Test
    void primaryUsesTheDominantSchoolEvenWhenAnotherIsStronger() {
        SchoolAggregate result = SpellScalingUtil.aggregateSchoolPower(
            MultiSchoolPowerPolicy.PRIMARY, powers("fire", 1.10f, "lightning", 1.90f), "fire");
        assertEquals(1.10f, result.schoolPower(), DELTA,
            "PRIMARY is the pre-multi-school behaviour: only the school affinity and progression "
                + "credit may scale the spell");
        assertEquals("fire", result.matchedSchool());
    }

    @Test
    void primaryContributesNothingWhenTheDominantSchoolHasNoIronsAttribute() {
        SchoolAggregate result = SpellScalingUtil.aggregateSchoolPower(
            MultiSchoolPowerPolicy.PRIMARY, powers("lightning", 1.90f), "generic");
        assertEquals(1.0f, result.schoolPower(), DELTA);
        assertNull(result.matchedSchool());
    }

    @Test
    void noMatchingSchoolIsNeutralUnderEveryPolicy() {
        for (MultiSchoolPowerPolicy policy : MultiSchoolPowerPolicy.values()) {
            SchoolAggregate result =
                SpellScalingUtil.aggregateSchoolPower(policy, powers(), "generic");
            assertEquals(1.0f, result.schoolPower(), DELTA,
                policy + " must pass the neutral 1.0 through, so combine()'s additive term is 0");
            assertNull(result.matchedSchool(), policy + " matched nothing and must report so");
        }
    }

    @Test
    void unknownPolicyNamesFallBackToMaxRatherThanAnythingAdditive() {
        assertEquals(MultiSchoolPowerPolicy.MAX, MultiSchoolPowerPolicy.fromString("sum"),
            "'sum' is deliberately not a policy; it must not resolve to anything that adds");
        assertEquals(MultiSchoolPowerPolicy.MAX, MultiSchoolPowerPolicy.fromString(null));
        assertEquals(MultiSchoolPowerPolicy.AVERAGE, MultiSchoolPowerPolicy.fromString("AvErAgE"));
    }
}
