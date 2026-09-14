package com.otectus.arsnspells.inscription;

import com.otectus.arsnspells.contract.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InscriptionSourcePolicyTest {
    record View(InscriptionSourceKind sourceKind, InscriptionSourceKind targetKind,
                boolean targetIsNativelyEmpty, int sourceStackCount) implements InscriptionView {}

    @Test void filledNativeScrollIsAConsumableSourceButNeverABlankTarget() {
        var source = InscriptionPlanner.plan(new View(InscriptionSourceKind.FILLED_SCROLL,
            InscriptionSourceKind.BLANK_PARCHMENT, true, 1));
        assertTrue(source.isPermitted());
        assertEquals(1, source.consumedUnits());
        var target = InscriptionPlanner.plan(new View(InscriptionSourceKind.REUSABLE_BOOK,
            InscriptionSourceKind.FILLED_SCROLL, true, 1));
        assertFalse(target.isPermitted());
        assertEquals(0, target.consumedUnits());
    }

    @Test void missingReusableSourceCannotCreateOutput() {
        var plan = InscriptionPlanner.plan(new View(InscriptionSourceKind.REUSABLE_BOOK,
            InscriptionSourceKind.BLANK_PARCHMENT, true, 0));
        assertFalse(plan.isPermitted());
        assertEquals(InscriptionPlan.REASON_INSUFFICIENT_STACK, plan.reasonCode());
    }
}
