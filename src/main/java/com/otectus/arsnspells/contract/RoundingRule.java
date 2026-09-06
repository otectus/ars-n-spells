package com.otectus.arsnspells.contract;

/**
 * The one declared rounding rule applied at a unit boundary.
 *
 * <p>Closes the audit finding that the same cost was rounded three different ways depending on
 * which seam charged it: {@code (float)(cost * rate)} in one place, {@code (int) Math.round(...)}
 * in another, an implicit narrowing cast in a third. The amount checked could therefore differ
 * from the amount charged, and at the config's {@code 0.01} rate floor the difference made every
 * spell under fifty mana free. A cast now names its rule once, on {@link CostRules}, and every
 * boundary uses that one.
 */
public enum RoundingRule {
    /** Round to the nearest whole unit, ties away from zero. The historical default. */
    HALF_UP {
        @Override
        public long apply(double value) {
            return (long) Math.floor(value + 0.5d);
        }
    },
    /** Always round down. Never charges more than the quoted amount. */
    FLOOR {
        @Override
        public long apply(double value) {
            return (long) Math.floor(value);
        }
    };

    /**
     * Reduce a quoted amount to whole units.
     *
     * @param value a finite amount; callers get amounts from {@link ResourceAmount}, which
     *              already rejects NaN and infinity
     * @return the amount in whole units
     */
    public abstract long apply(double value);
}
