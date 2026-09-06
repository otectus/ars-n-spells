package com.otectus.arsnspells.contract;

import java.util.Objects;

/**
 * The decision of who pays for one operation.
 *
 * <p>Closes the audit finding that "which pool does this come out of?" was answered
 * independently at each seam, from whichever of the mana-mode, Iron's-presence and
 * active-bridge fields happened to be current. A route is the single answer, derived once from
 * a {@link ModeRoutingSnapshot}.
 *
 * @param payingUnit       the pool actually debited
 * @param nativeAlsoDebits whether the owning mod still performs its own deduction. When
 *                         {@code false} the native drain has been redirected and ANS owns the
 *                         charge; charging in both places is the double-spend this flag exists
 *                         to make impossible to write by accident.
 * @param direction        the conversion applied on the way to {@code payingUnit}, if any
 */
public record BillingRoute(ResourceUnit payingUnit, boolean nativeAlsoDebits, BillingRoute.Direction direction) {

    /** Which way a price was converted before it was billed. */
    public enum Direction {
        /** No conversion: the price was already denominated in the paying unit. */
        NONE,
        /** An Ars-denominated price was converted into Iron's mana. */
        ARS_TO_IRONS,
        /** An Iron's-denominated price was converted into Ars mana. */
        IRONS_TO_ARS
    }

    public BillingRoute {
        Objects.requireNonNull(payingUnit, "payingUnit");
        Objects.requireNonNull(direction, "direction");
    }

    /** A route that leaves the owning mod to price and charge its own cast. */
    public static BillingRoute nativeOwned(ResourceUnit unit) {
        return new BillingRoute(unit, true, Direction.NONE);
    }
}
