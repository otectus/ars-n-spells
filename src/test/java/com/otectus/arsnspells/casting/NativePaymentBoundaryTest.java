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
    @Test void ceilingAndInvalidBalancesRefuseBeforeAnyMutation() {
        for (double value : new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,1500}) {
            Pool pool=new Pool(100,value); pool.cap=1000;
            assertFalse(pay(pool,new AttemptLedger(),10,20).paid()); assertEquals(0,pool.debits);
        }
        assertThrows(IllegalArgumentException.class,()->quote(Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->quote(-1,0));
        assertThrows(IllegalArgumentException.class,()->quote(Double.POSITIVE_INFINITY,0));
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
