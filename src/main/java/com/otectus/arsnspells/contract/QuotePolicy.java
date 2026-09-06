package com.otectus.arsnspells.contract;

/**
 * The single seam that turns a base price into what is owed.
 *
 * <p>Closes the audit finding that pricing was spread across a validation mixin, a cost-calc
 * event handler, an expend-mana mixin, and two cross-cast handlers, each with its own copy of
 * the arithmetic. There is one implementation, {@link StandardQuotePolicy}; the interface exists
 * so tests and future policies can substitute one without a second copy of the formula.
 */
public interface QuotePolicy {

    /**
     * Price one cast.
     *
     * @param origin  the base price as the caster's own system quoted it
     * @param rules   the config snapshot this cast is priced under
     * @param carrier the semantics of the item the cast came from
     * @return an immutable quote; never {@code null}
     */
    CostQuote quote(ResourceAmount origin, CostRules rules, CarrierPolicy carrier);
}
