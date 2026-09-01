package com.otectus.arsnspells.data;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit D1 — {@link DecayAccumulator} must implement the documented proportional decay
 * curve. The pre-3.0.2 handler used {@code Math.max(1, Math.floor(level * perInterval))},
 * which turned the documented ~0.05%/interval proportional decay into a flat 1
 * point/interval (~20x faster), because {@code level * perInterval < 1} for every reachable
 * level (max 100) at default settings. That bug was still present on this branch when the
 * port started.
 *
 * <p>Keys are full school ids rather than the 1.20.1 {@code AffinityType} enum: this branch
 * re-keyed affinity storage so addon schools are tracked too, and the accumulator follows
 * that keying. Persistence is a codec (an optional field of {@link AffinityData}'s), so the
 * round-trip test goes through {@link DecayAccumulator#CODEC} instead of raw NBT.
 */
class AffinityDecayResidualTest {

    /** Default-config per-interval factor: rate 0.01 * (1200 / 24000). */
    private static final double DEFAULT_PER_INTERVAL = 0.01 * (1200.0 / 24000.0);

    private static final String SCHOOL = "irons_spellbooks:fire";
    private static final String OTHER = "irons_spellbooks:ice";

    @Test
    void level100AtDefaults_losesOnePointEvery20Intervals_notEveryInterval() {
        DecayAccumulator decay = new DecayAccumulator();
        double perLevel100 = 100 * DEFAULT_PER_INTERVAL; // 0.05/interval
        int totalDecay = 0;
        for (int interval = 1; interval <= 19; interval++) {
            totalDecay += decay.accrue(SCHOOL, perLevel100);
        }
        assertEquals(0, totalDecay,
            "level 100 at default rate accrues 0.05/interval; no whole point may be "
                + "removed before interval 20 (old bug: 1 point EVERY interval)");
        assertEquals(1, decay.accrue(SCHOOL, perLevel100),
            "after 20 intervals exactly one whole point has accrued (20 * 0.05 = 1.0)");
        assertEquals(0, decay.accrue(SCHOOL, perLevel100),
            "the remainder resets to ~0 after a point is taken; interval 21 removes nothing");
    }

    @Test
    void level1AtDefaults_doesNotInstantlyLoseItsOnlyPoint() {
        DecayAccumulator decay = new DecayAccumulator();
        assertEquals(0, decay.accrue(SCHOOL, 1 * DEFAULT_PER_INTERVAL),
            "a level-1 school accrues 0.0005/interval and must not lose its point on "
                + "the first interval (the old Math.max(1, ...) floor did exactly that)");
    }

    @Test
    void decayRemainder_survivesCodecRoundTrip() {
        DecayAccumulator decay = new DecayAccumulator();
        for (int i = 0; i < 10; i++) {
            decay.accrue(SCHOOL, 0.05); // remainder now 0.5
        }
        JsonElement encoded = DecayAccumulator.CODEC
            .encodeStart(JsonOps.INSTANCE, decay).getOrThrow();
        DecayAccumulator reloaded = DecayAccumulator.CODEC
            .parse(JsonOps.INSTANCE, encoded).getOrThrow();

        int totalDecay = 0;
        for (int i = 0; i < 10; i++) {
            totalDecay += reloaded.accrue(SCHOOL, 0.05);
        }
        assertEquals(1, totalDecay,
            "0.5 carried remainder must survive save/load so 10 more 0.05 accruals "
                + "complete the point — otherwise relogging resets the decay clock");
    }

    @Test
    void accrue_ignoresNonFiniteAndNegativeAmounts() {
        DecayAccumulator decay = new DecayAccumulator();
        decay.accrue(SCHOOL, 0.9);
        assertEquals(0, decay.accrue(SCHOOL, Double.NaN),
            "NaN accrual must be a no-op");
        assertEquals(0, decay.accrue(SCHOOL, -5.0),
            "negative accrual must be a no-op (decay never adds levels)");
        assertEquals(1, decay.accrue(SCHOOL, 0.1),
            "the 0.9 remainder must be intact after the rejected inputs");
    }

    @Test
    void decode_sanitizesCorruptRemainders() {
        // Hand-edited or corrupt data: a remainder is by construction in [0, 1).
        JsonElement corrupt = com.mojang.serialization.Codec
            .unboundedMap(com.mojang.serialization.Codec.STRING, com.mojang.serialization.Codec.DOUBLE)
            .encodeStart(JsonOps.INSTANCE, Map.of(SCHOOL, 999.0)).getOrThrow();
        DecayAccumulator decay = DecayAccumulator.CODEC
            .parse(JsonOps.INSTANCE, corrupt).getOrThrow();
        assertTrue(decay.accrue(SCHOOL, 0.0000001) <= 1,
            "a corrupt out-of-range remainder must be clamped to < 1.0 so it can "
                + "never dump a burst of decay points at once");
    }

    @Test
    void clear_dropsCarriedFraction() {
        DecayAccumulator decay = new DecayAccumulator();
        decay.accrue(SCHOOL, 0.9);
        decay.clear(SCHOOL);
        assertEquals(0, decay.accrue(SCHOOL, 0.2),
            "after clear the carried 0.9 is gone (0.2 alone < 1)");
    }

    @Test
    void perSchoolRemainders_areIndependent() {
        DecayAccumulator decay = new DecayAccumulator();
        decay.accrue(SCHOOL, 0.9);
        assertEquals(0, decay.accrue(OTHER, 0.2),
            "each school carries its own remainder; another school's 0.9 must not leak");
        assertEquals(1, decay.accrue(SCHOOL, 0.1),
            "the first school's 0.9 remainder must be unaffected by the other school");
    }
}
