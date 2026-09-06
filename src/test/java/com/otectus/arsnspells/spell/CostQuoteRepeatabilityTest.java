package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.casting.CastLedger;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.ConversionKind;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.contract.RoundingRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V01 - a cost query must be repeatable, and must never charge.
 *
 * <p>The regression, exactly as the audit describes it: a valid Ars carrier, a native cost of
 * 100 and a cross-cast multiplier of 2. Upstream Ars 4.12.7's {@code getResolveCost()} builds
 * a <em>fresh</em> {@code SpellCostCalcEvent} on every call, and both {@code canCast()} and
 * {@code expendMana()} call it. The old handler guarded with {@code tryMarkMultiplierApplied},
 * a latch on the attempt rather than on the event, so it stamped the first event and returned
 * early on every later one: the first query answered 200 and the second answered 100. The
 * price of a spell depended on how many times something had asked.
 *
 * <p>A "fresh cost event" here is a fresh key carrying the same base cost, which is precisely
 * what upstream produces. That keeps the test on the real handler logic without a Minecraft
 * bootstrap - {@code CrossCastingHandler.onArsSpellCost} does nothing but unwrap the event
 * and call the method under test.
 */
class CostQuoteRepeatabilityTest {

    private static final int NATIVE_COST = 100;
    private static final double MULTIPLIER = 2.0d;

    private final UUID player = UUID.randomUUID();
    private final String carrier = "ars_nouveau:novice_spell_book";

    /** Balances only; nothing in a cost query is allowed to reach it. */
    private static final class RecordingAccess implements ResourceAccess {
        final Map<ResourceUnit, Double> balances = new HashMap<>();
        int debits;
        int credits;

        RecordingAccess() {
            balances.put(ResourceUnit.ARS_MANA, 1000.0d);
            balances.put(ResourceUnit.IRONS_MANA, 1000.0d);
        }

        @Override public double current(UUID p, ResourceUnit unit) {
            return balances.getOrDefault(unit, 0.0d);
        }
        @Override public double max(UUID p, ResourceUnit unit) { return 1000.0d; }
        @Override public double debit(UUID p, ResourceUnit unit, double amount) {
            debits++;
            double have = current(p, unit);
            double moved = Math.min(have, amount);
            balances.put(unit, have - moved);
            return moved;
        }
        @Override public double credit(UUID p, ResourceUnit unit, double amount) {
            credits++;
            balances.put(unit, current(p, unit) + amount);
            return amount;
        }
    }

    private final RecordingAccess access = new RecordingAccess();

    private static CostRules rules() {
        return CostRules.of("iss_primary", 1.0d, 1.0d, 0.5d, 0.5d,
            ConversionKind.FLAT_LEGACY, MULTIPLIER, RoundingRule.HALF_UP, 1);
    }

    private int query(Object freshEvent) {
        return CrossCastingHandler.quoteArsCostForEvent(freshEvent, player, carrier,
            CarrierPolicy.REUSABLE_BOOK_SEMANTICS, NATIVE_COST, rules(), 0L);
    }

    @AfterEach
    void closeAnyOpenAttempt() {
        // The ledger is process-wide; an attempt left open would leak into the next test.
        CastLedger.findOpen(player, carrier).ifPresent(a -> CastLedger.cancel(a, access));
    }

    @Test
    void twoFreshCostEventsQuoteTheSamePrice() {
        int first = query(new Object());
        int second = query(new Object());

        assertEquals(200, first,
            "a 100-mana spell at a 2x cross-cast multiplier costs 200");
        assertEquals(200, second,
            "the second query of the same cast must answer 200 as well. Before the attempt "
                + "ledger it answered 100, because the multiplier latch was per attempt and "
                + "suppressed every later event instead of stamping this one");
        assertEquals(first, second);
    }

    @Test
    void tenCostQueriesAllAgree() {
        int expected = query(new Object());
        for (int i = 0; i < 9; i++) {
            assertEquals(expected, query(new Object()),
                "query " + (i + 2) + " must match the first");
        }
    }

    @Test
    void aCostQueryNeverMovesAResource() {
        for (int i = 0; i < 10; i++) {
            query(new Object());
        }
        assertEquals(0, access.debits,
            "asking the price must not debit. SEPARATE mode used to consume the Iron's share "
                + "inside cost calculation, so ten reads took the Iron's leg ten times");
        assertEquals(0, access.credits, "asking the price must not credit either");
        assertEquals(1000.0d, access.current(player, ResourceUnit.ARS_MANA), 1.0e-9d);
        assertEquals(1000.0d, access.current(player, ResourceUnit.IRONS_MANA), 1.0e-9d);
    }

    @Test
    void theSameEventInstanceIsStampedOnce() {
        Object event = new Object();
        assertEquals(query(event), query(event),
            "idempotence is per event: re-posting one event must not compound the multiplier");
    }

    @Test
    void theFirstQueryOpensExactlyOneAttemptHoldingTheQuote() {
        query(new Object());
        query(new Object());

        Optional<CastAttempt> open = CastLedger.findOpen(player, carrier);
        assertTrue(open.isPresent(), "the first query opens the attempt that holds the quote");
        assertEquals(200.0d, open.get().quote().total(ResourceUnit.ARS_MANA), 1.0e-9d);
        assertEquals(1, CastLedger.ledger().openFor(player).size(),
            "a second query must read the open attempt, not open another one");
    }
}
