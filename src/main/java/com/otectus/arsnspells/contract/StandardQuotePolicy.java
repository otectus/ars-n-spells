package com.otectus.arsnspells.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure quote in final paying units. Rates apply only across a resource boundary. */
public final class StandardQuotePolicy implements QuotePolicy {
    public static final StandardQuotePolicy INSTANCE = new StandardQuotePolicy();

    @Override
    public CostQuote quote(ResourceAmount origin, CostRules rules, CarrierPolicy carrier) {
        return quote(origin, rules, carrier, 1, 1);
    }

    public CostQuote quote(ResourceAmount origin, CostRules rules, CarrierPolicy carrier,
                           double nativeArsMax, double nativeIronsMax) {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(rules);
        Objects.requireNonNull(carrier);
        boolean cross = carrier != CarrierPolicy.NATIVE_ONLY;
        double total = origin.amount() * (cross ? rules.crossCastMultiplier() : 1);
        List<QuoteModifier> modifiers = new ArrayList<>();
        modifiers.add(QuoteModifier.factor(cross ? "cross_cast_multiplier" : "native_cast",
            cross ? "Cross-cast multiplier" : "Native cast", cross ? rules.crossCastMultiplier() : 1));
        if (!Double.isFinite(total) || total > Integer.MAX_VALUE) throw new IllegalArgumentException("Unbounded cast cost");
        ResourceUnit other = opposite(origin.unit());
        if (rules.isUnificationDisabled() || other == null || (rules.isDualCostMode() && !cross)) {
            return new CostQuote(origin, modifiers, List.of(leg(origin.unit(), total, rules)), rules.generation());
        }
        double conversion = rules.flatRateFrom(origin.unit());
        if (rules.conversionKind() == ConversionKind.EQUAL_PERCENT) {
            double fromMax = origin.unit() == ResourceUnit.ARS_MANA ? nativeArsMax : nativeIronsMax;
            double toMax = origin.unit() == ResourceUnit.ARS_MANA ? nativeIronsMax : nativeArsMax;
            if (!Double.isFinite(fromMax) || !Double.isFinite(toMax) || fromMax <= 0 || toMax <= 0)
                throw new IllegalArgumentException("Equal-percentage pricing requires positive native ceilings");
            conversion = toMax / fromMax;
        }
        if (rules.isDualCostMode()) {
            modifiers.add(QuoteModifier.factor("dual_cost_origin_share", "Origin share", rules.shareFor(origin.unit())));
            modifiers.add(QuoteModifier.factor("dual_cost_cross_share", "Foreign share", rules.shareFor(other)));
            modifiers.add(QuoteModifier.factor("conversion", "Directional conversion", conversion));
            return new CostQuote(origin, modifiers, List.of(
                leg(origin.unit(), total * rules.shareFor(origin.unit()), rules),
                leg(other, total * rules.shareFor(other) * conversion, rules)), rules.generation());
        }
        ResourceUnit target = "ars_primary".equals(rules.modeName()) ? ResourceUnit.ARS_MANA : ResourceUnit.IRONS_MANA;
        double rate = target == origin.unit() ? 1 : conversion;
        modifiers.add(QuoteModifier.factor("conversion", "Conversion to paying pool", rate));
        return new CostQuote(origin, modifiers, List.of(leg(target, total * rate, rules)), rules.generation());
    }

    private static ResourceAmount leg(ResourceUnit unit, double amount, CostRules rules) {
        if (!Double.isFinite(amount) || amount > Integer.MAX_VALUE || amount < 0)
            throw new IllegalArgumentException("Unbounded payment leg");
        return new ResourceAmount(unit, rules.rounding().apply(amount));
    }

    private static ResourceUnit opposite(ResourceUnit unit) {
        return unit == ResourceUnit.ARS_MANA ? ResourceUnit.IRONS_MANA
            : unit == ResourceUnit.IRONS_MANA ? ResourceUnit.ARS_MANA : null;
    }
}