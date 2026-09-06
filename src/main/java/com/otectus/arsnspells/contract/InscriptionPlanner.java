package com.otectus.arsnspells.contract;

import java.util.Objects;

/**
 * Pure planning over an {@link InscriptionView} (audit V18).
 *
 * <p>Closes the two inscription findings that cost players items:
 *
 * <ul>
 *   <li>A reusable book or focus used as a source was consumed by some paths and not by others,
 *       because each path decided for itself. Here a reusable source is never consumed, and
 *       {@link InscriptionPlan} refuses to be constructed saying otherwise.</li>
 *   <li>A scroll already filled by another mod was classified blank, because "blank" meant "has
 *       no ANS data". Here a {@link InscriptionSourceKind#FILLED_SCROLL} is refused with reason
 *       {@link InscriptionPlan#REASON_NOT_BLANK}, and the native emptiness answer is asked for
 *       separately.</li>
 * </ul>
 */
public final class InscriptionPlanner {

    private InscriptionPlanner() {
    }

    /**
     * Decide what an inscription would do. Reads the view and nothing else; mutates nothing.
     */
    public static InscriptionPlan plan(InscriptionView view) {
        Objects.requireNonNull(view, "view");
        InscriptionSourceKind source = Objects.requireNonNull(view.sourceKind(), "sourceKind");
        InscriptionSourceKind target = Objects.requireNonNull(view.targetKind(), "targetKind");

        // A filled scroll is a filled scroll on either side of the operation. Checked before
        // anything else so no path can reach the "is it empty?" question with one in hand.
        if (target == InscriptionSourceKind.FILLED_SCROLL || source == InscriptionSourceKind.FILLED_SCROLL) {
            return new InscriptionPlan(source, target, 0, 0, InscriptionPlan.REASON_NOT_BLANK);
        }

        if (!view.targetIsNativelyEmpty()) {
            return new InscriptionPlan(source, target, 0, 0, InscriptionPlan.REASON_TARGET_NOT_EMPTY);
        }

        if (source.isReusable()) {
            return new InscriptionPlan(source, target, 0, 1, InscriptionPlan.REASON_OK);
        }

        if (view.sourceStackCount() < 1) {
            return new InscriptionPlan(source, target, 0, 0, InscriptionPlan.REASON_INSUFFICIENT_STACK);
        }

        return new InscriptionPlan(source, target, 1, 1, InscriptionPlan.REASON_OK);
    }
}
