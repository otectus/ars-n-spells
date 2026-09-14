package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.contract.*;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Measures every final-unit debit and rolls a changed/short native pool back before effects. */
public final class NativePayment {
    private NativePayment() {}
    public record Result(UUID id, boolean paid, List<ResourceAmount> debited, List<ResourceAmount> refunded,
                         TransactionSnapshot.Reason reason, ResourceUnit failureUnit) {}
    public static Result settle(Player player, CostQuote quote) {
        for (ResourceAmount leg : quote.legs()) {
            if (BridgeManager.getNativeBridge(leg.unit()).getMana(player) < leg.amount()) {
                return new Result(UUID.randomUUID(), false, List.of(), List.of(),
                    TransactionSnapshot.Reason.INSUFFICIENT_RESOURCE, leg.unit());
            }
        }
        ResourceAccess access = CastLedger.forPlayer(player);
        CastAttempt attempt = CastLedger.open(player.getUUID(), "native-payment:" + UUID.randomUUID(), 0,
            quote, player.level().getGameTime());
        List<ResourceAmount> debited = CastLedger.reserve(attempt, access);
        for (ResourceAmount leg : quote.legs()) {
            double actual = debited.stream().filter(value -> value.unit() == leg.unit()).mapToDouble(ResourceAmount::amount).sum();
            if (Math.abs(actual - leg.amount()) > Math.max(.001, Math.ulp((float) leg.amount()) * 2)) {
                return new Result(attempt.attemptId(), false, debited, CastLedger.fail(attempt, access),
                    TransactionSnapshot.Reason.RESOURCE_CHANGED, leg.unit());
            }
        }
        CastLedger.commitAndComplete(attempt);
        return new Result(attempt.attemptId(), true, debited, List.of(), TransactionSnapshot.Reason.NONE, quote.origin().unit());
    }
}
