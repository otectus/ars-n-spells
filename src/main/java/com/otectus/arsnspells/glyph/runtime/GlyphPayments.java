package com.otectus.arsnspells.glyph.runtime;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.CastLedger;
import com.otectus.arsnspells.casting.PaymentRecovery;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.contract.StandardQuotePolicy;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Prices and moves the imported (Iron's-origin) leg of a cast with imported glyphs.
 *
 * <p>One price per execution, from {@link StandardQuotePolicy} with Iron's mana as the origin and
 * reusable-carrier semantics: the active mana mode routes it exactly as an ANS cross-cast of that
 * Iron's spell (Iron's pool in {@code iss_primary}, converted to Ars mana in {@code ars_primary},
 * the shared route in {@code hybrid}, the dual-cost split in {@code separate}, Iron's mana with no
 * conversion in {@code disabled}), and {@code cross_cast_cost_multiplier} is applied exactly once.
 * The Ars leg is never re-priced here: Ars and ANS price the form and native effects as they always
 * have, and imported effects contribute nothing to it.
 *
 * <p>Resources move only through {@link CastLedger}: a reservation debits now and is either
 * committed (the effect ran) or released (refunded) exactly once.
 */
public final class GlyphPayments {

    private GlyphPayments() {
    }

    /** A held reservation for one imported execution. */
    public static final class Reservation {
        final CastAttempt attempt;
        final ResourceAccess access;
        final CostQuote quote;
        boolean closed;

        Reservation(CastAttempt attempt, ResourceAccess access, CostQuote quote) {
            this.attempt = attempt;
            this.access = access;
            this.quote = quote;
        }

        public CostQuote quote() {
            return quote;
        }

        public boolean closed() {
            return closed;
        }
    }

    /** Why a reservation could not be made, for the caster's message. */
    public record Shortage(ResourceUnit unit, double required, double available) {}

    public static CostQuote quote(ServerPlayer player, double ironsCost, CostRules rules) {
        double arsMax = BridgeManager.getNativeArsBridge().transactionMax(player);
        var irons = BridgeManager.getNativeIronsBridge();
        double ironsMax = irons == null ? arsMax : irons.transactionMax(player);
        return StandardQuotePolicy.INSTANCE.quote(new ResourceAmount(ResourceUnit.IRONS_MANA, Math.max(0.0, ironsCost)),
            rules, CarrierPolicy.REUSABLE_BOOK_SEMANTICS, arsMax, ironsMax);
    }

    /**
     * Reserve {@code quote} for {@code player}. Returns null and fills {@code shortageOut[0]} when it
     * cannot be paid; nothing is held in that case.
     */
    @Nullable
    public static Reservation reserve(ServerPlayer player, CostQuote quote, String label, long ttlTicks,
                                      Shortage[] shortageOut) {
        if (!PaymentRecovery.available() || CastLedger.blocksPayment(player)) {
            shortageOut[0] = new Shortage(ResourceUnit.IRONS_MANA, Double.NaN, Double.NaN);
            return null;
        }
        ResourceAccess access = CastLedger.forPlayer(player);
        for (ResourceAmount leg : quote.legs()) {
            double available = access.current(player.getUUID(), leg.unit());
            if (leg.amount() > 0 && available + 1.0e-6 < leg.amount()) {
                shortageOut[0] = new Shortage(leg.unit(), leg.amount(), available);
                return null;
            }
        }
        CastAttempt attempt = CastLedger.open(player.getUUID(), label, 0, quote, player.level().getGameTime(), ttlTicks);
        CastLedger.reserve(attempt, access);
        if (!attempt.paymentAccepted()) {
            CastLedger.fail(attempt, access);
            ResourceAmount first = quote.legs().isEmpty() ? new ResourceAmount(ResourceUnit.IRONS_MANA, 0) : quote.legs().get(0);
            shortageOut[0] = new Shortage(first.unit(), first.amount(), access.current(player.getUUID(), first.unit()));
            return null;
        }
        return new Reservation(attempt, access, quote);
    }

    /** The effect is about to run: the reservation becomes payment. Idempotent. */
    public static void commit(Reservation reservation) {
        if (reservation == null || reservation.closed) return;
        reservation.closed = true;
        CastLedger.commitAndComplete(reservation.attempt);
    }

    /** Nothing ran: refund what was held. Idempotent. */
    public static void release(Reservation reservation) {
        if (reservation == null || reservation.closed) return;
        reservation.closed = true;
        CastLedger.cancel(reservation.attempt, reservation.access);
    }
}
