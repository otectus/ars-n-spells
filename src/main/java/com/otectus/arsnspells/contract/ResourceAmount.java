package com.otectus.arsnspells.contract;

import java.util.Objects;

/**
 * A quantity of one {@link ResourceUnit}, validated at construction.
 *
 * <p>Closes the audit finding that a NaN or negative cost could travel all the way from a
 * misconfigured multiplier to a pool debit. {@code NaN} compares false against every
 * "can you afford it?" test, so a NaN cost silently made a spell free; a negative cost
 * credited the pool instead of draining it. Both are rejected here, once, rather than
 * re-checked at each seam.
 *
 * @param unit   the unit this amount is denominated in, never {@code null}
 * @param amount a finite, non-negative quantity
 */
public record ResourceAmount(ResourceUnit unit, double amount) {

    public ResourceAmount {
        Objects.requireNonNull(unit, "unit");
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("amount must be finite, got " + amount);
        }
        if (amount < 0.0d) {
            throw new IllegalArgumentException("amount must be non-negative, got " + amount);
        }
    }

    /** A zero amount in {@code unit}. */
    public static ResourceAmount zero(ResourceUnit unit) {
        return new ResourceAmount(unit, 0.0d);
    }

    /** {@code this} scaled by {@code factor}; the result is validated like any other amount. */
    public ResourceAmount scaled(double factor) {
        return new ResourceAmount(unit, amount * factor);
    }

    /** Whether this amount is exactly zero, i.e. free. */
    public boolean isZero() {
        return amount == 0.0d;
    }
}
