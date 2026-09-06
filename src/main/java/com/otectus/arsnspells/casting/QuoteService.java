package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ModeRoutingSnapshot;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.contract.RoundingRule;
import com.otectus.arsnspells.contract.StandardQuotePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one place a cast is priced (audit V05).
 *
 * <p>Closes the finding that validation and charging disagreed on conversion units and
 * percentages. They each read {@code AnsConfig} at their own instant and each carried their
 * own copy of the arithmetic, so the amount checked and the amount taken could differ by a
 * conversion rate, by a dual-cost share, or by both. Every seam now takes one
 * {@link CostRules} snapshot and hands it to {@link StandardQuotePolicy}; the resulting
 * {@link CostQuote} is what gets checked <em>and</em> what gets charged.
 *
 * <p>The snapshot is meant to be built once per cast attempt and carried. The no-argument
 * {@link #currentRules()} exists for the seams that are still single-shot (a ritual, a
 * tooltip); a cast that spans ticks must take one snapshot and pass it, or it is back to
 * reading a config that can change underneath it.
 */
public final class QuoteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuoteService.class);

    /**
     * The config generation whose share normalization has already been reported.
     *
     * <p>{@link CostRules} silently repairs a dual-cost split that does not sum to one, which
     * changes the effective price. Saying so once per config generation is the point: a pack
     * author gets told, and a cast storm does not fill the log. A reload bumps the generation
     * and the new value is reported again.
     */
    private static volatile int normalizationWarnedForGeneration = -1;

    private QuoteService() {
    }

    /**
     * Snapshot every config value that prices a cast, plus the effective mode.
     *
     * <p>The mode comes from the routing snapshot, not from a second config read, so the
     * rules and the routing a cast uses always describe the same instant.
     */
    public static CostRules currentRules() {
        ModeRoutingSnapshot routing = BridgeManager.getRoutingSnapshot();
        CostRules rules = CostRules.of(
            routing.effectiveMode(),
            AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get(),
            AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get(),
            AnsConfig.DUAL_COST_ARS_PERCENTAGE.get(),
            AnsConfig.DUAL_COST_ISS_PERCENTAGE.get(),
            AnsConfig.getConversionKind(),
            Math.max(0.0, AnsConfig.CROSS_CAST_COST_MULTIPLIER.get()),
            // The one declared rounding rule for this cast's unit boundaries. HALF_UP is what
            // the Ars cost event has always used (Math.round on an int cost).
            RoundingRule.HALF_UP,
            routing.generation());
        warnOnceIfSharesNormalized(rules);
        return rules;
    }

    /**
     * Price a cast the owning mod is performing itself: no cross-cast multiplier.
     *
     * @param origin   the unit the caster's own system quoted the price in
     * @param baseCost the price as that system quoted it; negatives are clamped to zero
     */
    public static CostQuote quoteNativeCast(ResourceUnit origin, double baseCost, CostRules rules) {
        return quote(origin, baseCost, rules, CarrierPolicy.NATIVE_ONLY);
    }

    /** Price a cast, applying the carrier's semantics. */
    public static CostQuote quote(ResourceUnit origin, double baseCost, CostRules rules,
                                  CarrierPolicy carrier) {
        double clamped = Math.max(0.0d, baseCost);
        return StandardQuotePolicy.INSTANCE.quote(new ResourceAmount(origin, clamped), rules, carrier);
    }

    /**
     * The quoted total in {@code unit}, narrowed to the {@code float} the mana bridges speak.
     *
     * <p>The contract computes in {@code double} while the bridges take {@code float}. The
     * narrowing stays here, at the loader boundary, exactly where the old code narrowed, so
     * observable costs do not shift: the two are equal arithmetically, not bit-identical
     * once narrowed, and doing it in a second place would reintroduce the very
     * check-versus-charge gap this class exists to close.
     */
    public static float legAsFloat(CostQuote quote, ResourceUnit unit) {
        return (float) quote.total(unit);
    }

    private static void warnOnceIfSharesNormalized(CostRules rules) {
        if (!rules.sharesNormalized()) {
            return;
        }
        if (normalizationWarnedForGeneration == rules.generation()) {
            return;
        }
        normalizationWarnedForGeneration = rules.generation();
        LOGGER.warn("dual_cost_ars_percentage and dual_cost_iss_percentage did not sum to 1.0; "
                + "they have been normalized to {} / {} for pricing. The effective price of a "
                + "dual-cost cast is not what the raw values describe - set them to sum to 1.0 "
                + "to silence this.",
            rules.arsShare(), rules.ironsShare());
    }
}
