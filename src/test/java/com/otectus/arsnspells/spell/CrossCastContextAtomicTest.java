package com.otectus.arsnspells.spell;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-004 - {@link CrossCastContext.Entry#tryMarkMultiplierApplied} must be an atomic
 * one-shot, even under concurrent calls from multiple threads.
 *
 * <p>The cost-calc event can fire more than once per spell resolve (preview plus actual
 * deduction). A plain read-then-write let two concurrent fires both observe {@code false} and
 * apply the cross-cast premium twice; {@code compareAndSet} means exactly one caller wins the
 * "first" slot.
 */
class CrossCastContextAtomicTest {

    /**
     * Reflectively build an Entry. Going through {@code CrossCastContext.begin(...)} would
     * need a real {@code Player}, which cannot be constructed without booting Minecraft.
     */
    private static CrossCastContext.Entry newEntry() throws Exception {
        Constructor<CrossCastContext.Entry> ctor =
            CrossCastContext.Entry.class.getDeclaredConstructor(CrossSpellType.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(CrossSpellType.ARS_NOUVEAU, Long.MAX_VALUE);
    }

    @Test
    void singleCall_winsTheSlot() throws Exception {
        assertTrue(newEntry().tryMarkMultiplierApplied(), "first call must win the slot");
    }

    @Test
    void secondCall_returnsFalse() throws Exception {
        CrossCastContext.Entry entry = newEntry();
        entry.tryMarkMultiplierApplied();
        assertFalse(entry.tryMarkMultiplierApplied(),
            "subsequent calls must return false - the mark is one-shot");
    }

    @Test
    void concurrent16Threads_exactlyOneWins() throws Exception {
        final CrossCastContext.Entry entry = newEntry();
        final int n = 16;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(n);
        final AtomicInteger winners = new AtomicInteger(0);

        for (int i = 0; i < n; i++) {
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
            "exactly ONE thread must win the first-application slot under concurrent calls - "
                + "two winners means the cross-cast premium was charged twice");
    }

    @Test
    void theMarkIsAnAtomicBoolean_notAPlainFlag() throws Exception {
        Field f = CrossCastContext.Entry.class.getDeclaredField("multiplierApplied");
        assertEquals(java.util.concurrent.atomic.AtomicBoolean.class, f.getType(),
            "a plain boolean here is the racy form the ticket was filed against");
        assertTrue(Modifier.isFinal(f.getModifiers()),
            "the holder must be final so the reference cannot be swapped mid-cast");
    }

    @Test
    void mutableFields_areDeclaredVolatile() throws NoSuchFieldException {
        // The other mutable fields carry writes from the cost-calc handler to the TAIL mixin,
        // which may run on a different thread under exotic mod chains. A torn read charges
        // the wrong pool.
        Class<?> entry = CrossCastContext.Entry.class;
        for (String fieldName : new String[] {
                "arsCost", "issCost", "costsReady", "blocked", "spellId", "issPaid", "attemptId"}) {
            Field f = entry.getDeclaredField(fieldName);
            assertTrue(Modifier.isVolatile(f.getModifiers()),
                fieldName + " must be volatile (ANS-HIGH-004)");
        }
    }
}
