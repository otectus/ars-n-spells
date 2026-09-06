package com.otectus.arsnspells.rituals;

import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.contract.InscriptionPlanner;
import com.otectus.arsnspells.contract.InscriptionSourceKind;
import com.otectus.arsnspells.contract.InscriptionView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V19: transcription mutated the whole target stack for a single source, so a player who
 * dropped a full stack got one spell's worth of value and 63 items' worth of loss.
 *
 * <p>These are the arithmetic contracts the loader paths use to size an inscription. Pure, so
 * they run in the bootstrap-free JUnit suite: {@link InscriptionBatch} holds no Minecraft types
 * for exactly that reason.
 */
class InscriptionBatchTest {

    private static InscriptionView view(InscriptionSourceKind source, int stackCount) {
        return new InscriptionView() {
            @Override
            public InscriptionSourceKind sourceKind() {
                return source;
            }

            @Override
            public InscriptionSourceKind targetKind() {
                return InscriptionSourceKind.BLANK_PARCHMENT;
            }

            @Override
            public boolean targetIsNativelyEmpty() {
                return true;
            }

            @Override
            public int sourceStackCount() {
                return stackCount;
            }
        };
    }

    @Test
    @DisplayName("a stack of 64 sources yields one output and returns 63")
    void fullStackOfSources_yieldsOneOutput_andReturnsSixtyThree() {
        InscriptionPlan plan =
            InscriptionPlanner.plan(view(InscriptionSourceKind.CONSUMABLE_SCROLL, 64));

        assertEquals(1, plan.outputCount(), "one unit is the default output");
        assertEquals(1, plan.consumedUnits(), "one unit is the default charge");
        assertEquals(63, InscriptionBatch.remainder(64, plan.consumedUnits()),
            "the untouched remainder must be handed back, not overwritten");
    }

    @Test
    @DisplayName("a reusable source is never consumed, however large the stack")
    void reusableSource_consumesNothing() {
        InscriptionPlan plan =
            InscriptionPlanner.plan(view(InscriptionSourceKind.REUSABLE_BOOK, 64));

        assertEquals(0, plan.consumedUnits());
        assertEquals(64, InscriptionBatch.remainder(64, plan.consumedUnits()));
    }

    @Test
    @DisplayName("the batch route charges per unit and is capped")
    void batch_chargesPerUnit_andIsCapped() {
        InscriptionPlan unit =
            InscriptionPlanner.plan(view(InscriptionSourceKind.CONSUMABLE_SCROLL, 64));

        InscriptionPlan four = InscriptionBatch.scale(unit, 4, 64);
        assertEquals(4, four.consumedUnits());
        assertEquals(4, four.outputCount());
        assertEquals(60, InscriptionBatch.remainder(64, four.consumedUnits()));

        InscriptionPlan capped = InscriptionBatch.scale(unit, 1000, 64);
        assertEquals(InscriptionBatch.MAX_BATCH_UNITS, capped.consumedUnits(),
            "an unbounded batch is how a stack gets eaten in one click");
    }

    @Test
    @DisplayName("the batch route never charges for more units than are present")
    void batch_clampsToAvailableUnits() {
        InscriptionPlan unit =
            InscriptionPlanner.plan(view(InscriptionSourceKind.CONSUMABLE_SCROLL, 3));

        InscriptionPlan scaled = InscriptionBatch.scale(unit, 8, 3);
        assertEquals(3, scaled.consumedUnits());
        assertEquals(3, scaled.outputCount());
        assertEquals(0, InscriptionBatch.remainder(3, scaled.consumedUnits()));

        InscriptionPlan none = InscriptionBatch.scale(unit, 8, 0);
        assertFalse(none.isPermitted());
        assertEquals(InscriptionPlan.REASON_INSUFFICIENT_STACK, none.reasonCode());
    }

    @Test
    @DisplayName("a refusal does not become permitted by being scaled")
    void batch_doesNotResurrectARefusal() {
        InscriptionPlan refused =
            InscriptionPlanner.plan(view(InscriptionSourceKind.FILLED_SCROLL, 64));
        assertFalse(refused.isPermitted());

        assertSame(refused, InscriptionBatch.scale(refused, 16, 64));
        assertTrue(InscriptionPlan.REASON_NOT_BLANK.equals(refused.reasonCode()));
    }
}
