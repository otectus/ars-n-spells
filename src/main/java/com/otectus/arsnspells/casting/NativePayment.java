package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.contract.*;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Final-unit payment, observed in the native pool's arithmetic before any effect. */
public final class NativePayment {
    private NativePayment() {}
    /**
     * @param required  the failing leg's price, NaN when no single leg failed
     * @param available that leg's balance as observed at the refusal, NaN when unobserved
     */
    public record Result(UUID id, boolean paid, List<ResourceAmount> debited, List<ResourceAmount> refunded,
                         TransactionSnapshot.Reason reason, ResourceUnit failureUnit,
                         List<ResourceAmount> remaining, Set<ResourceUnit> unknown, List<ResourceMovement> movements,
                         double required, double available) {
        /** A genuine shortage, as opposed to an adapter, identity or settlement failure. */
        public boolean shortage() {
            return !paid && reason == TransactionSnapshot.Reason.INSUFFICIENT_RESOURCE;
        }
    }
    public static Result settle(Player player, CostQuote quote) {
        return settle(player.getUUID(), quote, CastLedger.forPlayer(player), CastLedger.ledger(), player.level().getGameTime());
    }
    public static Result hold(Player player, CostQuote quote) {
        if (!PaymentRecovery.available() || CastLedger.ledger().openCount() >= 4096)
            return refused(UUID.randomUUID(), TransactionSnapshot.Reason.INCOMPLETE_COMPENSATION, quote.origin().unit());
        return settle(player.getUUID(), quote, CastLedger.forPlayer(player), CastLedger.ledger(), player.level().getGameTime(), false);
    }
    public static void effectStarted(Result result) {
        if (result == null || !result.paid()) return;
        CastLedger.ledger().allOpen().stream().filter(a -> a.attemptId().equals(result.id())).findFirst()
            .ifPresentOrElse(CastLedger::commitAndComplete, () -> { throw new IllegalStateException("Accepted payment reservation disappeared before effect entry"); });
    }
    public static Result abortBeforeEffect(Player player, Result result) {
        return abortBeforeEffect(player, result, TransactionSnapshot.Reason.NATIVE_FAILURE);
    }
    /** Refund a payment whose effect never started, recording why. */
    public static Result abortBeforeEffect(Player player, Result result, TransactionSnapshot.Reason reason) {
        if (result == null || !result.paid()) return result;
        var attempt = CastLedger.ledger().allOpen().stream().filter(a -> a.attemptId().equals(result.id())).findFirst();
        if (attempt.isEmpty()) return result;
        var refunds = CastLedger.fail(attempt.get(), CastLedger.forPlayer(player));
        return new Result(result.id(), false, result.debited(), refunds, reason,
            result.failureUnit(), attempt.get().remainingRefunds(), attempt.get().unknownUnits(), attempt.get().movements(),
            Double.NaN, Double.NaN);
    }
    public static Result settle(UUID player, CostQuote quote, ResourceAccess access, AttemptLedger ledger, long tick) {
        return settle(player, quote, access, ledger, tick, true);
    }
    private static Result settle(UUID player, CostQuote quote, ResourceAccess access, AttemptLedger ledger, long tick, boolean complete) {
        UUID id = UUID.randomUUID();
        if (ledger.openFor(player).stream().anyMatch(a -> a.state().isTerminal() && !a.isReleased()))
            return refused(id, TransactionSnapshot.Reason.INCOMPLETE_COMPENSATION, quote.origin().unit());
        for (ResourceAmount leg : quote.legs()) {
            if (leg.amount() == 0) continue;
            double before = Double.NaN;
            try {
                access.prepare(player, leg.unit());
                before = access.current(player, leg.unit());
                double max = access.max(player, leg.unit());
                TransactionSnapshot.Reason reason = null;
                if (!Double.isFinite(before) || !Double.isFinite(max) || before < 0 || max < 0)
                    reason = TransactionSnapshot.Reason.ADAPTER_UNAVAILABLE;
                else if (before > max) reason = TransactionSnapshot.Reason.CEILING_INCONSISTENT;
                else if (before < leg.amount()) reason = TransactionSnapshot.Reason.INSUFFICIENT_RESOURCE;
                else if (leg.amount() > 0 && (!Double.isFinite(access.expectedAfterDebit(leg.unit(), before, leg.amount())) || !(access.expectedAfterDebit(leg.unit(), before, leg.amount()) < before)))
                    reason = TransactionSnapshot.Reason.PRECISION_LIMIT;
                if (reason != null) return refused(id, reason, leg.unit(), leg.amount(), before);
            } catch (RuntimeException error) {
                return refused(id, TransactionSnapshot.Reason.ADAPTER_UNAVAILABLE, leg.unit(), leg.amount(), before);
            }
        }
        CastAttempt attempt = ledger.open(player, "native-payment:" + id, 0, quote, tick);
        attempt.validate(); attempt.markQuoted();
        List<ResourceAmount> debited = ledger.reserve(attempt, access);
        if (!attempt.paymentAccepted()) {
            ResourceMovement failed = attempt.movements().get(attempt.movements().size() - 1);
            var reason = failed.error() == null ? TransactionSnapshot.Reason.RESOURCE_CHANGED : TransactionSnapshot.Reason.SETTLEMENT_EXCEPTION;
            List<ResourceAmount> refunds = ledger.fail(attempt, access);
            return new Result(attempt.attemptId(), false, debited, refunds, reason,
                quote.legs().get(debited.size() - 1).unit(), attempt.remainingRefunds(), attempt.unknownUnits(), attempt.movements(),
                failed.requested(), failed.before());
        }
        if (complete) { ledger.commit(attempt); ledger.complete(attempt); }
        return new Result(attempt.attemptId(), true, debited, List.of(), TransactionSnapshot.Reason.NONE,
            quote.origin().unit(), List.of(), Set.of(), attempt.movements(), Double.NaN, Double.NaN);
    }
    public static Result refused(UUID id, TransactionSnapshot.Reason reason, ResourceUnit unit) {
        return refused(id, reason, unit, Double.NaN, Double.NaN);
    }
    public static Result refused(UUID id, TransactionSnapshot.Reason reason, ResourceUnit unit, double required, double available) {
        return new Result(id, false, List.of(), List.of(), reason, unit, List.of(), Set.of(), List.of(), required, available);
    }
}
