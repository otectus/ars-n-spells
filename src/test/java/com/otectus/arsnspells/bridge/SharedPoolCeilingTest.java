package com.otectus.arsnspells.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared pool must have exactly one ceiling, and it must sit at or above whatever
 * maximum the Ars side reports.
 *
 * <p>Background, verified against the shipped Iron's Spellbooks bytecode (3.15.0 and
 * 3.16.3 alike): {@code MagicData.setMana(float)} ends in
 * {@code if (mana > player.getAttributeValue(max_mana)) mana = that;} and
 * {@code addMana(v)} is {@code setMana(mana + v)}. Every write is clamped down. In HYBRID,
 * ANS used to push only the Ars <em>gear</em> bonus into that attribute while telling the
 * Ars side its full max (base + glyph bonus + book tier), so a player could hold more mana
 * than the ceiling allowed and the next cast — of any cost — collapsed the pool to the
 * ceiling. These tests pin the arithmetic that stops that happening.
 */
class SharedPoolCeilingTest {

    private static final double EPS = 1.0e-9;

    @Test
    void modifierAmount_liftsIronsOwnMaxUpToArsMax() {
        // Iron's default pool is small; an Ars spell book plus glyph bonuses is not.
        assertEquals(1900.0, SharedPoolCeiling.modifierAmount(100.0, 2000.0), EPS,
            "the modifier must make up the whole shortfall between Iron's max and Ars's max");
    }

    @Test
    void modifierAmount_accountsForIronsOwnGear_ratherThanItsBareBase() {
        // Iron's base 100 plus 400 from its own mage gear. Only the remaining 1500 is
        // needed; measuring against the bare base would add 1900 on top of the gear and
        // inflate the shared pool to 2400.
        assertEquals(1500.0, SharedPoolCeiling.modifierAmount(500.0, 2000.0), EPS);
        assertEquals(2000.0, SharedPoolCeiling.resultingCeiling(500.0, 2000.0), EPS,
            "the ceiling must land on max(Ars, Iron's), not overshoot both");
    }

    @Test
    void modifierAmount_isZeroWhenIronsAlreadyCoversArs() {
        assertEquals(0.0, SharedPoolCeiling.modifierAmount(3000.0, 2000.0), EPS,
            "never subtract: Iron's own larger pool must survive untouched");
    }

    @Test
    void modifierAmount_isNeverNegative() {
        assertTrue(SharedPoolCeiling.modifierAmount(500.0, 1.0) >= 0.0);
        assertTrue(SharedPoolCeiling.modifierAmount(0.0, -50.0) >= 0.0);
    }

    @Test
    void modifierAmount_ignoresUnreadableArsMax() {
        // arsRealMaxMana returns 0 when Ars's calculator cannot be read; that must not be
        // mistaken for "Ars wants a pool of zero" and drive the ceiling down.
        assertEquals(0.0, SharedPoolCeiling.modifierAmount(100.0, 0.0), EPS);
        assertEquals(0.0, SharedPoolCeiling.modifierAmount(100.0, Double.NaN), EPS);
    }

    @Test
    void resultingCeiling_isExactlyTheLargerOfTheTwoMaxima() {
        // Ars is the larger pool: the ceiling rises to cover it.
        assertEquals(2000.0, SharedPoolCeiling.resultingCeiling(350.0, 2000.0), EPS);

        // Iron's is the larger pool: nothing is added, and nothing is taken away.
        assertEquals(2600.0, SharedPoolCeiling.resultingCeiling(2600.0, 300.0), EPS,
            "an Ars max below Iron's own must leave Iron's ceiling exactly as it was");

        // And in general, for either ordering.
        for (double own : new double[] {0.0, 100.0, 500.0, 2600.0}) {
            for (double ars : new double[] {0.0, 300.0, 2000.0, 4000.0}) {
                double ceiling = SharedPoolCeiling.resultingCeiling(own, ars);
                assertTrue(ceiling >= own, "ceiling " + ceiling + " dropped below Iron's " + own);
                assertTrue(ceiling >= ars || ars <= 0.0,
                    "ceiling " + ceiling + " does not cover Ars's " + ars);
            }
        }
    }

    @Test
    void wouldDestroyMana_flagsTheReportedCase() {
        // The report: a 2000 pool, a ~300 spell, and a ceiling still at Iron's bare base.
        assertTrue(SharedPoolCeiling.wouldDestroyMana(2000.0, 100.0),
            "a pool above the ceiling is the precondition for the drain");
        // ...and the same pool once the ceiling has been synced.
        double synced = SharedPoolCeiling.resultingCeiling(100.0, 2000.0);
        assertFalse(SharedPoolCeiling.wouldDestroyMana(2000.0, synced),
            "after the sync the ceiling must hold the full pool");
    }
}
