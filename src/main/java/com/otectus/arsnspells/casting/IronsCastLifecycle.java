package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.contract.*;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.LogPrivacy;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import org.slf4j.LoggerFactory;

/**
 * Server-thread cast identity and per-invocation state, separate from payment ownership.
 *
 * <p>One invocation of Iron's {@code castSpell}: {@link #enter}, pricing after the final cost
 * event ({@code IronsCastPayments.price}), payment at Iron's native mana write
 * ({@link #nativeWrite}), commitment at effect entry ({@link #startEffect}), then
 * {@link #exit}. An invocation that returns without an effect ends in {@link #abort} when ANS
 * refused it and in {@link #notReached} when another mod ended it.
 */
public final class IronsCastLifecycle {
    private IronsCastLifecycle() {}
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(IronsCastLifecycle.class);
    private static final Map<MagicData, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<MagicData, Boolean> ABORTED = new WeakHashMap<>();
    private static final Map<UUID, Map<String, Boolean>> COOLDOWNS = new HashMap<>();
    private static final ThreadLocal<Deque<Frame>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);
    private static long failedSessions, exhaustedChannels, vetoedInvocations;

    private static final class Session {
        final UUID id = UUID.randomUUID(), owner;
        final String spell;
        final int level;
        final CastSource source;
        final CostRules rules;
        ItemStack carrier;
        int started, finished, sequence, lastDuration = Integer.MIN_VALUE;
        boolean active, terminal, messaged, exhausted;
        Session(ServerPlayer player, AbstractSpell spell, int level, CastSource source) {
            this.owner = player.getUUID(); this.spell = spell.getSpellId(); this.level = level;
            this.source = source; this.rules = QuoteService.currentRules();
        }
    }
    public static final class Frame {
        final Session session;
        final MagicData data;
        final ServerPlayer player;
        final AbstractSpell spell;
        public final int level;
        public final CastSource source;
        /** Native {@code castSpell} cooldown argument: false for an intermediate channel pulse. */
        final boolean finalInvocation;
        public IronsCastPayments.Outcome outcome;
        /** The final-unit price this invocation settled against, once committed. */
        public CostQuote quote;
        public UUID alternativeId;
        public ResourceAccess alternativeAccess;
        /** A proxy's delegated Ars result; null until a proxy effect reports one. */
        Boolean delegated;
        public boolean started, finished, aborted;
        final boolean owns;
        final boolean standalone;
        Frame(Session session, MagicData data, ServerPlayer player, AbstractSpell spell, int level, CastSource source,
              boolean finalInvocation, boolean owns) {
            this.session = session; this.data = data; this.player = player; this.spell = spell;
            this.level = level; this.source = source; this.finalInvocation = finalInvocation;
            this.owns = owns; this.standalone = !data.isCasting();
        }
    }
    public static void initiated(ServerPlayer player, AbstractSpell spell, int level, CastSource source) {
        MagicData data = MagicData.getPlayerMagicData(player);
        SESSIONS.put(data, new Session(player, spell, level, source));
        ABORTED.remove(data);
    }
    public static void bindCarrier(MagicData data) {
        Session session = SESSIONS.get(data);
        if (session != null) session.carrier = data.getPlayerCastingItem().copy();
    }
    public static Frame enter(ServerPlayer player, AbstractSpell spell, int level, CastSource source, boolean finalInvocation) {
        MagicData data = MagicData.getPlayerMagicData(player);
        Session session = SESSIONS.computeIfAbsent(data, ignored -> new Session(player, spell, level, source));
        boolean owns = !session.active;
        if (owns) ABORTED.remove(data);
        Frame frame = new Frame(session, data, player, spell, level, source, finalInvocation, owns);
        ACTIVE.get().push(frame);
        if (!owns || session.terminal || !session.owner.equals(player.getUUID()) || !session.spell.equals(spell.getSpellId())
                || session.level != level || session.source != source
                || (session.carrier != null && !ItemStack.matches(session.carrier, data.getPlayerCastingItem()))) {
            frame.outcome = IronsCastPayments.Outcome.refused(session.id, TransactionSnapshot.Reason.IDENTITY_CHANGED);
            return frame;
        }
        if (!session.rules.samePricingAs(QuoteService.currentRules())) {
            frame.outcome = IronsCastPayments.Outcome.refused(session.id, TransactionSnapshot.Reason.CONFIG_CHANGED);
            return frame;
        }
        int duration = data.getCastDurationRemaining();
        if (session.finished > 0 && (spell.getCastType() != CastType.CONTINUOUS || duration == session.lastDuration)) {
            frame.outcome = IronsCastPayments.Outcome.refused(session.id, TransactionSnapshot.Reason.DUPLICATE_EFFECT);
            return frame;
        }
        session.active = true; session.lastDuration = duration; session.sequence++;
        trace(frame, "effect_boundary");
        return frame;
    }
    /** Re-check after external event listeners and native synchronization callbacks. */
    public static TransactionSnapshot.Reason boundaryFailure() {
        Frame frame = current();
        if (frame == null || SESSIONS.get(frame.data) != frame.session || frame.session.terminal
                || (frame.session.carrier != null && !ItemStack.matches(frame.session.carrier, frame.data.getPlayerCastingItem())))
            return TransactionSnapshot.Reason.IDENTITY_CHANGED;
        return frame.session.rules.samePricingAs(QuoteService.currentRules())
            ? null : TransactionSnapshot.Reason.CONFIG_CHANGED;
    }
    public static Frame current() { return ACTIVE.get().peek(); }
    /**
     * Iron's has reached its own mana write. Everything between the cost event and here,
     * including another mod's affordability check or top-up, ran against the undebited pool.
     * An owed payment is taken now and replaces the write.
     *
     * @return whether Iron's own write should run
     * @throws PaymentRefused when the payment cannot be taken, so nothing after the write runs
     */
    public static boolean nativeWrite(MagicData data) {
        Frame frame = current();
        if (frame == null || frame.data != data || frame.outcome == null || !frame.outcome.allowed()) return true;
        if (frame.outcome.owed()) {
            if (frame.quote == null) throw new IllegalStateException("Owed payment has no final price");
            frame.outcome = IronsCastPayments.settle(frame.player, frame.outcome.cross(), frame.quote);
            if (!frame.outcome.allowed()) throw PaymentRefused.INSTANCE;
            trace(frame, "paid_at_native_write");
        }
        return !frame.outcome.replaced();
    }

    /** Ends an invocation whose payment was refused at Iron's native write; reported by {@link #abort}. */
    public static final class PaymentRefused extends RuntimeException {
        private static final PaymentRefused INSTANCE = new PaymentRefused();
        private PaymentRefused() { super("Payment refused at Iron's native mana write", null, false, false); }
    }
    public static void startEffect() {
        Frame frame = current();
        if (frame == null || frame.outcome == null || !frame.outcome.allowed()) throw new IllegalStateException("Missing accepted payment boundary");
        if (boundaryFailure() != null) throw new IllegalStateException("Cast identity or pricing changed before effect entry");
        if (frame.outcome.owed()) {
            // Iron's always writes its debit before the effect of a source it charges. Reaching
            // the effect still owing means another mod skipped that write, so the cast is free
            // by that mod's decision, exactly as it would be without ANS.
            frame.outcome = IronsCastPayments.waived(frame.outcome);
            trace(frame, "native_write_skipped");
        }
        NativePayment.effectStarted(frame.outcome.payment());
        if (frame.alternativeId != null) {
            AlternativePayment.commit(frame.alternativeId);
        }
        frame.started = true; frame.session.started++; trace(frame, "effect_started");
    }
    public static void finishEffect() {
        Frame frame = current();
        frame.finished = true; frame.session.finished++;
        COOLDOWNS.computeIfAbsent(frame.player.getUUID(), ignored -> new HashMap<>()).putIfAbsent(frame.spell.getSpellId(), frame.outcome.cross());
        trace(frame, "effect_finished");
    }

    /**
     * After a paid intermediate channel pulse, decide whether it was the last affordable one.
     *
     * <p>Iron's ends a channel on the pulse after which the caster could not pay another:
     * its ticker forecasts {@code mana - cost * 2 < 0} and runs that pulse as the final one,
     * with cooldown, scroll consumption and completion. That forecast reads the Iron's pool in
     * Iron's units and the base cost, so ANS disables it for channels ANS prices. This is the
     * same rule, applied after settlement with the exact final-unit price just paid, so a
     * routed pool, a conversion rate or an addon's cost listener cannot end a channel early or
     * let it run into a pulse that would then fail its payment.
     */
    public static void afterEffect(Frame frame) {
        if (!frame.owns || !frame.finished || frame.aborted || frame.finalInvocation
                || frame.spell.getCastType() != CastType.CONTINUOUS || !frame.data.isCasting()) return;
        var payment = frame.outcome == null ? null : frame.outcome.payment();
        if (payment == null || !payment.paid() || frame.quote == null) return;
        if (IronsCastPayments.affordsAnotherPulse(frame.player, frame.quote)) return;
        frame.session.exhausted = true;
        trace(frame, "channel_exhausted");
    }

    /**
     * Finish a channel whose last paid pulse left too little for another, exactly as Iron's
     * finishes a final pulse: native cooldown unless a recast is pending, scroll consumption,
     * then completion. Called by the native ticker right after the pulse's invocation.
     */
    public static boolean completeExhaustedChannel(ServerPlayer player) {
        MagicData data = MagicData.getPlayerMagicData(player);
        Session session = SESSIONS.get(data);
        if (session == null || !session.exhausted) return false;
        session.exhausted = false;
        if (!data.isCasting() || !session.spell.equals(data.getCastingSpellId())) return false;
        AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(session.spell);
        CastSource source = data.getCastSource();
        int level = data.getCastingSpellLevel();
        if (!data.getPlayerRecasts().hasRecastForSpell(spell.getSpellId())
                && (!player.isCreative() || io.redspace.ironsspellbooks.config.ServerConfigs.CREATIVE_COOLDOWN.get()))
            io.redspace.ironsspellbooks.api.magic.MagicHelper.MAGIC_MANAGER.addCooldown(player, spell, source);
        if (source == CastSource.SCROLL) io.redspace.ironsspellbooks.item.Scroll.attemptRemoveScrollAfterCast(player);
        spell.onServerCastComplete(player.level(), level, player, data, false);
        exhaustedChannels++;
        if (AnsConfig.DEBUG_MODE.get())
            LOG.info("[CastPayment] attempt={} step={} player={} spell={}/{} source={} stage=channel_completed_exhausted",
                session.id, session.sequence, LogPrivacy.token(player.getUUID()), session.spell, level, source);
        return true;
    }

    /** Record a proxy's delegated Ars result for the invocation that is running it. */
    public static void recordDelegatedResult(ServerPlayer player, AbstractSpell spell, boolean success) {
        Frame frame = current();
        if (frame != null && frame.player == player && frame.spell == spell) frame.delegated = success;
    }

    /**
     * Whether a native cooldown being added for {@code spell} belongs to the running
     * invocation of that spell and that invocation's delegated Ars cast did not succeed.
     */
    public static boolean delegatedCastFailed(ServerPlayer player, AbstractSpell spell) {
        Frame frame = current();
        return frame != null && frame.player == player && frame.spell == spell && !Boolean.TRUE.equals(frame.delegated);
    }

    public static Boolean consumeCooldown(ServerPlayer player, AbstractSpell spell) {
        Map<String, Boolean> entries = COOLDOWNS.get(player.getUUID());
        if (entries == null) return null;
        Boolean cross = entries.remove(spell.getSpellId());
        if (entries.isEmpty()) COOLDOWNS.remove(player.getUUID());
        return cross;
    }
    /**
     * {@code castSpell} returned without reaching the effect and without being ended by ANS.
     *
     * <p>A refusal ANS threw at the native write, which some wrapper swallowed, is still an
     * ANS refusal and is reported. Otherwise another mod ended the invocation, for example an
     * affordability check inside Iron's mana block. That is the cast's native outcome, not a
     * payment fault: anything held is returned, nothing is reported to the caster, and Iron's
     * own follow-through (completion, scroll handling) runs as it would without ANS.
     */
    public static void notReached(Frame frame) {
        if (frame.outcome != null && !frame.outcome.allowed()) { abort(frame, null); return; }
        var payment = frame.outcome == null ? null : frame.outcome.payment();
        var refund = NativePayment.abortBeforeEffect(frame.player, payment, TransactionSnapshot.Reason.NATIVE_VETO);
        if (frame.alternativeId != null) AlternativePayment.release(frame.alternativeId, frame.alternativeAccess);
        vetoedInvocations++;
        if (AnsConfig.DEBUG_MODE.get() && com.otectus.arsnspells.util.LogThrottle.allow(frame.player.getUUID(), 1000))
            LOG.info("[CastPayment] attempt={} step={} player={} spell={}/{} source={} stage=vetoed_before_effect quote={} refund={}",
                frame.session.id, frame.session.sequence, LogPrivacy.token(frame.player.getUUID()), frame.spell.getSpellId(),
                frame.level, frame.source, legs(frame.quote), refund == null ? "none" : refund.refunded());
    }
    public static void abort(Frame frame, RuntimeException failure) {
        if (frame.aborted) return;
        frame.aborted = true;
        if (!frame.owns) { trace(frame, "reentrant_rejection"); return; }
        frame.session.terminal = true;
        var payment = frame.outcome == null ? null : frame.outcome.payment();
        if (!frame.started) {
            payment = NativePayment.abortBeforeEffect(frame.player, payment);
            if (frame.alternativeId != null) AlternativePayment.release(frame.alternativeId, frame.alternativeAccess);
        }
        if (!frame.session.messaged) {
            frame.session.messaged = true;
            failedSessions++;
            report(frame, payment, failure);
        }
        try {
            if (frame.session.started > 0 && frame.data.isCasting())
                io.redspace.ironsspellbooks.api.util.Utils.serverSideCancelCast(frame.player);
            else frame.spell.onServerCastComplete(frame.player.level(), frame.level, frame.player, frame.data, true);
        } finally {
            if (frame.data.isCasting()) frame.data.resetCastingState();
            frame.player.stopUsingItem();
            ABORTED.put(frame.data, true);
        }
    }

    /**
     * One message and at most one log line per failed session.
     *
     * <p>A genuine shortage is an ordinary gameplay outcome: the caster is told what was needed
     * and what they had, and the server log only records it when debug mode is on. Anything
     * else (an identity or pricing change, a settlement or adapter failure, an exception) is a
     * fault worth a throttled warning.
     */
    private static void report(Frame frame, NativePayment.Result payment, RuntimeException failure) {
        String stage = frame.started ? "failed_during_effect" : frame.session.started > 0 ? "interrupted_after_effect" : "aborted_before_effect";
        boolean shortage = failure == null && payment != null && payment.shortage();
        String who = LogPrivacy.token(frame.player.getUUID());
        if (shortage) {
            if (AnsConfig.DEBUG_MODE.get() && com.otectus.arsnspells.util.LogThrottle.allow(frame.player.getUUID(), 1000))
                LOG.info("[CastPayment] attempt={} step={} player={} spell={}/{} source={} stage={} reason={} unit={} required={} available={} quote={}",
                    frame.session.id, frame.session.sequence, who, frame.spell.getSpellId(), frame.level, frame.source, stage,
                    payment.reason(), payment.failureUnit(), payment.required(), payment.available(), legs(frame.quote));
            frame.player.displayClientMessage(shortageMessage(frame, payment), true);
            return;
        }
        if (com.otectus.arsnspells.util.LogThrottle.allow(frame.player.getUUID(), 1000))
            LOG.warn("[CastPayment] attempt={} step={} player={} spell={}/{} source={} stage={} reason={} unit={} quote={} payment={} exception={}",
                frame.session.id, frame.session.sequence, who, frame.spell.getSpellId(), frame.level, frame.source, stage,
                payment == null ? "none" : payment.reason(), payment == null ? "none" : payment.failureUnit(), legs(frame.quote),
                payment, failure == null ? "none" : failure.toString());
        if (failure != null) LOG.debug("[CastPayment] exception", failure);
        frame.player.displayClientMessage(Component.translatable("message.ars_n_spells.cast.payment_failed"), true);
    }

    private static Component shortageMessage(Frame frame, NativePayment.Result payment) {
        Component unit = Component.translatable("ars_n_spells.resource." + payment.failureUnit().name().toLowerCase(Locale.ROOT));
        Component spell = frame.spell.getDisplayName(frame.player);
        if (!Double.isFinite(payment.required()))
            return Component.translatable("message.ars_n_spells.cast.insufficient_unknown", unit, spell);
        if (!Double.isFinite(payment.available()))
            return Component.translatable("message.ars_n_spells.cast.insufficient_required", unit, spell,
                PaymentMessages.amount(payment.required()));
        return Component.translatable("message.ars_n_spells.cast.insufficient", unit, spell,
            PaymentMessages.amount(payment.required()), PaymentMessages.amount(Math.max(0, payment.available())));
    }

    private static String legs(CostQuote quote) {
        if (quote == null) return "none";
        StringJoiner out = new StringJoiner("+");
        for (ResourceAmount leg : quote.legs()) out.add(leg.unit() + ":" + leg.amount());
        return out.toString();
    }
    public static boolean aborted(ServerPlayer player) { return ABORTED.containsKey(MagicData.getPlayerMagicData(player)); }
    public static void clearCaller(ServerPlayer player) { ABORTED.remove(MagicData.getPlayerMagicData(player)); }
    public static void exit(Frame frame) {
        if (frame.owns) {
            frame.session.active = false;
            if (frame.standalone) IronsCastPayments.clear(frame.data);
            if (!frame.data.getPlayerRecasts().hasRecastForSpell(frame.spell.getSpellId())
                    && frame.spell.getCastType() != CastType.CONTINUOUS) {
                Map<String, Boolean> pending = COOLDOWNS.get(frame.player.getUUID());
                if (pending != null) { pending.remove(frame.spell.getSpellId()); if (pending.isEmpty()) COOLDOWNS.remove(frame.player.getUUID()); }
            }
        }
        Deque<Frame> stack = ACTIVE.get();
        if (stack.peek() != frame) throw new IllegalStateException("Cast invocation stack mismatch");
        stack.pop(); if (stack.isEmpty()) ACTIVE.remove();
    }
    public static void reset(MagicData data) { SESSIONS.remove(data); }
    public static void logout(ServerPlayer player) {
        MagicData data = MagicData.getPlayerMagicData(player);
        SESSIONS.remove(data); ABORTED.remove(data); COOLDOWNS.remove(player.getUUID());
    }
    public static void stop() { SESSIONS.clear(); ABORTED.clear(); COOLDOWNS.clear(); ACTIVE.remove(); }
    /** Sessions that ended through a payment or boundary failure; read by diagnostics and GameTests. */
    public static long failedSessions() { return failedSessions; }
    /** Channels ANS finished as native final pulses because the next pulse was unaffordable. */
    public static long exhaustedChannels() { return exhaustedChannels; }
    /** Invocations another mod ended before the effect; ANS returned anything held and stayed silent. */
    public static long vetoedInvocations() { return vetoedInvocations; }
    private static void trace(Frame frame, String stage) {
        if (AnsConfig.DEBUG_MODE.get() && (frame.session.sequence <= 4 || frame.session.sequence % 20 == 0))
            LOG.info("[CastPayment] attempt={} step={} player={} spell={}/{} source={} mode={} generation={} stage={} quote={} outcome={}",
                frame.session.id, frame.session.sequence, LogPrivacy.token(frame.player.getUUID()), frame.spell.getSpellId(), frame.level, frame.source,
                frame.session.rules.modeName(), frame.session.rules.generation(), stage, legs(frame.quote), frame.outcome);
    }
}
