package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.contract.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativePaymentBoundaryTest {
    static final UUID PLAYER = UUID.randomUUID();
    static class Pool implements ResourceAccess {
        final Map<ResourceUnit, Double> values = new EnumMap<>(ResourceUnit.class);
        double cap = Double.MAX_VALUE;
        boolean floats, throwDebit, falseDebit, throwCredit, unknownAfter, readingFails, unavailable;
        double debitScale = 1, creditScale = 1;
        ResourceUnit fault = ResourceUnit.IRONS_MANA;
        int debits, credits;
        Pool(double ars, double irons) { values.put(ResourceUnit.ARS_MANA, ars); values.put(ResourceUnit.IRONS_MANA, irons); }
        public double current(UUID player, ResourceUnit unit) {
            if (readingFails || unavailable) throw new IllegalStateException("Unavailable");
            return values.get(unit);
        }
        public double max(UUID player, ResourceUnit unit) { return cap; }
        public double expectedAfterDebit(ResourceUnit unit, double before, double amount) {
            return floats && unit == ResourceUnit.IRONS_MANA ? (double)((float)before - (float)amount) : before - amount;
        }
        public double debit(UUID player, ResourceUnit unit, double amount) {
            debits++;
            double before = values.get(unit);
            double after = expectedAfterDebit(unit, before, amount * (unit == fault ? debitScale : 1));
            values.put(unit, after);
            if (unit == fault && unknownAfter) readingFails = true;
            if (unit == fault && throwDebit) throw new IllegalStateException("Mutation then exception");
            return unit == fault && falseDebit ? 0 : before - after;
        }
        public double credit(UUID player, ResourceUnit unit, double amount) {
            credits++;
            double before = values.get(unit), after = Math.min(cap, before + amount * creditScale);
            values.put(unit, after);
            if (throwCredit) throw new IllegalStateException("Credit then exception");
            return after - before;
        }
    }
    static CostQuote quote(double ars, double irons) {
        return new CostQuote(new ResourceAmount(ResourceUnit.ARS_MANA, ars), List.of(),
            List.of(new ResourceAmount(ResourceUnit.ARS_MANA, ars), new ResourceAmount(ResourceUnit.IRONS_MANA, irons)), 1);
    }
    static NativePayment.Result pay(Pool pool, AttemptLedger ledger, double ars, double irons) {
        return NativePayment.settle(PLAYER, quote(ars, irons), pool, ledger, 0);
    }
    @Test void doubleArsBalancesPreserveOneManaDebits() {
        for (double balance : new double[]{1000,32768.001,65536.002,32767.999,65535.999}) {
            Pool pool = new Pool(balance, 100);
            assertTrue(pay(pool, new AttemptLedger(), 1, 0).paid());
            assertEquals(balance - 1, pool.values.get(ResourceUnit.ARS_MANA));
        }
    }
    @Test void floatOperationIsVerifiedAtBalancePrecisionWithoutFreeCasts() {
        for (float before : new float[]{32767.99f,32768.01f,65535.99f,65536.01f}) {
            Pool pool = new Pool(100, before); pool.floats = true;
            assertTrue(pay(pool, new AttemptLedger(), 0, 1).paid());
            assertEquals((double)(before-1f), pool.values.get(ResourceUnit.IRONS_MANA));
        }
        Pool pool = new Pool(100, 33554432); pool.floats = true;
        assertEquals(TransactionSnapshot.Reason.PRECISION_LIMIT, pay(pool, new AttemptLedger(), 0, .25).reason());
        assertEquals(0, pool.debits);
    }
    @Test void exactFundsZeroAndSlightlyInsufficient() {
        Pool exact = new Pool(1, 1);
        assertTrue(pay(exact, new AttemptLedger(), 1, 1).paid());
        assertEquals(0d, exact.values.get(ResourceUnit.ARS_MANA));
        Pool shortPool = new Pool(.999999, 1);
        assertFalse(pay(shortPool, new AttemptLedger(), 1, 1).paid()); assertEquals(0, shortPool.debits);
        assertTrue(pay(shortPool, new AttemptLedger(), 0, 0).paid()); assertEquals(0, shortPool.debits);
    }
    @Test void secondLegMutationThenExceptionOrFalseCompensatesBothObservedDebits() {
        for (boolean exception : new boolean[]{true,false}) {
            Pool pool = new Pool(100, 100); pool.throwDebit = exception; pool.falseDebit = !exception;
            AttemptLedger ledger = new AttemptLedger();
            assertFalse(pay(pool, ledger, 10, 20).paid());
            assertEquals(100d, pool.values.get(ResourceUnit.ARS_MANA)); assertEquals(100d, pool.values.get(ResourceUnit.IRONS_MANA));
            assertEquals(0, ledger.openCount()); assertEquals(2, pool.credits);
        }
    }
    @Test void partialExcessAndVetoedDebitsNeverProducePaidResult() {
        for (double scale : new double[]{0,.5,2}) {
            Pool pool = new Pool(100,100); pool.debitScale = scale;
            assertFalse(pay(pool, new AttemptLedger(), 10,20).paid());
            assertEquals(100d,pool.values.get(ResourceUnit.IRONS_MANA));
        }
    }
    @Test void partialRefundRemainsOwedAndDuplicateCancellationOnlyPaysRemainder() {
        Pool pool = new Pool(100,100); pool.throwDebit = true; pool.creditScale = .5;
        AttemptLedger ledger = new AttemptLedger();
        var result = pay(pool,ledger,10,20);
        assertFalse(result.remaining().isEmpty()); assertEquals(1,ledger.openCount());
        CastAttempt attempt = ledger.openFor(PLAYER).get(0);
        assertFalse(attempt.isReleased());
        pool.unavailable = true; ledger.cancel(attempt,pool); assertFalse(attempt.isReleased());
        pool.unavailable = false; pool.creditScale = 1; pool.throwCredit = true;
        ledger.cancel(attempt,pool);
        assertTrue(attempt.isReleased()); assertEquals(100d,pool.values.get(ResourceUnit.ARS_MANA));
        assertEquals(100d,pool.values.get(ResourceUnit.IRONS_MANA));
        int credits=pool.credits; ledger.cancel(attempt,pool); assertEquals(credits,pool.credits);
    }
    @Test void unknownMutationIsQuarantinedAndKnownFirstLegStillRefunded() {
        Pool pool=new Pool(100,100); pool.unknownAfter=true;
        AttemptLedger ledger=new AttemptLedger(); var result=pay(pool,ledger,10,20);
        assertTrue(result.unknown().contains(ResourceUnit.IRONS_MANA));
        pool.readingFails=false;
        var attempt=ledger.openFor(PLAYER).get(0); ledger.cancel(attempt,pool);
        assertEquals(100d,pool.values.get(ResourceUnit.ARS_MANA));
        assertEquals(80d,pool.values.get(ResourceUnit.IRONS_MANA)); assertFalse(attempt.isReleased());
    }
    @Test void restoredObligationRetriesOnlyVerifiedRemainingAmount() {
        AttemptLedger ledger=new AttemptLedger(); UUID id=UUID.randomUUID();
        ledger.restore(id,PLAYER,List.of(new ResourceAmount(ResourceUnit.ARS_MANA,5)),Set.of(ResourceUnit.IRONS_MANA));
        Pool pool=new Pool(95,80); var attempt=ledger.openFor(PLAYER).get(0);
        ledger.cancel(attempt,pool); ledger.cancel(attempt,pool);
        assertEquals(100d,pool.values.get(ResourceUnit.ARS_MANA)); assertEquals(80d,pool.values.get(ResourceUnit.IRONS_MANA));
        assertFalse(attempt.isReleased()); assertEquals(1,pool.credits);
    }
    @Test void invalidBalancesRefuseBeforeAnyMutation() {
        for (double value : new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1}) {
            Pool pool=new Pool(100,value); pool.cap=1000;
            assertFalse(pay(pool,new AttemptLedger(),10,20).paid()); assertEquals(0,pool.debits);
        }
        assertThrows(IllegalArgumentException.class,()->quote(Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->quote(-1,0));
        assertThrows(IllegalArgumentException.class,()->quote(Double.POSITIVE_INFINITY,0));
    }

    /**
     * The loader adapter's rules over a pool that behaves like Iron's and Ars's: every write is
     * clamped to the ceiling, and a credit into a full pool writes nothing.
     */
    static class NativePool extends Pool {
        NativePool(double ars, double irons) { super(ars, irons); }
        @Override public double debit(UUID player, ResourceUnit unit, double amount) {
            double before = values.get(unit);
            super.debit(player, unit, amount);
            values.put(unit, Math.min(values.get(unit), cap));
            return Math.max(0, before - values.get(unit));
        }
        @Override public double credit(UUID player, ResourceUnit unit, double amount) {
            if (values.get(unit) >= cap) { credits++; return 0; }
            return super.credit(player, unit, amount);
        }
        @Override public boolean acceptsDebit(ResourceUnit unit, ResourceMovement move) {
            return CastLedger.acceptsNativeWrite(move);
        }
    }
    @Test void balanceAboveTheCeilingIsPaidByTheNativeWrite() {
        // The 3.3.5 report: 240 in the pool, the ceiling below it at the cast, a 90-mana spell.
        NativePool pool = new NativePool(100, 240); pool.cap = 200;
        assertTrue(pay(pool, new AttemptLedger(), 0, 90).paid(), "a surplus above the ceiling must not refuse the cast");
        assertEquals(150d, pool.values.get(ResourceUnit.IRONS_MANA), "a surplus smaller than the price costs only the price");
        NativePool large = new NativePool(100, 1500); large.cap = 1000;
        var result = pay(large, new AttemptLedger(), 0, 20);
        assertTrue(result.paid());
        assertEquals(1000d, large.values.get(ResourceUnit.IRONS_MANA), "the write clamps exactly as the native cast would");
        assertEquals(500d, result.debited().stream().filter(leg -> leg.unit() == ResourceUnit.IRONS_MANA)
            .mapToDouble(ResourceAmount::amount).sum(), "the reservation records what moved");
    }
    @Test void listenerAdjustedWritesArePaidButVetoedWritesAreRefused() {
        for (double scale : new double[]{.5, 2}) {
            NativePool pool = new NativePool(100, 100); pool.debitScale = scale;
            assertTrue(pay(pool, new AttemptLedger(), 0, 20).paid(), "a listener-adjusted debit is still the native payment");
            assertEquals(100 - 20 * scale, pool.values.get(ResourceUnit.IRONS_MANA));
        }
        NativePool vetoed = new NativePool(100, 100); vetoed.debitScale = 0;
        AttemptLedger ledger = new AttemptLedger();
        var result = pay(vetoed, ledger, 0, 20);
        assertFalse(result.paid(), "a cancelled debit must not become a free spell");
        assertEquals(TransactionSnapshot.Reason.RESOURCE_CHANGED, result.reason());
        assertEquals(100d, vetoed.values.get(ResourceUnit.IRONS_MANA)); assertEquals(0, ledger.openCount());
    }
    @Test void refundThatAFullPoolCannotHoldIsReleasedInsteadOfBlockingEveryCast() {
        NativePool pool = new NativePool(100, 1000); pool.cap = 1000;
        AttemptLedger ledger = new AttemptLedger();
        CastAttempt attempt = ledger.open(PLAYER, "book", 0, quote(0, 20), 0);
        attempt.validate(); attempt.markQuoted(); ledger.reserve(attempt, pool);
        assertEquals(980d, pool.values.get(ResourceUnit.IRONS_MANA));
        pool.values.put(ResourceUnit.IRONS_MANA, 1000d); // regeneration refilled the pool before the refund
        ledger.fail(attempt, pool);
        assertFalse(attempt.isReleased(), "the refund had nowhere to go");
        assertEquals(TransactionSnapshot.Reason.INCOMPLETE_COMPENSATION, NativePayment.settle(PLAYER, quote(0, 20), pool, ledger, 0).reason(),
            "the unsettled refund blocks until the gate settles it");
        assertFalse(CastLedger.blocksPayment(ledger, PLAYER, () -> pool), "a capped refund must not lock the player out");
        assertTrue(attempt.isReleased()); assertEquals(0, ledger.openCount());
        assertEquals(1000d, pool.values.get(ResourceUnit.IRONS_MANA), "nothing is credited past the ceiling");
        assertTrue(NativePayment.settle(PLAYER, quote(0, 20), pool, ledger, 0).paid());
    }
    @Test void refundWithRoomOrUnknownMovementStillBlocksUntilSettled() {
        NativePool pool = new NativePool(100, 100); pool.throwDebit = true; pool.creditScale = 0;
        AttemptLedger ledger = new AttemptLedger();
        pay(pool, ledger, 0, 20);
        assertTrue(CastLedger.blocksPayment(ledger, PLAYER, () -> pool), "a vetoed refund into a pool with room stays owed");
        pool.creditScale = 1;
        assertFalse(CastLedger.blocksPayment(ledger, PLAYER, () -> pool), "the retry settles it once the pool accepts it");
        assertEquals(100d, pool.values.get(ResourceUnit.IRONS_MANA));
        AttemptLedger restored = new AttemptLedger();
        restored.restore(UUID.randomUUID(), PLAYER, List.of(), Set.of(ResourceUnit.IRONS_MANA));
        NativePool full = new NativePool(100, 1000); full.cap = 1000;
        assertTrue(CastLedger.blocksPayment(restored, PLAYER, () -> full), "an unknown movement is never guessed away");
    }
    @Test void lateCancellationCannotRefundCommittedEffects() {
        Pool pool=new Pool(100,100); AttemptLedger ledger=new AttemptLedger();
        CastAttempt attempt=ledger.open(PLAYER,"book",0,quote(10,20),0);
        attempt.validate();attempt.markQuoted();ledger.reserve(attempt,pool);ledger.commit(attempt);ledger.complete(attempt);
        ledger.cancel(attempt,pool); assertEquals(0,pool.credits);assertEquals(90d,pool.values.get(ResourceUnit.ARS_MANA));
    }

    @Test void reentrantCancellationWaitsForTheCurrentAfterRead() {
        AttemptLedger ledger = new AttemptLedger();
        CastAttempt[] held = new CastAttempt[1];
        Pool pool = new Pool(100,100) {
            @Override public double debit(UUID player, ResourceUnit unit, double amount) {
                double moved = super.debit(player,unit,amount);
                ledger.cancel(held[0],this);
                return moved;
            }
        };
        held[0]=ledger.open(PLAYER,"reentrant",0,quote(10,20),0);
        held[0].validate();held[0].markQuoted();
        ledger.reserve(held[0],pool);
        assertFalse(held[0].paymentAccepted());assertTrue(held[0].isReleased());
        assertEquals(100d,pool.values.get(ResourceUnit.ARS_MANA));assertEquals(100d,pool.values.get(ResourceUnit.IRONS_MANA));
        assertEquals(1,pool.debits);assertEquals(1,pool.credits);assertEquals(0,ledger.openCount());
    }
    @Test void validationFailureAfterMutationStillRecordsAndCompensatesDebit() {
        Pool pool = new Pool(100,100) {
            @Override public boolean acceptsDebit(ResourceUnit unit, ResourceMovement movement) {
                throw new IllegalStateException("Injected receipt validation failure");
            }
        };
        assertFalse(pay(pool,new AttemptLedger(),10,20).paid());
        assertEquals(100d,pool.values.get(ResourceUnit.ARS_MANA));assertEquals(1,pool.credits);
    }
    @Test void alternativeMutationFalseExceptionAndReentrantReleaseRemainObservable() {
        for (String failure : new String[]{"false","exception","cancel"}) {
            UUID id=UUID.randomUUID();
            Pool pool = new Pool(0,0) {
                @Override public double debit(UUID player, ResourceUnit unit, double amount) {
                    double moved=super.debit(player,unit,amount);
                    if(failure.equals("cancel")) {
                        assertFalse(AlternativePayment.reserve(id,PLAYER,unit,amount,this,
                            CompatibilityStatus.verified("fault-fixture"),PaymentOpenFailurePolicy.REFUSE).allowsCast());
                        assertEquals(0,AlternativePayment.commit(id));
                        AlternativePayment.release(id,this);
                    }
                    return moved;
                }
            };
            pool.values.put(ResourceUnit.LP,100d);pool.fault=ResourceUnit.LP;
            pool.falseDebit=failure.equals("false");pool.throwDebit=failure.equals("exception");
            var result=AlternativePayment.reserve(id,PLAYER,ResourceUnit.LP,20,pool,
                CompatibilityStatus.verified("fault-fixture"),PaymentOpenFailurePolicy.REFUSE);
            assertFalse(result.allowsCast());assertEquals(100d,pool.values.get(ResourceUnit.LP));
            assertNull(AlternativePayment.peek(id));assertEquals(1,pool.debits);assertEquals(1,pool.credits);
            AlternativePayment.release(id,pool);assertEquals(1,pool.credits);
        }
    }
    @Test void repeatedRefundAttemptsKeepBoundedDiagnosticsAndOnlyRemainingCredit() {
        Pool pool=new Pool(100,100);pool.throwDebit=true;pool.creditScale=0;
        AttemptLedger ledger=new AttemptLedger();pay(pool,ledger,10,20);
        CastAttempt attempt=ledger.openFor(PLAYER).get(0);
        for(int retry=0;retry<100;retry++)ledger.cancel(attempt,pool);
        assertTrue(attempt.movements().size()<=64);assertFalse(attempt.isReleased());
        pool.creditScale=1;ledger.cancel(attempt,pool);
        assertEquals(100d,pool.values.get(ResourceUnit.ARS_MANA));assertEquals(100d,pool.values.get(ResourceUnit.IRONS_MANA));
    }

}
