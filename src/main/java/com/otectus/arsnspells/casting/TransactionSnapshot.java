package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceUnit;
import java.util.*;

/** Bounded server result for the last native cast. No client-supplied cost or display strings. */
public record TransactionSnapshot(UUID attemptId, ResourceUnit origin, boolean crossCast, Stage stage,
                                  List<ResourceAmount> quoted, List<ResourceAmount> paid, List<ResourceAmount> refunded,
                                  Reason reason, ResourceUnit failureUnit) {
    public enum Stage { RESERVED, COMMITTED, REFUNDED, REFUSED }
    public enum Reason { NONE, INSUFFICIENT_RESOURCE, CANCELLED, NATIVE_FAILURE, ADAPTER_UNAVAILABLE, RESOURCE_CHANGED, PRECISION_LIMIT, CEILING_INCONSISTENT, SETTLEMENT_EXCEPTION, INCOMPLETE_COMPENSATION, CONFIG_CHANGED, IDENTITY_CHANGED, DUPLICATE_EFFECT, INVALID_COST, NATIVE_VETO }
    public TransactionSnapshot {
        Objects.requireNonNull(attemptId);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(stage);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(failureUnit);
        quoted = validate(quoted);
        paid = validate(paid);
        refunded = validate(refunded);
    }
    private static List<ResourceAmount> validate(List<ResourceAmount> amounts) {
        if (amounts == null || amounts.size() > ResourceUnit.values().length) throw new IllegalArgumentException("Invalid resource list");
        EnumSet<ResourceUnit> units = EnumSet.noneOf(ResourceUnit.class);
        for (ResourceAmount amount : amounts) {
            if (amount == null || amount.amount() > Integer.MAX_VALUE || !units.add(amount.unit())) {
                throw new IllegalArgumentException("Invalid transaction amount");
            }
        }
        return List.copyOf(amounts);
    }
}
