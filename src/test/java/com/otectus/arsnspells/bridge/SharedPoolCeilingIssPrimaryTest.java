package com.otectus.arsnspells.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.3.7 - the ISS_PRIMARY pool is {@code max(Ars max x rate, Iron's own max + Ars gear x rate)}.
 *
 * <p>The regression: NeoForge 3.3.5 replaced the Ars floor with the gear term alone. A player
 * with an Ars pool of 740 (100 base, 16 glyphs, book tier 3, a 250 helmet) and Iron's base 100
 * went from 740 to 100 + 250 = 350. Both terms are written as one modifier on Iron's max mana,
 * so these cases fix the arithmetic, including what the modifier must be on an attribute other
 * mods multiply.
 */
class SharedPoolCeilingIssPrimaryTest {

    @Test
    void theReportedPoolIsRestoredAtTheDefaultRate() {
        double amount = SharedPoolCeiling.issPrimaryModifierAmount(100.0, 250.0, 740.0, 1.0);
        assertEquals(640.0, amount, 1.0e-9, "the pre-3.3.5 NeoForge modifier");
        assertEquals(740.0, SharedPoolCeiling.issPrimaryCeiling(100.0, 250.0, 740.0, 1.0), 1.0e-9);
    }

    @Test
    void theGearTermStillWinsWhenIronsOwnPoolIsTheLargerOne() {
        // Iron's 500 + Ars gear 100 x 2 beats Ars 200 x 2: the 3.3.5 gear bonus, unchanged.
        assertEquals(200.0, SharedPoolCeiling.issPrimaryModifierAmount(500.0, 200.0, 400.0, 1.0), 1.0e-9);
        assertEquals(700.0, SharedPoolCeiling.issPrimaryCeiling(500.0, 200.0, 400.0, 1.0), 1.0e-9);
    }

    @Test
    void neverBelowEitherPublishedRule() {
        double[][] cases = {
            // ironsOwn, gear (x rate), floor (x rate), amplification
            {100, 250, 740, 1}, {100, 125, 370, 1}, {100, 500, 1480, 1}, {500, 100, 200, 1},
            {220, 51.5625, 292, 1.375}, {220, 25.78125, 146, 1.375}, {100, 0, 100, 1}, {0, 0, 0, 1}};
        for (double[] c : cases) {
            double ceiling = SharedPoolCeiling.issPrimaryCeiling(c[0], c[1], c[2], c[3]);
            double gearOnly = c[0] + c[3] * c[1];
            double floorOnly = Math.max(c[0], c[2]);
            assertTrue(ceiling >= gearOnly - 1.0e-9, "below the 3.3.5 gear-only pool: " + ceiling + " < " + gearOnly);
            assertTrue(ceiling >= floorOnly - 1.0e-9, "below the 3.3.4 NeoForge pool: " + ceiling + " < " + floorOnly);
            assertEquals(Math.max(gearOnly, c[2]), ceiling, 1.0e-9, "the larger of the two terms, never their sum");
        }
    }

    /**
     * An ADDITION modifier is multiplied by the attribute's MULTIPLY_BASE and MULTIPLY_TOTAL
     * modifiers, so the floor part is sized in pre-multiplier units while the gear part, like any
     * gear, is amplified with the rest of Iron's pool.
     */
    @Test
    void anAmplifiedAttributeLandsExactlyOnTheFloor() {
        // Iron's (100 + 60) x 1.25 x 1.1 = 220; Ars (int) 292.5 = 292.
        double amount = SharedPoolCeiling.issPrimaryModifierAmount(220.0, 37.5, 292.0, 1.375);
        assertEquals((292.0 - 220.0) / 1.375, amount, 1.0e-12);
        assertEquals(292.0, SharedPoolCeiling.issPrimaryCeiling(220.0, 37.5, 292.0, 1.375), 1.0e-9);
        // At rate 0.5 the amplified gear term is the larger: 220 + 1.375 x 18.75.
        assertEquals(18.75, SharedPoolCeiling.issPrimaryModifierAmount(220.0, 18.75, 146.0, 1.375), 1.0e-12);
        assertEquals(245.78125, SharedPoolCeiling.issPrimaryCeiling(220.0, 18.75, 146.0, 1.375), 1.0e-9);
    }

    @Test
    void recomputingFromTheSameInputsAsksForTheSameModifier() {
        // The caller measures Iron's own max without this modifier, so a recompute sees the same
        // inputs and must not drift.
        double first = SharedPoolCeiling.issPrimaryModifierAmount(220.0, 51.5625, 292.0, 1.375);
        for (int pass = 0; pass < 5; pass++) {
            assertEquals(first, SharedPoolCeiling.issPrimaryModifierAmount(220.0, 51.5625, 292.0, 1.375));
        }
    }

    @Test
    void anUnusableFloorLeavesTheGearBonusAlone() {
        for (double floor : new double[] {0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(250.0, SharedPoolCeiling.issPrimaryModifierAmount(100.0, 250.0, floor, 1.0), 1.0e-9,
                "floor " + floor);
        }
        assertEquals(0.0, SharedPoolCeiling.issPrimaryModifierAmount(100.0, Double.NaN, 0.0, 1.0), 1.0e-9);
    }

    @Test
    void anUnmeasurableAmplificationDegradesToOne() {
        for (double amplification : new double[] {0.0, -3.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(640.0, SharedPoolCeiling.issPrimaryModifierAmount(100.0, 250.0, 740.0, amplification), 1.0e-9,
                "amplification " + amplification);
        }
    }

    @Test
    void aNegativeGearBonusStillLowersIronsSideAboveTheFloor() {
        // Gear that removes 50 Ars mana: the pool is max(Ars floor 300, Iron's 500 - 50).
        assertEquals(-50.0, SharedPoolCeiling.issPrimaryModifierAmount(500.0, -50.0, 300.0, 1.0), 1.0e-9);
        assertEquals(450.0, SharedPoolCeiling.issPrimaryCeiling(500.0, -50.0, 300.0, 1.0), 1.0e-9);
        // ...and the floor still holds when it is the larger.
        assertEquals(600.0, SharedPoolCeiling.issPrimaryCeiling(500.0, -50.0, 600.0, 1.0), 1.0e-9);
    }
}
