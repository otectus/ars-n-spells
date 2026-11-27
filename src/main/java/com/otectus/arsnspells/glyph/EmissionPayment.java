package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.AlternativePayment;
import com.otectus.arsnspells.casting.ArsCastPayments;
import com.otectus.arsnspells.casting.CastLedger;
import com.otectus.arsnspells.casting.NativePayment;
import com.otectus.arsnspells.compat.AlternativeResourceAccess;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CompatibilityStatus;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.CostRules;
import com.otectus.arsnspells.contract.PaymentOpenFailurePolicy;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.contract.StandardQuotePolicy;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

/**
 * The payment for one execution of an imported glyph, taken at the execution's effect boundary.
 *
 * <p><b>Routes (3.4.1 review PAYMENT-01).</b> Each occurrence pays through the {@link GlyphRoute}
 * admission fixed for it, which is the resource native Iron's casting would take from this caster:
 * <ul>
 *   <li>{@link GlyphRoute#MANA}: Iron's-origin mana, which {@code StandardQuotePolicy} turns into the
 *       paying pool(s) of the session's mana mode with the cross-cast multiplier applied once, exactly
 *       as an Iron's spell cast from an ANS carrier is priced;</li>
 *   <li>{@link GlyphRoute#LP}: Blood Magic LP at native casting's own LP formula, through the same
 *       alternative leg native casting uses;</li>
 *   <li>{@link GlyphRoute#AURA_NATIVE}: nothing here. Covenant of the Seven takes the aura at Iron's
 *       pre-cast event, which the execution posts, as it does for a native cast.</li>
 * </ul>
 * The rules snapshot is the session's, taken when the Ars cast was admitted, so a reload mid-flight
 * cannot check one mode and charge another.
 *
 * <p>The first execution of each imported glyph redeems what was reserved for it at admission. When
 * Iron's own cost event changed the price since then, or for a later execution (a split, a burst, a
 * lingering zone), the price is settled fresh at the effect boundary and refused if unaffordable, so
 * every execution is authorized and paid before its effect and none is prepaid on another's behalf.
 * An execution refused before its effect gives back what it held; nothing is ever refunded after an
 * effect has started.
 */
public final class EmissionPayment {
    private final ServerPlayer player;
    private final GlyphCastSession session;
    private final GlyphCastSession.Occurrence occurrence;
    private boolean paid;
    private boolean released;
    private CostQuote settled;
    private String described = "nothing";
    private NativePayment.Result shortage;
    private Component refusal;

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

    // ---- LP route ---------------------------------------------------------------------------

    /**
     * Reserve the first execution's LP at admission, as native casting reserves a Cursed-Ring cast:
     * held when affordable; with insufficient LP, the configured death option admits it unpaid, the
     * native fallback policy moves the occurrence to the mana route, and otherwise it is refused.
     *
     * @return null when admitted (possibly re-routed to mana), else the refusal to show
     */
    @Nullable
    static Component admitLp(ServerPlayer player, GlyphCastSession.Occurrence occurrence, double lp, Component spellName) {
        if (!Double.isFinite(lp)) return Component.translatable("message.ars_n_spells.glyph.dormant.spell_missing", spellName);
        if (AlternativePayment.pending(player.getUUID())) return Component.translatable("message.ars_n_spells.cast.payment_failed");
        ResourceAccess access = lpAccess(player);
        CompatibilityStatus status = AlternativeResourceAccess.lpStatus();
        PaymentOpenFailurePolicy policy = AnsConfig.getPaymentOpenFailurePolicy();
        UUID id = UUID.randomUUID();
        var result = AlternativePayment.reserve(id, player.getUUID(), ResourceUnit.LP, lp, access, status, policy);
        if (result.allowsCast()) {
            occurrence.lpLeg = result.leg() == null ? null : id;
            occurrence.lpReserved = result.leg() == null ? 0 : result.leg().reserved();
            if (occurrence.lpLeg != null) markLpLeg(player, id);
            return null;
        }
        if (result.leg() != null) return Component.translatable("message.ars_n_spells.cast.payment_failed");
        if (status.isUsable() && AnsConfig.DEATH_ON_INSUFFICIENT_LP.get()) {
            occurrence.deathPenalty = true;
            return null;
        }
        if (policy == PaymentOpenFailurePolicy.NATIVE_FALLBACK) {
            occurrence.route = GlyphRoute.MANA;
            return null;
        }
        return lpShortage(player, access, status, spellName, lp);
    }

    /** Give an unspent LP leg back (the session closed). */
    static void releaseLp(GlyphCastSession.Occurrence occurrence, UUID owner, @Nullable ServerPlayer live, @Nullable MinecraftServer server) {
        UUID leg = occurrence.lpLeg;
        if (leg == null) return;
        occurrence.lpLeg = null;
        AlternativePayment.release(leg);
    }

    /** Mark an alternative leg as an imported glyph's, so a timeline reader can tell it from the Ars leg. */
    private static void markLpLeg(ServerPlayer player, UUID attempt) {
        com.otectus.arsnspells.casting.PaymentTimeline.record(player.getUUID(), -1,
            com.otectus.arsnspells.casting.PaymentTimeline.Kind.EMISSION, "glyph-lp:" + attempt, "imported LP leg");
    }

    private static ResourceAccess lpAccess(ServerPlayer player) {
        return AlternativeResourceAccess.lp(id -> id.equals(player.getUUID()) ? player : null);
    }

    private static Component lpShortage(ServerPlayer player, ResourceAccess access, CompatibilityStatus status, Component spellName, double lp) {
        Component unit = Component.translatable("ars_n_spells.resource.lp");
        if (!status.isUsable())
            return Component.translatable("message.ars_n_spells.glyph.refused.lp_unavailable", spellName, status.reason());
        double have;
        try {
            have = access.current(player.getUUID(), ResourceUnit.LP);
        } catch (RuntimeException unreadable) {
            have = Double.NaN;
        }
        if (!Double.isFinite(have))
            return Component.translatable("message.ars_n_spells.cast.insufficient_required", unit, spellName,
                com.otectus.arsnspells.casting.PaymentMessages.amount(lp));
        return Component.translatable("message.ars_n_spells.cast.insufficient", unit, spellName,
            com.otectus.arsnspells.casting.PaymentMessages.amount(lp), com.otectus.arsnspells.casting.PaymentMessages.amount(Math.max(0, have)));
    }

    // ---- The effect boundary ------------------------------------------------------------------

    /** {@link #payAtEffect(double, int, boolean)} at the occurrence's native level. */
    public boolean payAtEffect(double finalIronsCost, boolean free) {
        return payAtEffect(finalIronsCost, occurrence.nativeLevel, free);
    }

    /**
     * Pay for this execution now, right before its effect.
     *
     * @param finalIronsCost the cost Iron's own cost event settled on, in Iron's units
     * @param finalLevel     the level Iron's cost event settled on (the LP formula depends on it)
     * @param free           true for a native recast or a creative caster Iron's does not charge
     * @return whether the effect may run
     */
    public boolean payAtEffect(double finalIronsCost, int finalLevel, boolean free) {
        if (paid) return true;
        if (free) {
            releaseHeld();
            markPaid(null, "free");
            return true;
        }
        return switch (occurrence.route) {
            case AURA_NATIVE -> {
                // Audit ANS-001: paid only on Covenant's receipt. Covenant re-checks the caster's
                // equipment at the pre-cast event and may have charged nothing; without the receipt
                // the execution is refused, never marked paid and never repriced into mana or LP.
                releaseHeld();
                ImportedExecutionScope.Frame frame = ImportedExecutionScope.current(player);
                if (frame == null || frame.route() != GlyphRoute.AURA_NATIVE || !frame.auraCharged()) {
                    occurrence.redeemed = true;
                    refusal = Component.translatable("message.ars_n_spells.glyph.refused.aura_not_charged",
                        occurrence.definition.spellName() + " " + occurrence.definition.levelNumeral());
                    yield false;
                }
                markPaid(null, "aura (Covenant receipt)");
                yield true;
            }
            case LP -> payLp(finalIronsCost, finalLevel);
            case MANA -> payMana(finalIronsCost);
        };
    }

    private boolean payMana(double finalIronsCost) {
        CastAttempt reservation = firstReservation();
        CostQuote finalQuote = quote(player, session.rules, finalIronsCost);
        if (reservation != null && sameLegs(reservation.quote(), finalQuote)) {
            CastLedger.commitAndComplete(reservation);
            markPaid(finalQuote, finalQuote.legs().toString());
            return true;
        }
        // The price moved (an Iron's cost listener, an equipment change) or this is a later
        // execution: give back what was held and settle the real price.
        if (reservation != null) CastLedger.cancel(reservation, CastLedger.forPlayer(player));
        if (finalQuote.isFree()) {
            markPaid(finalQuote, "free");
            return true;
        }
        NativePayment.Result result = NativePayment.hold(player, finalQuote);
        if (!result.paid()) {
            shortage = result;
            occurrence.redeemed = true;
            return false;
        }
        NativePayment.effectStarted(result);
        markPaid(finalQuote, finalQuote.legs().toString());
        return true;
    }

    private boolean payLp(double finalIronsCost, int finalLevel) {
        double finalLp = IronGlyphBridges.get().lpCost(player, occurrence.definition.spell(), finalLevel, finalIronsCost);
        ResourceAccess access = lpAccess(player);
        UUID held = occurrence.redeemed ? null : occurrence.lpLeg;
        if (!Double.isFinite(finalLp)) {
            releaseHeld();
            occurrence.redeemed = true;
            refusal = Component.translatable("message.ars_n_spells.glyph.dormant.spell_missing", occurrence.definition.spellName());
            return false;
        }
        if (held != null && Math.abs(occurrence.lpReserved - finalLp) < 1.0e-6) {
            double taken = AlternativePayment.commit(held);
            occurrence.lpLeg = null;
            markPaid(null, "LP:" + taken);
            lpMessage(taken);
            return true;
        }
        // The price moved, or this is a later execution: return what was held, settle the real price.
        if (held != null) {
            AlternativePayment.release(held);
            occurrence.lpLeg = null;
        }
        if (occurrence.deathPenalty && !occurrence.redeemed) {
            // Insufficient LP with the native death option: the effect runs and the caster dies after it.
            ArsCastPayments.applyDeathPenalty(player);
            markPaid(null, "death penalty");
            return true;
        }
        if (finalLp <= 0) {
            markPaid(null, "free");
            return true;
        }
        UUID fresh = UUID.randomUUID();
        CompatibilityStatus status = AlternativeResourceAccess.lpStatus();
        var result = AlternativePayment.reserve(fresh, player.getUUID(), ResourceUnit.LP, finalLp, access, status,
            AnsConfig.getPaymentOpenFailurePolicy());
        if (!result.allowsCast()) {
            occurrence.redeemed = true;
            refusal = lpShortage(player, access, status, Component.literal(occurrence.definition.spellName()), finalLp);
            return false;
        }
        if (result.leg() != null) markLpLeg(player, fresh);
        double taken = AlternativePayment.commit(fresh);
        markPaid(null, "LP:" + taken);
        lpMessage(taken);
        return true;
    }

    private void lpMessage(double taken) {
        if (taken > 0 && AnsConfig.SHOW_LP_COST_MESSAGES.get())
            player.displayClientMessage(Component.translatable("message.ars_n_spells.lp.consumed", Math.round(taken)), true);
    }

    /**
     * Hand the first execution's reservation back before Iron's own cast pipeline prices a
     * charged or channelled child, which then pays each invocation at Iron's native write (or in LP,
     * or not at all for Covenant's aura) by the same route rules: one owner per debit.
     */
    public void releaseForNativePipeline() {
        releaseHeld();
        occurrence.redeemed = true;
        released = true;
    }

    /** Refused before any effect: return a held reservation. Idempotent; never refunds a paid execution. */
    public void releaseIfUnpaid() {
        if (paid || released) return;
        released = true;
        if (firstReservation() != null || (!occurrence.redeemed && occurrence.lpLeg != null)) {
            releaseHeld();
            occurrence.redeemed = true;
        }
    }

    private void releaseHeld() {
        CastAttempt reservation = firstReservation();
        if (reservation != null) CastLedger.cancel(reservation, CastLedger.forPlayer(player));
        if (!occurrence.redeemed && occurrence.lpLeg != null) {
            AlternativePayment.release(occurrence.lpLeg);
            occurrence.lpLeg = null;
        }
    }

    public boolean paid() { return paid; }
    /** The resource route this execution pays through (audit ANS-001: the Covenant hooks consult it). */
    public GlyphRoute route() { return occurrence.route; }
    public CostQuote settled() { return settled; }
    public NativePayment.Result shortage() { return shortage; }

    /** Why the payment was refused, when it was refused for something other than a mana shortage. */
    @Nullable
    public Component refusal() { return refusal; }

    /** What this execution paid, for {@code /ans glyph last}. */
    public String describe() {
        return paid ? described : "nothing";
    }

    private CastAttempt firstReservation() {
        CastAttempt reservation = occurrence.reservation;
        if (occurrence.redeemed || reservation == null || reservation.state().isTerminal()) return null;
        return reservation;
    }

    private void markPaid(@Nullable CostQuote quote, String description) {
        paid = true;
        settled = quote;
        described = description;
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
