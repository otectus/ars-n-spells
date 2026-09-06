package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.contract.AttemptState;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.QuoteModifier;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceUnit;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V01 - a failed cast releases its reservation exactly once.
 *
 * <p>The regression this guards: nothing tied a refund to the charge it was reversing. A long
 * cast that was interrupted and then also ended normally ran the release path twice and
 * credited the player twice, and a cross-cast whose Ars leg failed after the Iron's share had
 * been pre-paid could refund a payment that a different exit path had already refunded. The
 * ledger makes release idempotent, so the second caller gets nothing back rather than a second
 * credit - while still leaving the attempt terminal, because a caller that arrives second
 * still needs the attempt to end.
 */
class AttemptLedgerIntegrationTest {

    /** Counts every move, because "released once" is a claim about calls, not balances. */
    private static final class FakeAccess implements ResourceAccess {
        final Map<ResourceUnit, Double> balances = new HashMap<>();
        int debitCalls;
        int creditCalls;
        double totalCredited;

        FakeAccess(double ars, double irons) {
            balances.put(ResourceUnit.ARS_MANA, ars);
            balances.put(ResourceUnit.IRONS_MANA, irons);
        }

        @Override public double current(UUID player, ResourceUnit unit) {
            return balances.getOrDefault(unit, 0.0d);
        }
        @Override public double max(UUID player, ResourceUnit unit) { return 1000.0d; }
        @Override public double debit(UUID player, ResourceUnit unit, double amount) {
            debitCalls++;
            double moved = Math.min(current(player, unit), amount);
            balances.put(unit, current(player, unit) - moved);
            return moved;
        }
        @Override public double credit(UUID player, ResourceUnit unit, double amount) {
            creditCalls++;
            totalCredited += amount;
            balances.put(unit, current(player, unit) + amount);
            return amount;
        }
    }

    private static CostQuote quote(double ars, double irons) {
        return new CostQuote(
            new ResourceAmount(ResourceUnit.ARS_MANA, ars),
            List.of(QuoteModifier.factor("test", "test", 1.0d)),
            List.of(new ResourceAmount(ResourceUnit.ARS_MANA, ars),
                new ResourceAmount(ResourceUnit.IRONS_MANA, irons)),
            1);
    }

    private CastAttempt openReserved(FakeAccess access, UUID player, String carrier,
                                     double ars, double irons) {
        CastAttempt attempt = CastLedger.open(player, carrier, 0, quote(ars, irons), 0L);
        CastLedger.reserve(attempt, access);
        return attempt;
    }

    @Test
    void reserveThenFail_releasesExactlyOnce() {
        UUID player = UUID.randomUUID();
        FakeAccess access = new FakeAccess(500.0d, 500.0d);
        CastAttempt attempt = openReserved(access, player, "test:carrier", 40.0d, 25.0d);

        assertSame(AttemptState.RESERVED, attempt.state());
        assertEquals(460.0d, access.current(player, ResourceUnit.ARS_MANA), 1.0e-9d);
        assertEquals(475.0d, access.current(player, ResourceUnit.IRONS_MANA), 1.0e-9d);

        List<ResourceAmount> refunded = CastLedger.fail(attempt, access);

        assertEquals(2, refunded.size(), "both reserved legs come back");
        assertEquals(65.0d, access.totalCredited, 1.0e-9d);
        assertEquals(500.0d, access.current(player, ResourceUnit.ARS_MANA), 1.0e-9d);
        assertEquals(500.0d, access.current(player, ResourceUnit.IRONS_MANA), 1.0e-9d);
        assertSame(AttemptState.FAILED, attempt.state());
        assertTrue(attempt.isReleased());
    }

    @Test
    void aSecondReleaseIsANoOp() {
        UUID player = UUID.randomUUID();
        FakeAccess access = new FakeAccess(500.0d, 500.0d);
        CastAttempt attempt = openReserved(access, player, "test:carrier", 40.0d, 25.0d);

        CastLedger.fail(attempt, access);
        int creditsAfterFirst = access.creditCalls;
        double balanceAfterFirst = access.current(player, ResourceUnit.ARS_MANA);

        List<ResourceAmount> second = CastLedger.cancel(attempt, access);

        assertTrue(second.isEmpty(),
            "the second release must refund nothing - the refund already happened");
        assertEquals(creditsAfterFirst, access.creditCalls,
            "no second credit may reach the pool; that is the double-refund duplication bug");
        assertEquals(balanceAfterFirst, access.current(player, ResourceUnit.ARS_MANA), 1.0e-9d);
    }

    @Test
    void aCommittedAttemptKeepsThePayment() {
        UUID player = UUID.randomUUID();
        FakeAccess access = new FakeAccess(500.0d, 500.0d);
        CastAttempt attempt = openReserved(access, player, "test:carrier", 40.0d, 25.0d);

        CastLedger.commitAndComplete(attempt);

        assertSame(AttemptState.COMPLETED, attempt.state());
        assertEquals(0, access.creditCalls, "a successful cast refunds nothing");
        assertEquals(460.0d, access.current(player, ResourceUnit.ARS_MANA), 1.0e-9d);
        assertEquals(475.0d, access.current(player, ResourceUnit.IRONS_MANA), 1.0e-9d);
        assertFalse(CastLedger.findOpen(player, "test:carrier").isPresent(),
            "a settled attempt is dropped from the ledger");
    }

    @Test
    void aPartialDrainReservesOnlyWhatMoved_andRefundsOnlyThat() {
        // The pool holds less than the quote asks for. debit() reports what it actually
        // moved, so the reservation - and therefore the refund - is the smaller figure. The
        // old code refunded the amount requested and handed the player mana it never took.
        UUID player = UUID.randomUUID();
        FakeAccess access = new FakeAccess(10.0d, 500.0d);
        CastAttempt attempt = openReserved(access, player, "test:carrier", 40.0d, 25.0d);

        assertEquals(10.0d, attempt.reservedLegs().get(0).amount(), 1.0e-9d,
            "only 10 Ars mana was there, so only 10 was reserved");

        CastLedger.fail(attempt, access);

        assertEquals(35.0d, access.totalCredited, 1.0e-9d,
            "the refund is what was taken (10 + 25), not what was quoted (40 + 25)");
        assertEquals(10.0d, access.current(player, ResourceUnit.ARS_MANA), 1.0e-9d);
        assertEquals(500.0d, access.current(player, ResourceUnit.IRONS_MANA), 1.0e-9d);
    }

    @Test
    void theTtlSweepCancelsAndReleasesALeakedAttempt() {
        UUID player = UUID.randomUUID();
        FakeAccess access = new FakeAccess(500.0d, 500.0d);
        CastAttempt attempt = openReserved(access, player, "test:leaked", 40.0d, 25.0d);

        List<CastAttempt> swept = CastLedger.ledger()
            .expireOlderThan(CastLedger.ATTEMPT_TTL_TICKS + 1L, CastLedger.ATTEMPT_TTL_TICKS, access);

        assertTrue(swept.contains(attempt), "an attempt older than the TTL must be swept");
        assertSame(AttemptState.CANCELLED, attempt.state());
        assertEquals(500.0d, access.current(player, ResourceUnit.ARS_MANA), 1.0e-9d);
        assertFalse(CastLedger.findOpen(player, "test:leaked").isPresent());
    }
}
