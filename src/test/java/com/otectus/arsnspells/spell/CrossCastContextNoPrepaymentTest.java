package com.otectus.arsnspells.spell;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V01 - the cross-cast context must hold no money and no per-attempt cost latch.
 *
 * <p>Replaces ANS-HIGH-004's CrossCastContextAtomicTest, which asserted that
 * {@code tryMarkMultiplierApplied} was an atomic one-shot. It was, and that was the bug:
 * making the latch atomic made it reliably wrong. A latch on the <em>attempt</em> suppressed
 * every cost event after the first, and upstream Ars builds a fresh event on every
 * {@code getResolveCost()} call, so the first query answered 200 and the second answered 100.
 * Idempotence belongs to the event, not the attempt, and it now lives in
 * {@code CrossCastingHandler.quoteArsCostForEvent}.
 *
 * <p>{@code issPaid} went with it. It recorded an Iron's share consumed <em>inside</em> cost
 * calculation - a debit performed by a question. The money now lives on the ledger's
 * {@code CastAttempt}, where a refund is tied to the charge it reverses.
 *
 * <p>This test exists so neither comes back by accident: a re-added field here would mean a
 * second, unreconciled record of what a cast owes, which is the shape of the original defect.
 */
class CrossCastContextNoPrepaymentTest {

    private static final List<String> FORBIDDEN_FIELDS = List.of("issPaid", "multiplierApplied");

    private static final List<String> FORBIDDEN_METHODS =
        List.of("tryMarkMultiplierApplied", "isMultiplierApplied");

    @Test
    void theEntryCarriesNoPrepaymentAndNoPerAttemptCostLatch() {
        List<String> present = Arrays.stream(CrossCastContext.Entry.class.getDeclaredFields())
            .map(Field::getName)
            .filter(FORBIDDEN_FIELDS::contains)
            .toList();
        assertTrue(present.isEmpty(),
            "these fields were deleted by V01 and must not return: " + present
                + ". A cost query may not charge, and cost idempotence is per event");
    }

    @Test
    void theMultiplierLatchMethodsAreGone() {
        List<String> present = Arrays.stream(CrossCastContext.Entry.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName)
            .filter(FORBIDDEN_METHODS::contains)
            .toList();
        assertTrue(present.isEmpty(),
            "the per-attempt multiplier latch was deleted by V01 and must not return: "
                + present);
    }

    @Test
    void theRemainingMutableFieldsAreStillVolatile() {
        // ANS-HIGH-004's surviving half: the cost-calc handler and the resolver mixins can
        // observe this entry from different threads under exotic mod chains.
        Class<?> entry = CrossCastContext.Entry.class;
        for (String fieldName : new String[]{"arsCost", "issCost", "costsReady", "blocked", "spellId"}) {
            try {
                Field f = entry.getDeclaredField(fieldName);
                assertTrue(java.lang.reflect.Modifier.isVolatile(f.getModifiers()),
                    fieldName + " must be volatile (ANS-HIGH-004)");
            } catch (NoSuchFieldException e) {
                throw new AssertionError("expected field " + fieldName + " on CrossCastContext.Entry", e);
            }
        }
    }
}
