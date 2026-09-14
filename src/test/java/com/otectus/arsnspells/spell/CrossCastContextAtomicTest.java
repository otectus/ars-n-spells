package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.contract.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

/** A query is immutable data; cast identity must never be a first-query pricing latch. */
class CrossCastContextAtomicTest {
    private static CrossCastContext.Entry entry(UUID attempt, long expiry) throws Exception {
        var ctor = CrossCastContext.Entry.class.getDeclaredConstructor(CrossSpellType.class, long.class, UUID.class);
        ctor.setAccessible(true);
        return ctor.newInstance(CrossSpellType.ARS_NOUVEAU, expiry, attempt);
    }
    @Test void initiationKeepsItsSuppliedAttemptIdentity() throws Exception {
        UUID attempt = UUID.randomUUID();
        var context = entry(attempt, 100);
        assertEquals(attempt, context.attemptId);
        assertFalse(context.isExpired(99));
        assertTrue(context.isExpired(100));
        assertEquals(attempt, context.attemptId);
    }
    @Test void nativeInitiationsReceiveDistinctNonNullIdentities() throws Exception {
        assertNotEquals(entry(null, 100).attemptId, entry(null, 100).attemptId);
    }
    @Test void concurrentQueriesReturnTheEntireSamePremiumEveryTime() throws Exception {
        var rules = CostRules.of("iss_primary", 2, 7, .5, .5, ConversionKind.FLAT_LEGACY, 1.25, RoundingRule.HALF_UP, 3);
        var executor = Executors.newFixedThreadPool(4);
        try {
            var calls = IntStream.range(0, 160).mapToObj(i -> (java.util.concurrent.Callable<CostQuote>) () ->
                StandardQuotePolicy.INSTANCE.quote(new ResourceAmount(ResourceUnit.ARS_MANA, 100),
                    rules, CarrierPolicy.REUSABLE_BOOK_SEMANTICS, 1000, 200)).toList();
            for (var result : executor.invokeAll(calls)) {
                assertEquals(250, result.get().total(ResourceUnit.IRONS_MANA));
                assertEquals(0, result.get().total(ResourceUnit.ARS_MANA));
            }
        } finally { executor.shutdownNow(); }
    }
}
