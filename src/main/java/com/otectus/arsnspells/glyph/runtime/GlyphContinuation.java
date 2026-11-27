package com.otectus.arsnspells.glyph.runtime;

import com.hollingsworth.arsnouveau.api.event.DelayedSpellEvent;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A suspended branch of an Ars resolution while an imported charged or continuous Iron's effect
 * runs, using Ars's own delay mechanism: the resolver's context is delayed by this event, so Ars
 * resolves nothing after the imported effect until it ends, and then resumes exactly where it
 * stopped. It never restarts from the first glyph and never replays the effects before it.
 *
 * <ul>
 *   <li><b>Charge</b> (Iron's LONG): waits the effective cast time (Iron's cast-time reduction,
 *       then Accelerate/Decelerate), revalidates, then runs the effect once on its held
 *       reservation. A successful effect resumes the branch; a failed one cancels it.</li>
 *   <li><b>Channel</b> (Iron's CONTINUOUS): ticks the native channel every server tick and pulses
 *       every 10 ticks, the first pulse prepaid and every later one paid before it runs. When the
 *       caster cannot pay the next pulse the channel ends there, as Iron's does, and the branch
 *       resumes.</li>
 * </ul>
 * Cancellation (owner gone or dead, dimension change, interruption, lifetime bound) releases only
 * what is unspent and does not resume the branch. Held in Ars's in-memory event queue like any Ars
 * delay; a server restart drops it, and the session's reservation is released by the ledger.
 */
public final class GlyphContinuation extends DelayedSpellEvent {

    private enum Mode { CHARGE, CHANNEL }

    final GlyphCastSession session;
    final GlyphRequest request;
    final ImportedIronEffect effect;
    private final Mode mode;
    private final SpellContext context;
    @Nullable private GlyphPayments.Reservation reservation;
    @Nullable private GlyphChannel channel;
    @Nullable private GlyphCharge charge;
    @Nullable private final GlyphCastSession.Token token;
    private int remaining;
    private int pulses;
    private boolean done;

    private GlyphContinuation(Mode mode, GlyphCastSession session, GlyphRequest request, ImportedIronEffect effect,
                              HitResult hit, Level level, SpellResolver resolver, @Nullable GlyphCastSession.Token token,
                              @Nullable GlyphPayments.Reservation reservation, int ticks) {
        super(Integer.MAX_VALUE / 2, hit, level, resolver, false);
        this.mode = mode;
        this.session = session;
        this.request = request;
        this.effect = effect;
        this.context = resolver.spellContext;
        this.token = token;
        this.reservation = reservation;
        this.remaining = Math.max(1, ticks);
    }

    static GlyphContinuation charge(GlyphCastSession session, GlyphRequest request, ImportedIronEffect effect, HitResult hit,
                                    Level level, SpellResolver resolver, @Nullable GlyphCastSession.Token token,
                                    @Nullable GlyphPayments.Reservation reservation, int ticks, GlyphCharge charge) {
        GlyphContinuation continuation = new GlyphContinuation(Mode.CHARGE, session, request, effect, hit, level, resolver, token, reservation, ticks);
        continuation.charge = charge;
        return continuation;
    }

    /** The native charge this continuation drives; null for a channel. */
    GlyphCharge charge() {
        return charge;
    }

    static GlyphContinuation channel(GlyphCastSession session, GlyphRequest request, ImportedIronEffect effect, HitResult hit,
                                     Level level, SpellResolver resolver, GlyphChannel channel) {
        GlyphContinuation continuation = new GlyphContinuation(Mode.CHANNEL, session, request, effect, hit, level, resolver,
            null, null, Integer.MAX_VALUE);
        continuation.channel = channel;
        return continuation;
    }

    boolean charging() {
        return mode == Mode.CHARGE && !done;
    }

    /** The world the suspended branch resolves in. */
    Level level() {
        return world;
    }

    @Override
    public void tick(boolean serverSide) {
        if (!serverSide || done) return;
        ServerPlayer owner = session.ownerEntity();
        if (session.closed || owner == null || owner != request.caster() || !owner.isAlive()
                || owner.level() != world) {
            cancel("caster unavailable");
            return;
        }
        // 3.4.1 review CAST-04: native code never escapes into Ars's event queue; a failure ends this
        // continuation with what it owes and leaves the scheduler usable.
        try {
            if (mode == Mode.CHARGE) {
                charge.tick();
                if (charge.failed()) {
                    // Native code threw while charging, before any effect: nothing is owed.
                    cancel("failed");
                    return;
                }
                if (--remaining > 0) return;
                done = true;
                boolean applied;
                try {
                    applied = GlyphRuntime.completeCharge(this, token, reservation);
                } catch (RuntimeException | LinkageError failure) {
                    // The bridge contains native failures; this is ANS's own bookkeeping failing.
                    org.slf4j.LoggerFactory.getLogger(GlyphContinuation.class).warn("[ImportedGlyphs] {} charge completion failed: {}",
                        request.key(), failure.toString());
                    charge.cancel();
                    GlyphPayments.release(reservation);
                    session.releaseEmission();
                    applied = false;
                }
                reservation = null;
                finish(applied);
            } else {
                GlyphChannel ch = channel;
                if (ch.failed()) {
                    fail(ch);
                    return;
                }
                ch.tick();
                if (ch.failed()) {
                    fail(ch);
                    return;
                }
                if (ch.pulseDue()) {
                    // 3.4.1 review CAST-03: each pulse is authorized against the policy now, before it is paid.
                    Component refusal = GlyphRuntime.pulseRefusal(this);
                    if (refusal != null) {
                        stop(ch, refusal);
                        return;
                    }
                    boolean pulsed = ch.pulse();
                    if (ch.failed()) {
                        fail(ch);
                        return;
                    }
                    if (pulsed) {
                        pulses++;
                    } else {
                        complete(ch, "ran out of funds after " + pulses + " paid pulse(s)");
                        return;
                    }
                }
                if (ch.finished()) {
                    complete(ch, pulses + " paid pulse(s) after the first");
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            org.slf4j.LoggerFactory.getLogger(GlyphContinuation.class).warn("[ImportedGlyphs] {} continuation failed: {}",
                request.key(), failure.toString());
            if (channel != null) fail(channel);
            else cancel("failed");
        }
    }

    private void complete(GlyphChannel ch, String detail) {
        done = true;
        ch.end(false, true);
        session.lastOutcome = GlyphOutcome.COMPLETED;
        GlyphSessions.recordOutcome(request.caster(), request.key(), request.effectiveLevel(), GlyphOutcome.COMPLETED, detail);
        finish(true);
    }

    /** The policy no longer allows the next pulse: end before it, keep the cooldown earned, never resume. */
    private void stop(GlyphChannel ch, Component why) {
        done = true;
        ch.end(true, ch.pulsedOnce());
        session.lastOutcome = GlyphOutcome.CANCELED;
        session.record("channel stopped: " + why.getString());
        GlyphSessions.recordOutcome(request.caster(), request.key(), request.effectiveLevel(), GlyphOutcome.CANCELED,
            "stopped before pulse " + (pulses + 1) + ": " + GlyphSessions.detail(why));
        request.caster().displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
        finish(false);
    }

    /** Native code threw: the paid pulses stay paid and the cooldown applies; nothing more runs. */
    private void fail(GlyphChannel ch) {
        if (done) return;
        done = true;
        ch.end(true, ch.pulsedOnce());
        session.lastOutcome = GlyphOutcome.FAILED_AFTER_EFFECT;
        GlyphSessions.recordOutcome(request.caster(), request.key(), request.effectiveLevel(), GlyphOutcome.FAILED_AFTER_EFFECT,
            "failed after " + pulses + " pulse(s)");
        request.caster().displayClientMessage(Component.translatable("ars_n_spells.glyph.refused.failed", effect.spellName())
            .withStyle(ChatFormatting.RED), true);
        finish(false);
    }

    /** End the suspension: resume the branch on success, leave it stopped otherwise. */
    private void finish(boolean resume) {
        this.duration = 0;
        GlyphSessions.unregister(this);
        try {
            if (resume && !context.isCanceled()) {
                resolver.resume(world);
            }
        } finally {
            GlyphSessions.pendingEnded(session);
        }
    }

    /**
     * Cancel without running: release what is unspent, never resume. Idempotent. A charge gives back
     * its reservation and its emission permit; a channel that already pulsed ends with the native
     * cooldown those pulses earned (3.4.1 review CAST-04), one that did not ends without it.
     */
    void cancel(String why) {
        if (done) return;
        done = true;
        this.duration = 0;
        if (reservation != null) {
            GlyphPayments.release(reservation);
            reservation = null;
        }
        if (mode == Mode.CHARGE) {
            session.releaseEmission();
            if (charge != null) charge.cancel();
        }
        if (channel != null) {
            channel.end(true, channel.pulsedOnce());
        }
        session.lastOutcome = GlyphOutcome.CANCELED;
        session.record("continuation cancelled: " + why);
        GlyphSessions.recordOutcome(request.caster(), request.key(), request.effectiveLevel(), GlyphOutcome.CANCELED, why);
        ServerPlayer owner = session.ownerEntity();
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("ars_n_spells.glyph.cancelled", effect.spellName(), why)
                .withStyle(ChatFormatting.GRAY), true);
        }
        GlyphSessions.unregister(this);
        GlyphSessions.pendingEnded(session);
    }

    @Override
    public void resolveSpell() {
        // Resumption is driven by tick(); Ars's default resume-on-expiry must not run.
    }

    @Override
    public boolean isExpired() {
        return done;
    }
}
