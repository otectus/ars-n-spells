package com.otectus.arsnspells.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Audit V13 (minimal) - the ceiling delta must be derived, not subtracted out of a multiplied
 * total.
 *
 * <p>The regression: {@code modifierAmount} computed {@code arsMax - ironsOwnMax} and the result
 * was added as an {@code ADDITION} modifier, i.e. <em>before</em> any third-party
 * {@code MULTIPLY_TOTAL} on the same attribute. With a x2 multiplier in play, a native ceiling
 * of 200 and a desired 300 produced a delta of 100, which the multiplier turned into a final
 * value of 400. The next recompute measured 200 again, asked for 100 again, and the pool sat
 * permanently a third above where either system wanted it.
 *
 * <p>The fix is to ask what one additive point is actually worth on this attribute - the
 * amplification - and divide by it. The scenario below is the audit's: native max 200, desired
 * 300, one third-party x2 multiplier, recomputed five times.
 */
class SharedPoolCeilingAmplificationTest {

    private static final double NATIVE_MAX = 200.0d;
    private static final double DESIRED = 300.0d;
    private static final double THIRD_PARTY_DOUBLING = 2.0d;

    @Test
    void aDoubledAttributeSettlesOnTheDesiredCeilingAndStaysThere() {
        for (int recompute = 0; recompute < 5; recompute++) {
            // Each recompute starts from the isolated native snapshot: ANS's own modifier has
            // been removed, so what is read is Iron's own doubled max, unchanged every time.
            double delta = SharedPoolCeiling.modifierAmount(NATIVE_MAX, DESIRED, THIRD_PARTY_DOUBLING);
            assertEquals(50.0d, delta, 1.0e-9d,
                "recompute " + recompute + " asked for the raw shortfall instead of the pre-multiplier delta");
            assertEquals(DESIRED,
                SharedPoolCeiling.resultingCeiling(NATIVE_MAX, DESIRED, THIRD_PARTY_DOUBLING), 1.0e-9d,
                "recompute " + recompute + " overshot the desired ceiling");
        }
    }

    @Test
    void anUnamplifiedAttributeBehavesExactlyAsBefore() {
        assertEquals(100.0d, SharedPoolCeiling.modifierAmount(NATIVE_MAX, DESIRED, 1.0d), 1.0e-9d);
        assertEquals(100.0d, SharedPoolCeiling.modifierAmount(NATIVE_MAX, DESIRED), 1.0e-9d);
    }

    @Test
    void aNativeCeilingThatAlreadyFitsAsksForNothing() {
        assertEquals(0.0d, SharedPoolCeiling.modifierAmount(400.0d, DESIRED, THIRD_PARTY_DOUBLING));
        assertEquals(400.0d, SharedPoolCeiling.resultingCeiling(400.0d, DESIRED, THIRD_PARTY_DOUBLING));
    }

    /**
     * An amplification that could not be measured must not become a division by zero or a
     * negative delta. Degrading to 1.0 reproduces the pre-fix arithmetic, which is wrong but
     * bounded, rather than producing an infinite ceiling.
     */
    @Test
    void anUnmeasurableAmplificationDegradesToOne() {
        assertEquals(100.0d, SharedPoolCeiling.modifierAmount(NATIVE_MAX, DESIRED, 0.0d), 1.0e-9d);
        assertEquals(100.0d, SharedPoolCeiling.modifierAmount(NATIVE_MAX, DESIRED, -3.0d), 1.0e-9d);
        assertEquals(100.0d, SharedPoolCeiling.modifierAmount(NATIVE_MAX, DESIRED, Double.NaN), 1.0e-9d);
    }
}
