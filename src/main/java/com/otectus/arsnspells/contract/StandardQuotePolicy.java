package com.otectus.arsnspells.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The one implementation of {@link QuotePolicy}.
 *
 * <p>Closes the audit finding that the two loaders had drifted apart on price. The arithmetic
 * below is the arithmetic the 1.20.1 and 1.21.1 cross-cast handlers already perform, written
 * once:
 *
 * <pre>
 *   total    = base * crossCastMultiplier          (cross-casts only; a native cast is not multiplied)
 *   SEPARATE : originLeg = total * originShare
 *              crossLeg  = total * crossShare * flatRate(origin)
 *   other    : leg       = total * flatRate(origin)
 *   DISABLED : leg       = total
 * </pre>
 *
 * <p>{@link ConversionKind#FLAT_LEGACY} applies {@code flatRate} exactly as shown, in that
 * factor order, so a world on the shipped config is priced identically to today.
 * {@link ConversionKind#EQUAL_PERCENT} substitutes {@code 1.0} for every {@code flatRate}: each
 * pool is asked for the same percentage of the cast's own price and the pool-size conversion is
 * left to the resource layer, which is the only layer that knows pool sizes.
 *
 * <p>Legs are exact, unrounded doubles by design. The loaders do not round a converted leg
 * today, so rounding here would itself be a price change; {@link CostRules#rounding()} is the
 * single declared rule a caller applies at the loader boundary where the old code narrowed.
 */
public final class StandardQuotePolicy implements QuotePolicy {

    /** Shared, stateless instance. */
    public static final StandardQuotePolicy INSTANCE = new StandardQuotePolicy();

    public StandardQuotePolicy() {
    }

    @Override
    public CostQuote quote(ResourceAmount origin, CostRules rules, CarrierPolicy carrier) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(carrier, "carrier");

        List<QuoteModifier> breakdown = new ArrayList<>();
        double running = origin.amount();

        if (carrier == CarrierPolicy.NATIVE_ONLY) {
            breakdown.add(QuoteModifier.factor("native_cast",
                "Native cast: the cross-cast multiplier does not apply", 1.0d));
        } else {
            double multiplier = rules.crossCastMultiplier();
            running = running * multiplier;
            breakdown.add(QuoteModifier.factor("cross_cast_multiplier",
                "Cross-cast cost multiplier", multiplier));
        }

        List<ResourceAmount> legs = new ArrayList<>();

        if (rules.isUnificationDisabled()) {
            breakdown.add(QuoteModifier.factor("unification_disabled",
                "Mana unification disabled: no conversion", 1.0d));
            legs.add(new ResourceAmount(origin.unit(), running));
            return new CostQuote(origin, breakdown, legs, rules.generation());
        }

        ResourceUnit crossUnit = opposite(origin.unit());
        double rate = rules.conversionKind() == ConversionKind.FLAT_LEGACY
            ? rules.flatRateFrom(origin.unit())
            : 1.0d;
        String rateId = rules.conversionKind() == ConversionKind.FLAT_LEGACY
            ? "flat_conversion"
            : "equal_percent_conversion";
        String rateText = rules.conversionKind() == ConversionKind.FLAT_LEGACY
            ? "Flat conversion rate applied to the cross-system leg"
            : "Equal-percentage policy: no flat rate applied, conversion deferred to the pools";

        if (rules.isDualCostMode() && crossUnit != null) {
            double originShare = rules.shareFor(origin.unit());
            double crossShare = rules.shareFor(crossUnit);
            breakdown.add(QuoteModifier.factor("dual_cost_origin_share",
                "Dual-cost share drawn from the origin pool", originShare));
            breakdown.add(QuoteModifier.factor("dual_cost_cross_share",
                "Dual-cost share drawn from the other pool", crossShare));
            breakdown.add(QuoteModifier.factor(rateId, rateText, rate));
            legs.add(new ResourceAmount(origin.unit(), running * originShare));
            legs.add(new ResourceAmount(crossUnit, running * crossShare * rate));
            return new CostQuote(origin, breakdown, legs, rules.generation());
        }

        breakdown.add(QuoteModifier.factor(rateId, rateText, rate));
        legs.add(new ResourceAmount(origin.unit(), running * rate));
        return new CostQuote(origin, breakdown, legs, rules.generation());
    }

    /**
     * The other half of the Ars / Iron's exchange, or {@code null} for a unit that has no
     * counterpart. LP and aura are alternative payments for a cast, not a second mana pool, so
     * they never take part in a dual-cost split.
     */
    private static ResourceUnit opposite(ResourceUnit unit) {
        if (unit == ResourceUnit.ARS_MANA) {
            return ResourceUnit.IRONS_MANA;
        }
        if (unit == ResourceUnit.IRONS_MANA) {
            return ResourceUnit.ARS_MANA;
        }
        return null;
    }
}
