package com.otectus.arsnspells.rituals;

import com.otectus.arsnspells.contract.InscriptionPlan;

/**
 * How many units one inscription moves (audit V19).
 *
 * <p>Closes the finding that transcription mutated the <em>whole</em> target stack for a single
 * source: a player who dropped 64 blank scrolls got one spell's worth of value and 63 items'
 * worth of loss, because the code stamped the stack it was handed instead of one unit of it.
 * One unit is the default here and the remainder is arithmetic the caller must hand back.
 *
 * <p>The batch route exists so "do 8 at once" cannot be bolted on later as an unbounded loop:
 * it charges {@link InscriptionPlan#consumedUnits()} <em>per unit</em>, is capped at
 * {@link #MAX_BATCH_UNITS}, is clamped to what is actually present, and returns a plan - so it
 * is still a preview that nothing has acted on.
 *
 * <p>Deliberately free of Minecraft types: this is the arithmetic, and it is unit-tested in the
 * bootstrap-free JUnit suite.
 */
public final class InscriptionBatch {

    /** One unit per operation. The only route the loom and the rituals take today. */
    public static final int DEFAULT_UNITS = 1;

    /**
     * Hard ceiling on an opt-in batch. Explicit rather than "whatever the stack holds" so the
     * worst case of a mis-click is bounded and statable in a preview.
     */
    public static final int MAX_BATCH_UNITS = 16;

    private InscriptionBatch() {
    }

    /**
     * Scale a one-unit plan up to {@code requestedUnits}, bounded by {@link #MAX_BATCH_UNITS}
     * and by {@code availableUnits}. Mutates nothing.
     *
     * <p>A refused plan is returned unchanged: scaling is not a way to make a rejection
     * permitted. When nothing is available the result is an explicit
     * {@link InscriptionPlan#REASON_INSUFFICIENT_STACK} refusal rather than a zero-unit "success".
     *
     * @param unitPlan       the plan for a single unit, as produced by
     *                       {@code InscriptionPlanner.plan}
     * @param requestedUnits how many units the caller asked for
     * @param availableUnits how many units the limiting stack actually holds
     */
    public static InscriptionPlan scale(InscriptionPlan unitPlan, int requestedUnits,
                                        int availableUnits) {
        if (unitPlan == null || !unitPlan.isPermitted()) {
            return unitPlan;
        }
        int units = Math.min(Math.max(requestedUnits, DEFAULT_UNITS), MAX_BATCH_UNITS);
        units = Math.min(units, Math.max(availableUnits, 0));
        if (units < 1) {
            return new InscriptionPlan(unitPlan.sourceKind(), unitPlan.targetKind(), 0, 0,
                InscriptionPlan.REASON_INSUFFICIENT_STACK);
        }
        return new InscriptionPlan(unitPlan.sourceKind(), unitPlan.targetKind(),
            unitPlan.consumedUnits() * units, unitPlan.outputCount() * units,
            unitPlan.reasonCode());
    }

    /** What is left of a stack of {@code availableUnits} after {@code consumedUnits} are taken. */
    public static int remainder(int availableUnits, int consumedUnits) {
        return Math.max(0, availableUnits - Math.max(0, consumedUnits));
    }
}
