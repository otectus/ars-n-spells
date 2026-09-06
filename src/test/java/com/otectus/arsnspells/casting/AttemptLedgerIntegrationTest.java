package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.contract.AttemptLedger;
import com.otectus.arsnspells.contract.AttemptState;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V01 / V03 - reserve then fail must release exactly once, and a second release must move
 * nothing.
 *
 * <p>The defect: a long cast that was interrupted <em>and</em> then also ended normally ran two
 * different cleanup paths, and both credited the player. Nothing tied a refund to the charge it
 * was reversing, so "refund what this cast took" degraded into "add back the number we
 * remembered", once per path. With the cross-cast pre-payment in play a player could end a failed
 * cast richer than they started it.
 *
 * <p>These exercise the real {@link AttemptLedger} against a fake pool, which is the point: the
 * ledger is the only thing that moves resources now, so proving its release is one-shot proves it
 * for every caller.
 */
class AttemptLedgerIntegrationTest {

    /** A pool that records every movement, and clamps like a real one. */
    private static final class FakePools implements ResourceAccess {
        final Map<ResourceUnit, Double> balances = new EnumMap<>(ResourceUnit.class);
        final Map<ResourceUnit, Double> ceiling = new EnumMap<>(ResourceUnit.class);
        int debits;
        int credits;

        FakePools() {
            for (ResourceUnit unit : ResourceUnit.values()) {
                balances.put(unit, 1000.0d);
                ceiling.put(unit, 1000.0d);
            }
        }

        @Override public double current(UUID p, ResourceUnit u) { return balances.get(u); }
        @Override public double max(UUID p, ResourceUnit u) { return ceiling.get(u); }

        @Override
        public double debit(UUID p, ResourceUnit u, double amount) {
            debits++;
            double moved = Math.min(amount, balances.get(u));
            balances.put(u, balances.get(u) - moved);
            return moved;
        }

        @Override
        public double credit(UUID p, ResourceUnit u, double amount) {
            credits++;
            // Clamp at the ceiling, so a credit can move less than it was asked to - which is
            // exactly why the ledger records what moved rather than what was requested.
            double moved = Math.min(amount, ceiling.get(u) - balances.get(u));
            moved = Math.max(0.0d, moved);
            balances.put(u, balances.get(u) + moved);
            return moved;
        }
    }

    private static final UUID PLAYER = UUID.fromString("0a000000-0000-0000-0000-0000000001ed");

    private AttemptLedger ledger;
    private FakePools pools;

    @BeforeEach
    void setUp() {
        ledger = new AttemptLedger();
        pools = new FakePools();
    }

    private static CostQuote quote(double ars, double irons) {
        return new CostQuote(
            new ResourceAmount(ResourceUnit.ARS_MANA, ars),
            List.of(),
            List.of(new ResourceAmount(ResourceUnit.ARS_MANA, ars),
                new ResourceAmount(ResourceUnit.IRONS_MANA, irons)),
            1);
    }

    private CastAttempt opened(CostQuote quote) {
        CastAttempt attempt = ledger.open(PLAYER, "carrier#0", 1, quote, 0L);
        attempt.validate();
        attempt.markQuoted();
        return attempt;
    }

    @Test
    void reserveThenFail_releasesExactlyOnce() {
        CastAttempt attempt = opened(quote(30.0d, 20.0d));

        ledger.reserve(attempt, pools);
        assertEquals(970.0d, pools.balances.get(ResourceUnit.ARS_MANA), 1.0e-9d,
            "the Ars leg must be held");
        assertEquals(980.0d, pools.balances.get(ResourceUnit.IRONS_MANA), 1.0e-9d,
            "the Iron's leg must be held too - a dual-cost reservation is both legs or neither");

        List<ResourceAmount> firstRelease = ledger.fail(attempt, pools);
        assertEquals(2, firstRelease.size(), "both held legs come back");
        assertEquals(1000.0d, pools.balances.get(ResourceUnit.ARS_MANA), 1.0e-9d);
        assertEquals(1000.0d, pools.balances.get(ResourceUnit.IRONS_MANA), 1.0e-9d);
        assertSame(AttemptState.FAILED, attempt.state());
    }

    @Test
    void secondRelease_isANoOp() {
        CastAttempt attempt = opened(quote(30.0d, 20.0d));
        ledger.reserve(attempt, pools);
        ledger.fail(attempt, pools);

        int creditsAfterFirst = pools.credits;
        double arsAfterFirst = pools.balances.get(ResourceUnit.ARS_MANA);
        double ironsAfterFirst = pools.balances.get(ResourceUnit.IRONS_MANA);

        // The second path: an interrupt handler and a completion handler both settling the same
        // cast is the shape that produced the double refund.
        List<ResourceAmount> second = ledger.fail(attempt, pools);
        assertTrue(second.isEmpty(),
            "a second release must report that it refunded nothing, not repeat the refund");
        assertEquals(creditsAfterFirst, pools.credits,
            "the second release must not touch the pool at all");
        assertEquals(arsAfterFirst, pools.balances.get(ResourceUnit.ARS_MANA), 1.0e-9d,
            "the player must not end a failed cast richer than they started it");
        assertEquals(ironsAfterFirst, pools.balances.get(ResourceUnit.IRONS_MANA), 1.0e-9d);

        // A cancel arriving after a fail is the same hazard by another name.
        assertTrue(ledger.cancel(attempt, pools).isEmpty(),
            "cancel-after-fail must also refund nothing");
        assertEquals(creditsAfterFirst, pools.credits);
    }

    @Test
    void aPartialDrain_refundsOnlyWhatWasActuallyTaken() {
        // Only 10 Ars mana in the pool against a 30 quote: the drain moves 10, not 30.
        pools.balances.put(ResourceUnit.ARS_MANA, 10.0d);
        CastAttempt attempt = opened(quote(30.0d, 20.0d));

        ledger.reserve(attempt, pools);
        assertEquals(10.0d, attempt.reservedLegs().get(0).amount(), 1.0e-9d,
            "the reservation records what moved, not what was asked for - recording 30 here is "
                + "how a refund invented mana");

        ledger.fail(attempt, pools);
        assertEquals(10.0d, pools.balances.get(ResourceUnit.ARS_MANA), 1.0e-9d,
            "the refund returns the 10 that was taken, not the 30 that was quoted");
    }

    @Test
    void commitThenComplete_refundsNothing() {
        CastAttempt attempt = opened(quote(30.0d, 20.0d));
        ledger.reserve(attempt, pools);
        ledger.commit(attempt);
        int creditsBefore = pools.credits;

        ledger.complete(attempt);
        assertEquals(creditsBefore, pools.credits,
            "a successful cast keeps its payment; completing must not credit anything back");
        assertEquals(970.0d, pools.balances.get(ResourceUnit.ARS_MANA), 1.0e-9d);
        assertEquals(0, ledger.openCount(), "a completed attempt is forgotten");
    }

    @Test
    void anAttemptNoExitPathClosed_isSweptAndReleased() {
        CastAttempt attempt = opened(quote(30.0d, 20.0d));
        ledger.reserve(attempt, pools);

        assertTrue(ledger.expireOlderThan(10L, 100L, pools).isEmpty(),
            "a young attempt must survive the sweep - long casts legitimately span ticks");
        assertEquals(1, ledger.openCount());

        List<CastAttempt> expired = ledger.expireOlderThan(1000L, 100L, pools);
        assertEquals(1, expired.size(), "the leak guard must reclaim an abandoned attempt");
        assertEquals(1000.0d, pools.balances.get(ResourceUnit.ARS_MANA), 1.0e-9d,
            "and give the held mana back rather than leaving it held forever");
        assertEquals(0, ledger.openCount());
    }

    @Test
    void aSweptAttempt_cannotThenBeReleasedAgain() {
        CastAttempt attempt = opened(quote(30.0d, 20.0d));
        ledger.reserve(attempt, pools);
        ledger.expireOlderThan(1000L, 100L, pools);
        int creditsAfterSweep = pools.credits;

        // The late finish boundary arriving after the sweep already reclaimed the attempt.
        assertTrue(ledger.fail(attempt, pools).isEmpty());
        assertEquals(creditsAfterSweep, pools.credits,
            "a sweep followed by a late completion must not pay the player twice");
        assertFalse(attempt.tryMarkReleased(), "the one release has already been claimed");
    }
}
