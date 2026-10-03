package com.otectus.arsnspells.bridge;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ARS_PRIMARY regeneration guard: scoped to one player's Iron's regen tick, never a spend. */
class IronsRegenScopeTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void scopeNamesOnlyThePlayerWhoseRegenIsRunning() {
        assertFalse(IronsRegenScope.isRegenTickFor(A));
        IronsRegenScope.run(A, () -> {
            assertTrue(IronsRegenScope.isRegenTickFor(A));
            assertFalse(IronsRegenScope.isRegenTickFor(B));
            return null;
        });
        assertFalse(IronsRegenScope.isRegenTickFor(A), "the scope must close with the tick");
    }

    @Test
    void nestedAndThrowingTicksRestoreTheOuterScope() {
        IronsRegenScope.run(A, () -> {
            assertThrows(IllegalStateException.class, () -> IronsRegenScope.run(B, () -> {
                throw new IllegalStateException("expected");
            }));
            assertTrue(IronsRegenScope.isRegenTickFor(A), "an inner failure must not leak or drop the outer scope");
            return null;
        });
        assertFalse(IronsRegenScope.isRegenTickFor(B));
    }

    @Test
    void regenerationMayRaiseButNeverLowerABalance() {
        assertTrue(IronsRegenScope.suppresses(80, 20), "a lagging mirror clamp is refused");
        assertTrue(IronsRegenScope.suppresses(100, 99), "an integer-truncated mirror at full mana is refused");
        assertFalse(IronsRegenScope.suppresses(50, 52), "ordinary regeneration applies");
        assertFalse(IronsRegenScope.suppresses(100, 100), "an unchanged balance is not a debit");
    }
}
