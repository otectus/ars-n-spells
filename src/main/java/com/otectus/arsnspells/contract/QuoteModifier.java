package com.otectus.arsnspells.contract;

import java.util.Objects;

/**
 * One ordered step in a {@link CostQuote}'s breakdown.
 *
 * <p>Closes the audit finding that a quoted price could not explain itself. When a player asked
 * why a cast cost what it cost, the only answer available was to re-derive it by reading four
 * handlers. A quote now carries its own steps, in application order, so a tooltip or an
 * {@code /ans} diagnostic can print the derivation instead of the result alone.
 *
 * @param id             stable machine key, e.g. {@code "cross_cast_multiplier"}
 * @param description    short human-readable phrase for a tooltip or log line
 * @param factorOrDelta  the multiplier when {@code multiplicative}, otherwise the addend
 * @param multiplicative {@code true} if this step multiplies, {@code false} if it adds
 */
public record QuoteModifier(String id, String description, double factorOrDelta, boolean multiplicative) {

    public QuoteModifier {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
        if (Double.isNaN(factorOrDelta) || Double.isInfinite(factorOrDelta)) {
            throw new IllegalArgumentException("factorOrDelta must be finite, got " + factorOrDelta);
        }
    }

    /** A multiplicative step. */
    public static QuoteModifier factor(String id, String description, double factor) {
        return new QuoteModifier(id, description, factor, true);
    }

    /** An additive step. */
    public static QuoteModifier delta(String id, String description, double delta) {
        return new QuoteModifier(id, description, delta, false);
    }

    /** Apply this step to a running amount. */
    public double applyTo(double value) {
        return multiplicative ? value * factorOrDelta : value + factorOrDelta;
    }
}
