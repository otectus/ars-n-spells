package com.otectus.arsnspells.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The priced result of one cast: what it started as, how it was derived, and what is owed.
 *
 * <p>Closes the audit finding that "the cost" existed only as a set of mutable float fields on
 * a shared cross-cast context, rewritten by whichever handler ran last. Validation read one
 * value and the deduction read another. A quote is immutable data computed once by a
 * {@link QuotePolicy}; it charges nothing and reads nothing.
 *
 * <p>The quote also carries {@link #rulesGeneration()}, the {@link CostRules#generation()} it was
 * computed under. A caller holding a quote across a config reload can compare generations and
 * re-quote instead of charging a price the config no longer describes.
 *
 * @param origin        the cast's base price, in the unit the caster's own system quoted it in
 * @param breakdown     the ordered derivation steps, defensively copied and unmodifiable
 * @param legs          what is actually owed, per unit, defensively copied and unmodifiable
 * @param rulesGeneration the {@link CostRules} generation this quote was computed under
 */
public record CostQuote(
    ResourceAmount origin,
    List<QuoteModifier> breakdown,
    List<ResourceAmount> legs,
    int rulesGeneration
) {
    public CostQuote {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(breakdown, "breakdown");
        Objects.requireNonNull(legs, "legs");
        breakdown = Collections.unmodifiableList(new ArrayList<>(breakdown));
        legs = Collections.unmodifiableList(new ArrayList<>(legs));
        for (QuoteModifier m : breakdown) {
            Objects.requireNonNull(m, "breakdown entry");
        }
        for (ResourceAmount leg : legs) {
            Objects.requireNonNull(leg, "leg");
        }
    }

    /** The total owed in one unit, summed over every leg denominated in it. */
    public double total(ResourceUnit unit) {
        Objects.requireNonNull(unit, "unit");
        double sum = 0.0d;
        for (ResourceAmount leg : legs) {
            if (leg.unit() == unit) {
                sum += leg.amount();
            }
        }
        return sum;
    }

    /** Whether every leg is zero, i.e. the cast is free. */
    public boolean isFree() {
        for (ResourceAmount leg : legs) {
            if (!leg.isZero()) {
                return false;
            }
        }
        return true;
    }
}
