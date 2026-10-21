package com.otectus.arsnspells.glyph;

/**
 * How many Iron's spell executions one Ars cast may still produce.
 *
 * <p>Every execution counts: the first delivery of each imported glyph and every extra one a
 * split, burst, linger or repeated delivery produces. The budget is fixed when the cast is
 * admitted, from {@code max_foreign_emissions_per_cast} clamped to its hard ceiling, so a reload
 * cannot widen a cast already in flight. Pure, so its accounting is unit-tested.
 */
public final class GlyphInvocationBudget {
    private final int limit;
    private int used;

    public GlyphInvocationBudget(int limit, int ceiling) {
        if (ceiling < 1) throw new IllegalArgumentException("ceiling must be positive");
        this.limit = Math.max(1, Math.min(limit, ceiling));
    }

    /** Take one execution; false when the budget is spent, in which case nothing was taken. */
    public boolean tryConsume() {
        if (used >= limit) return false;
        used++;
        return true;
    }

    /** Give back an execution that was taken but refused before any effect. */
    public void refund() {
        if (used > 0) used--;
    }

    public int used() { return used; }
    public int limit() { return limit; }
    public int remaining() { return limit - used; }
}
