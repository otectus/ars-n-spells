package com.otectus.arsnspells.spell;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-004 — verifies {@link CrossCastContext.Entry#tryMarkMultiplierApplied}
 * is an atomic one-shot, even under concurrent calls from multiple threads.
 *
 * <p>The cost-calc event can fire more than once per spell resolve (preview +
 * actual). The old design used a plain {@code boolean} read-then-write that
 * could let two concurrent fires both observe {@code false} and apply the
 * cross-cast multiplier twice. The fix wraps the flag in {@link java.util.concurrent.atomic.AtomicBoolean}
 * via compareAndSet; exactly one caller must win the "first" slot.
 */
class CrossCastContextAtomicTest {

    /**
     * Reflectively create an Entry instance for testing the atomic flag. We avoid
     * going through {@code CrossCastContext.begin(...)} because that requires a
     * real {@code Player}, which we cannot construct without booting Minecraft.
     */
    private static CrossCastContext.Entry newEntry() throws Exception {
        Constructor<CrossCastContext.Entry> ctor =
            CrossCastContext.Entry.class.getDeclaredConstructor(
                CrossSpellType.class, long.class, UUID.class);
        ctor.setAccessible(true);
        return ctor.newInstance(CrossSpellType.ARS_NOUVEAU, Long.MAX_VALUE, UUID.randomUUID());
    }

    @Test
    void singleCall_returnsTrueAndMarksApplied() throws Exception {
        CrossCastContext.Entry entry = newEntry();
        assertFalse(entry.isMultiplierApplied());
        assertTrue(entry.tryMarkMultiplierApplied(),
            "first call must win the slot");
        assertTrue(entry.isMultiplierApplied());
    }

    @Test
    void secondCall_returnsFalse() throws Exception {
        CrossCastContext.Entry entry = newEntry();
        entry.tryMarkMultiplierApplied();
        assertFalse(entry.tryMarkMultiplierApplied(),
            "subsequent calls must return false — one-shot");
    }

    @Test
    void concurrent16Threads_exactlyOneWins() throws Exception {
        final CrossCastContext.Entry entry = newEntry();
        final int N = 16;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(N);
        final AtomicInteger winners = new AtomicInteger(0);

        for (int i = 0; i < N; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    if (entry.tryMarkMultiplierApplied()) {
                        winners.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        done.await();

        assertEquals(1, winners.get(),
            "exactly ONE thread must win the first-application slot under concurrent calls");
    }

    @Test
    void mutableFields_areDeclaredVolatile() throws NoSuchFieldException {
        // ANS-HIGH-004 also marked the other mutable fields volatile so writes
        // from the cost-calc handler are visible to the TAIL mixin under exotic
        // threading. Reflectively verify the field modifiers.
        Class<?> entry = CrossCastContext.Entry.class;
        for (String fieldName : new String[]{"arsCost", "issCost", "costsReady", "blocked", "spellId"}) {
            Field f = entry.getDeclaredField(fieldName);
            int mods = f.getModifiers();
            assertTrue(java.lang.reflect.Modifier.isVolatile(mods),
                fieldName + " must be volatile (ANS-HIGH-004)");
        }
    }
}
