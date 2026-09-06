package com.otectus.arsnspells.contract;

import java.util.Objects;

/**
 * The description of one inscription. Describes an operation; performs none.
 *
 * <p>Closes the audit finding that the inscription code decided and acted in the same pass, so
 * a rejection halfway through had already shrunk a stack. Planning is separated from doing:
 * this record says what would happen, a caller inspects it, and only then does the loader move
 * items.
 *
 * @param sourceKind    what the spell is being read from
 * @param targetKind    what the spell is being written to
 * @param consumedUnits how many items are taken from the source stack; always {@code 0} for a
 *                      reusable source
 * @param outputCount   how many inscribed items the operation yields; {@code 0} means refused
 * @param reasonCode    why, in a stable machine-readable form
 */
public record InscriptionPlan(
    InscriptionSourceKind sourceKind,
    InscriptionSourceKind targetKind,
    int consumedUnits,
    int outputCount,
    String reasonCode
) {
    /** The operation is legal and will run. */
    public static final String REASON_OK = "ok";

    /** The target already carries a spell. A filled scroll is never treated as blank. */
    public static final String REASON_NOT_BLANK = "not blank";

    /** The target's own native container reports contents. */
    public static final String REASON_TARGET_NOT_EMPTY = "target not empty";

    /** The source stack has nothing left to consume. */
    public static final String REASON_INSUFFICIENT_STACK = "insufficient stack";

    public InscriptionPlan {
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(reasonCode, "reasonCode");
        if (consumedUnits < 0) {
            throw new IllegalArgumentException("consumedUnits must be non-negative, got " + consumedUnits);
        }
        if (outputCount < 0) {
            throw new IllegalArgumentException("outputCount must be non-negative, got " + outputCount);
        }
        if (sourceKind.isReusable() && consumedUnits != 0) {
            throw new IllegalArgumentException(
                "a reusable source is never consumed, but the plan consumes " + consumedUnits);
        }
    }

    /** Whether this plan would actually produce anything. */
    public boolean isPermitted() {
        return outputCount > 0;
    }
}
