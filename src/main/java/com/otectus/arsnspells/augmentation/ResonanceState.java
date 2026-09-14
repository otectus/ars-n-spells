package com.otectus.arsnspells.augmentation;

/** Pure, tick-based threshold/linger policy. Queries never refresh the linger deadline. */
public record ResonanceState(double multiplier, long expiresAt) {
    public static final ResonanceState INACTIVE = new ResonanceState(1.0, Long.MIN_VALUE);

    public static ResonanceState update(ResonanceState previous, long tick, double current,
                                        double maximum, double threshold, int duration,
                                        double strength, double cap) {
        if (!Double.isFinite(current) || !Double.isFinite(maximum) || maximum <= 0
                || !Double.isFinite(strength) || !Double.isFinite(cap)) return INACTIVE;
        double fraction = Math.max(0, Math.min(1, current / maximum));
        double limit = Math.max(1, cap);
        if (fraction >= threshold) {
            return new ResonanceState(Math.min(limit, 1 + fraction * Math.max(0, strength) * 0.2),
                tick + Math.max(0, duration));
        }
        if (previous != null && tick < previous.expiresAt()) {
            return new ResonanceState(Math.min(limit, previous.multiplier()), previous.expiresAt());
        }
        return INACTIVE;
    }
}
