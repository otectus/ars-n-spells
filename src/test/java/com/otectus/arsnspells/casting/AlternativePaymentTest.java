package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.contract.CompatibilityStatus;
import com.otectus.arsnspells.contract.PaymentOpenFailurePolicy;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V23 / V24 - an alternative payment leg is reserved, committed or released as part of
 * one transaction, and a drain that came up short is never reported as a completed payment.
 *
 * <p>The regressions these pin:
 *
 * <ul>
 *   <li><b>V23.</b> The pre-cast check and the resolve-time charge read two unrelated FIFO
 *       queues, so a cast could be validated against one staged cost and charged against
 *       another. A leg is now keyed on the cast's own identity, and asking twice for one cast
 *       cannot take twice.</li>
 *   <li><b>V24.</b> The LP and aura consume calls answered a boolean that was true for a
 *       partial drain. A cast could therefore proceed having paid a fraction of its price, and
 *       a refund gave back the price rather than the fraction.</li>
 * </ul>
 */
class AlternativePaymentTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    /** A pool that reports exactly what it moved, and can be told to move less than asked. */
    private static final class FakePool implements ResourceAccess {
        private final ResourceUnit unit;
        private double balance;
        /** Cap on any single debit, so a partial drain can be produced on demand. */
        private double debitCeiling = Double.MAX_VALUE;
        /** How much of a credit actually lands; a lossy adapter returns less than asked. */
        private double creditFraction = 1.0d;
        int debits;
        int credits;

        FakePool(ResourceUnit unit, double balance) {
            this.unit = unit;
            this.balance = balance;
        }

        @Override
        public double current(UUID player, ResourceUnit asked) {
            return asked == unit ? balance : 0.0d;
        }

        @Override
        public double max(UUID player, ResourceUnit asked) {
            return current(player, asked);
        }

        @Override
        public double debit(UUID player, ResourceUnit asked, double amount) {
            if (asked != unit) {
                return 0.0d;
            }
            debits++;
            double moved = Math.min(Math.min(amount, balance), debitCeiling);
            balance -= moved;
            return moved;
        }

        @Override
        public double credit(UUID player, ResourceUnit asked, double amount) {
            if (asked != unit) {
                return 0.0d;
            }
            credits++;
            double moved = amount * creditFraction;
            balance += moved;
            return moved;
        }
    }

    private static final CompatibilityStatus VERIFIED_LP =
        CompatibilityStatus.verified("ans:sanctified_lp");
    private static final CompatibilityStatus VERIFIED_AURA =
        CompatibilityStatus.verified("ans:covenant_aura");
    private static final CompatibilityStatus DEGRADED_AURA = new CompatibilityStatus(
        "ans:covenant_aura", CompatibilityStatus.State.DEGRADED, "reflection bridge incomplete");

    @BeforeEach
    void clearOpenLegs() {
        AlternativePayment.clearForTest();
    }

    @Test
    void insufficientLpDeniesBeforeInitiationWithNoDebit() {
        FakePool lp = new FakePool(ResourceUnit.LP, 40.0d);
        AlternativePayment.Result result = AlternativePayment.reserve(
            UUID.randomUUID(), PLAYER, ResourceUnit.LP, 100.0d, lp, VERIFIED_LP,
            PaymentOpenFailurePolicy.REFUSE);

        assertEquals(AlternativePayment.Outcome.DENIED, result.outcome());
        assertFalse(result.allowsCast());
        assertEquals(0, lp.debits, "the pool was touched even though the balance check failed");
        assertEquals(40.0d, lp.current(PLAYER, ResourceUnit.LP));
        assertEquals(0, AlternativePayment.openCount());
    }

    @Test
    void aFullDrainReservesAndCommitsExactlyWhatItTook() {
        UUID attempt = UUID.randomUUID();
        FakePool lp = new FakePool(ResourceUnit.LP, 500.0d);
        AlternativePayment.Result result = AlternativePayment.reserve(
            attempt, PLAYER, ResourceUnit.LP, 100.0d, lp, VERIFIED_LP,
            PaymentOpenFailurePolicy.REFUSE);

        assertEquals(AlternativePayment.Outcome.RESERVED, result.outcome());
        assertEquals(100.0d, result.leg().reserved());
        assertEquals(400.0d, lp.current(PLAYER, ResourceUnit.LP));

        assertEquals(100.0d, AlternativePayment.commit(attempt));
        assertEquals(0, lp.credits, "a commit must never refund");
        assertEquals(0, AlternativePayment.openCount());
    }

    @Test
    void partialAuraUnderRefuseDeniesAndReleasesTheReservation() {
        UUID attempt = UUID.randomUUID();
        FakePool aura = new FakePool(ResourceUnit.AURA, 1000.0d);
        aura.debitCeiling = 30.0d; // the surrounding chunks only had 30 to give

        AlternativePayment.Result result = AlternativePayment.reserve(
            attempt, PLAYER, ResourceUnit.AURA, 100.0d, aura, VERIFIED_AURA,
            PaymentOpenFailurePolicy.REFUSE);

        assertEquals(AlternativePayment.Outcome.DENIED, result.outcome());
        assertNull(result.leg(), "a denied cast must not be left holding a leg");
        assertEquals(1, aura.credits, "the 30 that was drained was not given back");
        assertEquals(1000.0d, aura.current(PLAYER, ResourceUnit.AURA),
            "the release did not restore the partial drain");
        assertEquals(0, AlternativePayment.openCount());
    }

    @Test
    void partialAuraUnderLegacyOpenBehavesAsBeforeAndHoldsOnlyWhatMoved() {
        UUID attempt = UUID.randomUUID();
        FakePool aura = new FakePool(ResourceUnit.AURA, 1000.0d);
        aura.debitCeiling = 30.0d;

        AlternativePayment.Result result = AlternativePayment.reserve(
            attempt, PLAYER, ResourceUnit.AURA, 100.0d, aura, VERIFIED_AURA,
            PaymentOpenFailurePolicy.LEGACY_OPEN);

        assertEquals(AlternativePayment.Outcome.RESERVED_SHORT, result.outcome());
        assertTrue(result.allowsCast(), "legacy_open must reproduce the historical free pass");
        assertTrue(result.leg().isShort());
        assertEquals(30.0d, result.leg().reserved(),
            "the leg must hold what moved, not what was asked for");
        assertEquals(30.0d, AlternativePayment.commit(attempt),
            "the commit must keep the short amount, not the quote");
    }

    @Test
    void anUnusableAdapterDeniesUnderRefuseAndPassesUnderLegacyOpen() {
        FakePool aura = new FakePool(ResourceUnit.AURA, 1000.0d);

        AlternativePayment.Result refused = AlternativePayment.reserve(
            UUID.randomUUID(), PLAYER, ResourceUnit.AURA, 100.0d, aura, DEGRADED_AURA,
            PaymentOpenFailurePolicy.REFUSE);
        assertEquals(AlternativePayment.Outcome.DENIED, refused.outcome());
        assertEquals(0, aura.debits, "a degraded adapter must not be asked to charge anybody");

        AlternativePayment.Result legacy = AlternativePayment.reserve(
            UUID.randomUUID(), PLAYER, ResourceUnit.AURA, 100.0d, aura, DEGRADED_AURA,
            PaymentOpenFailurePolicy.LEGACY_OPEN);
        assertEquals(AlternativePayment.Outcome.RESERVED_SHORT, legacy.outcome());
        assertTrue(legacy.allowsCast());
        assertEquals(0, aura.debits);
    }

    @Test
    void anInterruptReleasesExactlyWhatWasTakenAndOnlyOnce() {
        UUID attempt = UUID.randomUUID();
        FakePool lp = new FakePool(ResourceUnit.LP, 500.0d);
        AlternativePayment.reserve(attempt, PLAYER, ResourceUnit.LP, 100.0d, lp, VERIFIED_LP,
            PaymentOpenFailurePolicy.REFUSE);

        assertEquals(100.0d, AlternativePayment.release(attempt, lp));
        assertEquals(500.0d, lp.current(PLAYER, ResourceUnit.LP));
        // The double-refund the ledger contract exists to prevent, at the alternative leg.
        assertEquals(0.0d, AlternativePayment.release(attempt, lp));
        assertEquals(500.0d, lp.current(PLAYER, ResourceUnit.LP));
        assertEquals(1, lp.credits);
    }

    @Test
    void reservingTwiceForOneCastTakesOnce() {
        UUID attempt = UUID.randomUUID();
        FakePool lp = new FakePool(ResourceUnit.LP, 500.0d);
        AlternativePayment.reserve(attempt, PLAYER, ResourceUnit.LP, 100.0d, lp, VERIFIED_LP,
            PaymentOpenFailurePolicy.REFUSE);
        AlternativePayment.Result second = AlternativePayment.reserve(
            attempt, PLAYER, ResourceUnit.LP, 100.0d, lp, VERIFIED_LP,
            PaymentOpenFailurePolicy.REFUSE);

        assertEquals(AlternativePayment.Outcome.RESERVED, second.outcome());
        assertEquals(1, lp.debits, "the second reserve for the same cast charged again");
        assertEquals(400.0d, lp.current(PLAYER, ResourceUnit.LP));
    }

    @Test
    void aCommittedLegIsNeverReleased() {
        UUID attempt = UUID.randomUUID();
        FakePool lp = new FakePool(ResourceUnit.LP, 500.0d);
        AlternativePayment.reserve(attempt, PLAYER, ResourceUnit.LP, 100.0d, lp, VERIFIED_LP,
            PaymentOpenFailurePolicy.REFUSE);
        AlternativePayment.commit(attempt);

        assertEquals(0.0d, AlternativePayment.release(attempt, lp));
        assertEquals(400.0d, lp.current(PLAYER, ResourceUnit.LP),
            "a spell that already paid was refunded by a late interrupt");
    }

    /**
     * A lossy adapter - one whose credit puts back less than it took - must be reported, not
     * silently absorbed. The release still settles the leg so it cannot be released twice.
     */
    @Test
    void aLossyReleaseReportsWhatActuallyLanded() {
        UUID attempt = UUID.randomUUID();
        FakePool aura = new FakePool(ResourceUnit.AURA, 1000.0d);
        AlternativePayment.reserve(attempt, PLAYER, ResourceUnit.AURA, 100.0d, aura, VERIFIED_AURA,
            PaymentOpenFailurePolicy.REFUSE);
        aura.creditFraction = 0.5d;

        assertEquals(50.0d, AlternativePayment.release(attempt, aura));
        assertEquals(0, AlternativePayment.openCount());
    }

    @Test
    void aZeroCostLegOpensNothing() {
        AlternativePayment.Result result = AlternativePayment.reserve(
            UUID.randomUUID(), PLAYER, ResourceUnit.LP, 0.0d, null, null,
            PaymentOpenFailurePolicy.REFUSE);
        assertEquals(AlternativePayment.Outcome.RESERVED, result.outcome());
        assertNull(result.leg());
        assertEquals(0, AlternativePayment.openCount());
    }

    @Test
    void logoutDropsHeldLegsWithoutRefunding() {
        UUID attempt = UUID.randomUUID();
        FakePool lp = new FakePool(ResourceUnit.LP, 500.0d);
        AlternativePayment.reserve(attempt, PLAYER, ResourceUnit.LP, 100.0d, lp, VERIFIED_LP,
            PaymentOpenFailurePolicy.REFUSE);

        AlternativePayment.forgetPlayer(PLAYER);
        assertEquals(0, AlternativePayment.openCount());
        assertEquals(0, lp.credits);
    }
}
