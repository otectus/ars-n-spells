package com.otectus.arsnspells.augmentation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `ANS-HIGH-006` / `ANS-HIGH-007` — resonance is a damage multiplier, so every input and the
 * output must be bounded.
 *
 * <p>The 1.21.1 port shipped without any of these clamps. Because the value is multiplied into
 * Iron's {@code getSpellPower} with nothing downstream to catch it, an unbounded
 * {@code manaPercent} — reachable whenever the pool sits above the {@code max_mana} attribute —
 * scaled Iron's spell damage without limit.
 *
 * <p>These target the pure helpers rather than {@code computeResonance}, which needs a
 * {@code Player} and Iron's {@code MagicData}. Iron's is a {@code compileOnly} dependency and
 * is absent at test runtime, so the arithmetic was extracted specifically to be reachable here.
 */
class ResonanceManagerClampTest {

    private static double clientResonanceField() throws Exception {
        Field f = ResonanceManager.class.getDeclaredField("clientResonance");
        f.setAccessible(true);
        return f.getDouble(null);
    }

    @Test
    void clientResonanceField_isVolatile() throws NoSuchFieldException {
        Field f = ResonanceManager.class.getDeclaredField("clientResonance");
        assertTrue(Modifier.isVolatile(f.getModifiers()),
            "clientResonance is written by the payload handler and read on the render path; "
                + "it must be volatile for that publication to be visible");
    }

    @Test
    void setClientResonance_rejectsNaN() throws Exception {
        ResonanceManager.setClientResonance(1.5f);
        ResonanceManager.setClientResonance(Float.NaN);
        double value = clientResonanceField();
        assertTrue(Double.isFinite(value), "clientResonance must stay finite after a NaN write");
        assertEquals(1.5, value, 1.0e-6,
            "a NaN write must leave the previous valid value in place, not poison the field - "
                + "NaN defeats every Math.min-style cap downstream");
    }

    @Test
    void setClientResonance_rejectsInfinity() throws Exception {
        ResonanceManager.setClientResonance(2.0f);
        ResonanceManager.setClientResonance(Float.POSITIVE_INFINITY);
        assertEquals(2.0, clientResonanceField(), 1.0e-6,
            "an infinite write must leave the previous valid value in place");
    }

    @Test
    void setClientResonance_clampsAbsurdValue() throws Exception {
        ResonanceManager.setClientResonance(1e9f);
        double value = clientResonanceField();
        assertTrue(value <= ResonanceManager.MAX_RESONANCE,
            "clientResonance must be capped at MAX_RESONANCE, got " + value);
        assertTrue(value > 0.0);
    }

    @Test
    void setClientResonance_clampsNegativeToZero() throws Exception {
        ResonanceManager.setClientResonance(-5.0f);
        assertEquals(0.0, clientResonanceField(), 1.0e-6,
            "a negative multiplier would heal the target instead of damaging it");
    }

    // ---- resonanceFor: the server-side value that actually scales damage ----

    @Test
    void resonanceFor_isNeutralAtEmptyPool() {
        assertEquals(1.0, ResonanceManager.resonanceFor(0.0, 1.0, 5.0), 1.0e-9,
            "an empty pool grants no bonus");
    }

    @Test
    void resonanceFor_matchesTheDocumentedCurveAtStockConfig() {
        // 1.0 + (manaPercent * strength * 0.2); full pool at strength 1.0 -> 1.2
        assertEquals(1.2, ResonanceManager.resonanceFor(1.0, 1.0, 5.0), 1.0e-9);
        assertEquals(1.1, ResonanceManager.resonanceFor(0.5, 1.0, 5.0), 1.0e-9);
    }

    @Test
    void resonanceFor_clampsManaPercentAboveFull() {
        // THE regression. mana above max is reachable: Iron's clamps writes down to the
        // max_mana attribute, so a drifted ceiling leaves the pool above it. Unclamped, this
        // is 1.0 + (5.0 * 10.0 * 0.2) = 11.0 and nothing downstream catches it.
        double clamped = ResonanceManager.resonanceFor(5.0, 10.0, 100.0);
        double asIfFull = ResonanceManager.resonanceFor(1.0, 10.0, 100.0);
        assertEquals(asIfFull, clamped, 1.0e-9,
            "manaPercent above 1.0 must be treated as a full pool, not scaled through");
        assertEquals(3.0, clamped, 1.0e-9);
    }

    @Test
    void resonanceFor_appliesTheConfiguredCap() {
        assertEquals(1.5, ResonanceManager.resonanceFor(1.0, 10.0, 1.5), 1.0e-9,
            "max_damage_multiplier must bind - it is a live config key, not decoration");
    }

    @Test
    void resonanceFor_neverExceedsTheHardCeilingEvenIfConfigIsAbsurd() {
        assertTrue(ResonanceManager.resonanceFor(1.0, 10.0, 1e9) <= ResonanceManager.MAX_RESONANCE,
            "a hand-edited config must not be able to lift resonance past the hard ceiling");
    }

    @Test
    void resonanceFor_isNeutralOnNonFiniteInput() {
        assertEquals(1.0, ResonanceManager.resonanceFor(Double.NaN, 1.0, 5.0), 1.0e-9);
        assertEquals(1.0, ResonanceManager.resonanceFor(1.0, Double.NaN, 5.0), 1.0e-9);
        assertEquals(1.0, ResonanceManager.resonanceFor(Double.POSITIVE_INFINITY, 1.0, 5.0), 1.0e-9,
            "a non-finite input must fall back to neutral rather than produce NaN, which "
                + "would sail through SpellScalingUtil's Math.min cap");
    }

    @Test
    void resonanceFor_treatsNegativeStrengthAsZero() {
        assertEquals(1.0, ResonanceManager.resonanceFor(1.0, -5.0, 5.0), 1.0e-9,
            "a negative strength must not invert the multiplier");
    }

    // ---- The threshold gate ----
    //
    // resonance_threshold and resonance_duration described a threshold-gated, lingering
    // resonance and were read by nothing in either the 1.20.1 or the 1.21.1 line. The gate is
    // orthogonal to the curve, so it was layered on top rather than the curve being rewritten:
    // resonanceFor still decides how large the bonus is, gateOpen decides whether it applies.
    // The default threshold of 0 must leave the gate permanently open, which is what makes
    // this a live knob rather than a behaviour change for every existing server.

    @Test
    void gate_isAlwaysOpenAtTheDefaultThresholdOfZero() {
        for (double percent : new double[] {0.0, 0.01, 0.5, 0.94, 1.0}) {
            assertTrue(ResonanceManager.gateOpen(percent, 0.0, Long.MAX_VALUE, 0),
                "threshold 0 must leave the gate open at " + percent + " mana, whatever the "
                    + "linger state: that is what reproduces the historical always-on "
                    + "behaviour byte for byte");
        }
    }

    @Test
    void gate_opensAtOrAboveTheThreshold() {
        assertTrue(ResonanceManager.gateOpen(0.95, 0.95, Long.MAX_VALUE, 100),
            "the boundary is inclusive: 'at or above'");
        assertTrue(ResonanceManager.gateOpen(1.0, 0.95, Long.MAX_VALUE, 100));
    }

    @Test
    void gate_staysOpenWhileLingering() {
        assertTrue(ResonanceManager.gateOpen(0.10, 0.95, 0, 100),
            "the cast that spends the mana must not switch off the bonus it earned");
        assertTrue(ResonanceManager.gateOpen(0.10, 0.95, 100, 100),
            "the linger boundary is inclusive");
    }

    @Test
    void gate_closesOnceTheLingerExpires() {
        assertTrue(!ResonanceManager.gateOpen(0.10, 0.95, 101, 100));
        assertTrue(!ResonanceManager.gateOpen(0.10, 0.95, Long.MAX_VALUE, 100),
            "a player who has never been above the threshold gets no bonus");
    }

    @Test
    void gate_withZeroDurationClosesImmediatelyBelowTheThreshold() {
        assertTrue(!ResonanceManager.gateOpen(0.94, 0.95, 0, 0),
            "duration 0 means no linger at all");
    }

    @Test
    void gate_treatsAnOutOfRangeThresholdAsClamped() {
        assertTrue(ResonanceManager.gateOpen(1.0, 5.0, Long.MAX_VALUE, 0),
            "a threshold above 1 would otherwise be unreachable and disable resonance "
                + "outright; it clamps to 1, which a full pool satisfies");
        assertTrue(ResonanceManager.gateOpen(0.0, -1.0, Long.MAX_VALUE, 0),
            "a negative threshold clamps to 0");
        assertTrue(ResonanceManager.gateOpen(0.0, Double.NaN, Long.MAX_VALUE, 0),
            "NaN must not make the comparison false forever and silently kill the feature");
    }

    @Test
    void gate_ignoresANegativeElapsedTime() {
        // Game time is per-level, so a dimension change can hand back a smaller value than the
        // one recorded. That must not read as "zero ticks ago" and pin the gate open.
        assertTrue(!ResonanceManager.gateOpen(0.10, 0.95, -50, 100));
    }

    @Test
    void clampManaPercent_boundsWhatTheGateCompares() {
        assertEquals(1.0, ResonanceManager.clampManaPercent(4.0), 1.0e-9,
            "mana above max is reachable, and an unclamped fraction would satisfy any "
                + "threshold while also unbounding the curve");
        assertEquals(0.0, ResonanceManager.clampManaPercent(-1.0), 1.0e-9);
        assertEquals(0.0, ResonanceManager.clampManaPercent(Double.NaN), 1.0e-9);
    }
}
