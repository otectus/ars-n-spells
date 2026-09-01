package com.otectus.arsnspells.data;

import com.mojang.serialization.Codec;

import java.util.HashMap;
import java.util.Map;

/**
 * Fractional decay carried between affinity decay intervals, always in [0, 1)
 * per school. With default decay settings a level loses well under one point
 * per interval; without this residual the handler's integer floor forced a
 * flat 1 point/interval (~20x the documented proportional rate — audit D1).
 *
 * <p>Lives outside {@link AffinityData}'s level map so the math is
 * unit-testable in isolation, and is persisted as an optional field of
 * AffinityData's codec so a school's residual survives saves and relogs.
 *
 * <p>Keys are full school ids ({@code "irons_spellbooks:fire"}), matching
 * {@link AffinityData}'s own keying. The 1.20.1 original keyed on the 16-value
 * {@code AffinityType} enum; this port follows AffinityData's string keys so
 * addon schools carry a residual too.
 *
 * <p>Threading contract matches AffinityData (ANS-MED-013): server main thread
 * only; the plain HashMap is intentional, not an oversight.
 */
public final class DecayAccumulator {

    /**
     * Persisted shape: a bare {@code {schoolId -> remainder}} map. Sanitized on
     * decode, so a hand-edited or corrupt file cannot inject a residual outside
     * [0, 1) and silently strip levels.
     */
    public static final Codec<DecayAccumulator> CODEC =
        Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
            .xmap(DecayAccumulator::fromMap, DecayAccumulator::snapshot);

    private final Map<String, Double> remainders = new HashMap<>();

    public DecayAccumulator() {}

    private static DecayAccumulator fromMap(Map<String, Double> raw) {
        DecayAccumulator acc = new DecayAccumulator();
        raw.forEach((key, remainder) -> {
            if (key == null || key.isEmpty() || remainder == null) {
                return;
            }
            // A remainder is by construction in [0, 1); clamp anything else away.
            if (Double.isFinite(remainder) && remainder > 0.0) {
                acc.remainders.put(key, Math.min(remainder, Math.nextDown(1.0)));
            }
        });
        return acc;
    }

    /**
     * Accumulates fractional decay for a school and returns the whole number of
     * points that should be removed now (0 on most intervals). The sub-1.0
     * remainder is retained (and persisted) so slow proportional decay is
     * honored across intervals, saves, and relogs instead of rounding up.
     *
     * @param amount fractional decay accrued this interval; non-finite or
     *               non-positive values are ignored and leave the remainder unchanged
     */
    public int accrue(String schoolKey, double amount) {
        if (schoolKey == null || schoolKey.isEmpty() || !Double.isFinite(amount) || amount <= 0.0) {
            return 0;
        }
        double total = remainders.getOrDefault(schoolKey, 0.0) + amount;
        int whole = (int) Math.floor(total);
        remainders.put(schoolKey, total - whole);
        return whole;
    }

    /** Drops any carried fractional decay, e.g. once a school reaches level 0. */
    public void clear(String schoolKey) {
        remainders.remove(schoolKey);
    }

    /** The currently carried residual for a school, in [0, 1). */
    public double remainderOf(String schoolKey) {
        return remainders.getOrDefault(schoolKey, 0.0);
    }

    /** A copy of every carried residual. Safe to iterate while mutating the original. */
    public Map<String, Double> snapshot() {
        return new HashMap<>(remainders);
    }

    public boolean isEmpty() {
        return remainders.isEmpty();
    }
}
