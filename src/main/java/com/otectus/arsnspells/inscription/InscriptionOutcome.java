package com.otectus.arsnspells.inscription;

import com.otectus.arsnspells.contract.InscriptionPlan;

import java.util.Objects;

/**
 * How an {@link InscriptionPlan} lands on two real stacks: what is charged, and what is handed
 * back (audit V19).
 *
 * <p>Closes the finding that transcription treated a stack as a unit. The source was consumed
 * whole for one inscription, and the target was inscribed whole for one payment -- so a stack
 * of 64 blanks came back as 64 charged outputs, and a stack of 64 scrolls vanished for one.
 * The plan already says one unit in and one unit out; this record is the arithmetic that splits
 * the remainder back rather than transforming what was never paid for.
 *
 * <p>Pure, and deliberately free of {@code ItemStack} so it can be pinned by the unit suite
 * without a live level.
 *
 * @param consumedFromSource  units taken off the source stack; always {@code 0} for a reusable
 *                            source and for any refused plan
 * @param sourceRemainder     units left on the source stack afterwards
 * @param transformedTargets  units of the target actually inscribed
 * @param targetRemainder     units of the target split back untouched
 * @param reasonCode          the plan's reason code, carried through so a caller can report it
 */
public record InscriptionOutcome(
    int consumedFromSource,
    int sourceRemainder,
    int transformedTargets,
    int targetRemainder,
    String reasonCode
) {
    public InscriptionOutcome {
        Objects.requireNonNull(reasonCode, "reasonCode");
    }

    /**
     * Apply {@code plan} to stacks of the given sizes.
     *
     * <p>A refused plan moves nothing: both remainders come back at their full input size. A
     * permitted plan whose stacks cannot cover it is downgraded to a refusal rather than
     * part-charged, so there is no state in which half an inscription has happened.
     */
    public static InscriptionOutcome of(InscriptionPlan plan, int sourceCount, int targetCount) {
        Objects.requireNonNull(plan, "plan");
        if (!plan.isPermitted()) {
            return refusal(plan.reasonCode(), sourceCount, targetCount);
        }
        if (sourceCount < plan.consumedUnits() || targetCount < plan.outputCount()) {
            return refusal(InscriptionPlan.REASON_INSUFFICIENT_STACK, sourceCount, targetCount);
        }
        return new InscriptionOutcome(
            plan.consumedUnits(), sourceCount - plan.consumedUnits(),
            plan.outputCount(), targetCount - plan.outputCount(),
            plan.reasonCode());
    }

    private static InscriptionOutcome refusal(String reasonCode, int sourceCount, int targetCount) {
        return new InscriptionOutcome(0, Math.max(0, sourceCount), 0, Math.max(0, targetCount),
            reasonCode);
    }

    /** Whether anything at all should be moved. */
    public boolean isPermitted() {
        return transformedTargets() > 0;
    }
}
