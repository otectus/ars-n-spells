package com.otectus.arsnspells.casting;

/** Number formatting for the caster-facing shortage message. Pure, so it is unit-testable. */
public final class PaymentMessages {
    private PaymentMessages() {}

    /**
     * Whole amounts print without a fraction; anything else keeps one decimal, rounded down so
     * an "available" figure never overstates what the caster has.
     */
    public static String amount(double value) {
        double tenths = Math.floor(value * 10 + 1e-9) / 10;
        return tenths == Math.rint(tenths) ? String.valueOf((long) tenths) : String.valueOf(tenths);
    }
}
