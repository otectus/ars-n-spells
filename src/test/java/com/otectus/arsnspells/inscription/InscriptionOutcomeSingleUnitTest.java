package com.otectus.arsnspells.inscription;

import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.contract.InscriptionPlanner;
import com.otectus.arsnspells.contract.InscriptionSourceKind;
import com.otectus.arsnspells.contract.InscriptionView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V19 - one inscription is one unit.
 *
 * <p>Transcription used to hand the whole stack to the mutation step: a stack of 64 sources
 * was consumed for a single inscription, and a stack of 64 targets came back inscribed 64
 * times for one payment. {@link InscriptionOutcome} is the arithmetic that splits both sides,
 * and it is pure so it can be pinned here without a live level.
 */
class InscriptionOutcomeSingleUnitTest {

    /** A view with no ItemStack behind it, so the split arithmetic is testable off-thread. */
    private record FixedView(InscriptionSourceKind sourceKind, InscriptionSourceKind targetKind,
                             boolean targetIsNativelyEmpty, int sourceStackCount)
        implements InscriptionView {
    }

    private static InscriptionPlan consumableOntoBlank(int sourceCount) {
        return InscriptionPlanner.plan(new FixedView(
            InscriptionSourceKind.CONSUMABLE_SCROLL, InscriptionSourceKind.BLANK_PARCHMENT,
            true, sourceCount));
    }

    @Test
    @DisplayName("a stack of 64 sources yields one output and returns 63")
    void stackOfSixtyFourYieldsOneOutputAndReturnsSixtyThree() {
        InscriptionOutcome outcome = InscriptionOutcome.of(consumableOntoBlank(64), 64, 1);

        assertTrue(outcome.isPermitted(), "a full stack of legal sources must still be permitted");
        assertEquals(1, outcome.consumedFromSource(), "exactly one source unit is charged");
        assertEquals(63, outcome.sourceRemainder(), "the other 63 sources are returned untouched");
        assertEquals(1, outcome.transformedTargets(), "one inscription produces one output");
    }

    @Test
    @DisplayName("a stack of 64 targets is inscribed once, 63 stay blank")
    void stackOfSixtyFourTargetsIsInscribedOnce() {
        InscriptionOutcome outcome = InscriptionOutcome.of(consumableOntoBlank(1), 1, 64);

        assertEquals(1, outcome.transformedTargets(), "one payment transforms one target");
        assertEquals(63, outcome.targetRemainder(), "the rest of the target stack is split back");
    }

    @Test
    @DisplayName("a reusable source is never consumed and never returns a remainder short")
    void reusableSourceIsNeverConsumed() {
        InscriptionPlan plan = InscriptionPlanner.plan(new FixedView(
            InscriptionSourceKind.REUSABLE_BOOK, InscriptionSourceKind.BLANK_PARCHMENT, true, 1));
        InscriptionOutcome outcome = InscriptionOutcome.of(plan, 1, 1);

        assertEquals(0, outcome.consumedFromSource(), "a spellbook is read, not eaten");
        assertEquals(1, outcome.sourceRemainder(), "the book stays in the slot");
        assertEquals(1, outcome.transformedTargets());
    }

    @Test
    @DisplayName("a refused plan moves nothing at all")
    void refusedPlanMovesNothing() {
        InscriptionPlan plan = InscriptionPlanner.plan(new FixedView(
            InscriptionSourceKind.CONSUMABLE_SCROLL, InscriptionSourceKind.FILLED_SCROLL, true, 8));
        InscriptionOutcome outcome = InscriptionOutcome.of(plan, 8, 3);

        assertFalse(outcome.isPermitted());
        assertEquals(InscriptionPlan.REASON_NOT_BLANK, outcome.reasonCode());
        assertEquals(0, outcome.consumedFromSource());
        assertEquals(8, outcome.sourceRemainder());
        assertEquals(0, outcome.transformedTargets());
        assertEquals(3, outcome.targetRemainder());
    }

    @Test
    @DisplayName("an empty source stack cannot pay for an inscription")
    void emptySourceStackIsRefused() {
        InscriptionOutcome outcome = InscriptionOutcome.of(consumableOntoBlank(0), 0, 1);

        assertFalse(outcome.isPermitted());
        assertEquals(InscriptionPlan.REASON_INSUFFICIENT_STACK, outcome.reasonCode());
    }
}
