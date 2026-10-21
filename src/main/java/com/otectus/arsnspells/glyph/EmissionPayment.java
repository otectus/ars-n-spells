package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.CastLedger;
import com.otectus.arsnspells.casting.NativePayment;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.contract.StandardQuotePolicy;
import net.minecraft.server.level.ServerPlayer;

/**
 * The payment for one execution of an imported glyph, taken at the execution's effect boundary.
 *
 * <p>Iron's spells are priced in Iron's mana: {@code StandardQuotePolicy} turns the Iron's-origin
 * cost into the paying pool(s) of the current mode with the cross-cast multiplier applied once,
 * exactly as an Iron's spell cast from an ANS carrier is priced. The rules snapshot is the
 * session's, taken when the Ars cast was admitted, so a reload mid-flight cannot check one mode
 * and charge another.
 *
 * <p>The first execution of each imported glyph redeems the reservation taken when the Ars cast
 * was admitted. When Iron's own cost event changed the price since then, or for a later
 * execution (a split, a burst, a lingering zone), the price is settled fresh at the effect
 * boundary and refused if unaffordable, so every execution is authorized and paid before its
 * effect and none is prepaid on another's behalf. An execution refused before its effect gives
 * back what it held; nothing is ever refunded after an effect has started.
 */
public final class EmissionPayment {
    private final ServerPlayer player;
    private final GlyphCastSession session;
    private final GlyphCastSession.Occurrence occurrence;
    private boolean paid;
    private boolean released;
    private CostQuote settled;
    private NativePayment.Result shortage;

    EmissionPayment(ServerPlayer player, GlyphCastSession session, GlyphCastSession.Occurrence occurrence) {
        this.player = player;
        this.session = session;
        this.occurrence = occurrence;
    }

    /** The Iron's-origin quote for {@code ironsCost} under this session's rules. */
    public static CostQuote quote(ServerPlayer player, CostRules rules, double ironsCost) {
        double arsMax = BridgeManager.getNativeArsBridge().transactionMax(player);
        var irons = BridgeManager.getNativeIronsBridge();
        double ironsMax = irons == null ? arsMax : irons.transactionMax(player);
        return StandardQuotePolicy.INSTANCE.quote(new ResourceAmount(ResourceUnit.IRONS_MANA, Math.max(0, ironsCost)),
            rules, CarrierPolicy.REUSABLE_BOOK_SEMANTICS, arsMax, ironsMax);
    }

    /**
     * Pay for this execution now, right before its effect.
     *
     * @param finalIronsCost the cost Iron's own cost event settled on, in Iron's units
     * @param free           true for a native recast or a creative caster Iron's does not charge
     * @return whether the effect may run
     */
    public boolean payAtEffect(double finalIronsCost, boolean free) {
        if (paid) return true;
        CastAttempt reservation = firstReservation();
        if (free) {
            if (reservation != null) CastLedger.cancel(reservation, CastLedger.forPlayer(player));
            markPaid(null);
            return true;
        }
        CostQuote finalQuote = quote(player, session.rules, finalIronsCost);
        if (reservation != null && sameLegs(reservation.quote(), finalQuote)) {
            CastLedger.commitAndComplete(reservation);
            markPaid(finalQuote);
            return true;
        }
        // The price moved (an Iron's cost listener, an equipment change) or this is a later
        // execution: give back what was held and settle the real price.
        if (reservation != null) CastLedger.cancel(reservation, CastLedger.forPlayer(player));
        if (finalQuote.isFree()) {
            markPaid(finalQuote);
            return true;
        }
        NativePayment.Result result = NativePayment.hold(player, finalQuote);
        if (!result.paid()) {
            shortage = result;
            occurrence.redeemed = true;
            return false;
        }
        NativePayment.effectStarted(result);
        markPaid(finalQuote);
        return true;
    }

    /**
     * Hand the first execution's reservation back before Iron's own cast pipeline prices a
     * charged or channelled child, which then pays each invocation at Iron's native write.
     */
    public void releaseForNativePipeline() {
        CastAttempt reservation = firstReservation();
        if (reservation != null) CastLedger.cancel(reservation, CastLedger.forPlayer(player));
        occurrence.redeemed = true;
        released = true;
    }

    /** Refused before any effect: return a held reservation. Idempotent; never refunds a paid execution. */
    public void releaseIfUnpaid() {
        if (paid || released) return;
        released = true;
        CastAttempt reservation = firstReservation();
        if (reservation != null) {
            CastLedger.cancel(reservation, CastLedger.forPlayer(player));
            occurrence.redeemed = true;
        }
    }

    public boolean paid() { return paid; }
    public CostQuote settled() { return settled; }
    public NativePayment.Result shortage() { return shortage; }

    private CastAttempt firstReservation() {
        CastAttempt reservation = occurrence.reservation;
        if (occurrence.redeemed || reservation == null || reservation.state().isTerminal()) return null;
        return reservation;
    }

    private void markPaid(CostQuote quote) {
        paid = true;
        settled = quote;
        occurrence.redeemed = true;
    }

    private static boolean sameLegs(CostQuote reserved, CostQuote now) {
        if (reserved.legs().size() != now.legs().size()) return false;
        for (int i = 0; i < reserved.legs().size(); i++) {
            ResourceAmount a = reserved.legs().get(i), b = now.legs().get(i);
            if (a.unit() != b.unit() || Math.abs(a.amount() - b.amount()) > 1.0e-6) return false;
        }
        return true;
    }
}
