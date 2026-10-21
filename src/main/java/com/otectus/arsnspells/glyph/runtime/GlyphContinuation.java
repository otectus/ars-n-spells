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
                                    @Nullable GlyphPayments.Reservation reservation, int ticks) {
        return new GlyphContinuation(Mode.CHARGE, session, request, effect, hit, level, resolver, token, reservation, ticks);
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

    @Override
    public void tick(boolean serverSide) {
        if (!serverSide || done) return;
        ServerPlayer owner = session.ownerEntity();
        if (session.closed || owner == null || owner != request.caster() || !owner.isAlive()
                || owner.level() != world) {
            cancel("caster unavailable");
            return;
        }
        if (mode == Mode.CHARGE) {
            if (--remaining > 0) return;
            done = true;
            boolean applied = GlyphRuntime.completeCharge(this, token, reservation);
            reservation = null;
            finish(applied);
        } else {
            GlyphChannel ch = channel;
            ch.tick();
            if (ch.pulseDue()) {
                boolean pulsed = ch.pulse();
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
    }

    private void complete(GlyphChannel ch, String detail) {
        done = true;
        ch.end(false, true);
        session.lastOutcome = GlyphOutcome.COMPLETED;
        GlyphSessions.recordOutcome(request.caster(), request.key(), request.effectiveLevel(), GlyphOutcome.COMPLETED, detail);
        finish(true);
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

    /** Cancel without running: release what is unspent, never resume. Idempotent. */
    void cancel(String why) {
        if (done) return;
        done = true;
        this.duration = 0;
        if (reservation != null) {
            GlyphPayments.release(reservation);
            reservation = null;
        }
        if (channel != null) {
            channel.end(true, false);
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
