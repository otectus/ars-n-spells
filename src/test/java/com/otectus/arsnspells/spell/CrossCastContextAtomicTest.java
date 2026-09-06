package com.otectus.arsnspells.spell;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The staging context is shared mutable state on the cast path, so what it may and may not hold
 * is itself a regression surface.
 *
 * <p>This class used to assert that {@code Entry.tryMarkMultiplierApplied()} was an atomic
 * one-shot latch (ANS-HIGH-004). Audit V01 removed the latch, and the assertion is inverted
 * rather than deleted: <b>the latch must stay gone.</b> It was a once-per-<em>attempt</em> guard
 * standing in for once-per-<em>event</em> idempotence, and Ars 5.13.1 builds a fresh cost event
 * on every query - {@code getResolveCost()} posts {@code SpellCostCalcEvent.Pre},
 * {@code getExpendedCost()} posts {@code Post}, and one listener sees both. So the latch did not
 * stop the premium being applied twice; it stopped it being applied to the second event at all,
 * which is how a repeated cost query returned the unmultiplied base cost. Re-adding it would
 * bring that back.
 *
 * <p>Repeatability itself is proved end to end by {@link CostQuoteRepeatabilityTest}.
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
    void entryIsStillConstructible() throws Exception {
        assertTrue(newEntry() != null, "the staging entry must still exist");
    }

    @Test
    void theOncePerAttemptLatchIsGone() {
        assertThrows(NoSuchMethodException.class,
            () -> CrossCastContext.Entry.class.getDeclaredMethod("tryMarkMultiplierApplied"),
            "tryMarkMultiplierApplied() suppressed later cost events instead of pricing them, so "
                + "the second query for one cast returned the unmultiplied base cost (audit V01). "
                + "Idempotence belongs per event, not per attempt.");
        assertThrows(NoSuchFieldException.class,
            () -> CrossCastContext.Entry.class.getDeclaredField("multiplierApplied"),
            "the latch's field must go with it, or the next caller will reach for it");
    }

    @Test
    void mutableFields_areDeclaredVolatile() throws NoSuchFieldException {
        // These carry writes from the cost-calc handler to the payment boundary, which may run
        // on a different thread under exotic mod chains. A torn read charges the wrong pool.
        Class<?> entry = CrossCastContext.Entry.class;
        for (String fieldName : new String[] {
                "arsCost", "issCost", "costsReady", "blocked", "spellId", "issPaid", "attemptId",
                "carrierIdentity"}) {
            Field f = entry.getDeclaredField(fieldName);
            assertTrue(Modifier.isVolatile(f.getModifiers()),
                fieldName + " must be volatile");
        }
    }

    @Test
    void carrierIdentity_isCarriedSoTheLedgerCanBeFound() throws NoSuchFieldException {
        Field f = CrossCastContext.Entry.class.getDeclaredField("carrierIdentity");
        assertTrue(f.getType() == String.class,
            "the carrier identity is the opaque String key the AttemptLedger files an attempt "
                + "under; anything richer would tie the ledger to an ItemStack that can be "
                + "swapped mid-cast");
    }
}
