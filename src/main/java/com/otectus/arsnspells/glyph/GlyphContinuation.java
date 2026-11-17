package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.event.DelayedSpellEvent;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

/**
 * The rest of an Ars recipe, held while a charged or channelled imported glyph runs.
 *
 * <p>Uses Ars's own delay facility, exactly as {@code EffectDelay} does: the resolver's context is
 * marked delayed, which stops its loop after this glyph, and the same resolver is resumed later
 * from the next part, at the same target, as Ars's own delayed spells are. The event is never put
 * on Ars's timed queue; it is held here and released by the charged cast's outcome.
 *
 * <p><b>States (3.4.1 review CAST-02).</b>
 * <ul>
 *   <li>{@link State#IDLE}: nothing held. A glyph that is the last part of its recipe stays here.</li>
 *   <li>{@link State#HELD}: the tail is held while the child runs.</li>
 *   <li>{@link State#QUEUED}: the child completed and the tail waits for the next server tick. This is
 *       not terminal: the continuation owns one pending reference on its session, so the session
 *       cannot close under it, and it can still be cancelled.</li>
 *   <li>{@link State#RESUMED} and {@link State#CANCELLED}: terminal. The pending reference was
 *       released exactly once on the way in.</li>
 * </ul>
 * Resumption runs from {@link GlyphSessions}'s tick queue, never inside Iron's own cast completion,
 * and only after {@link GlyphExecutionPolicy} re-checks, on the server thread immediately before it,
 * that the session is still open and owned, its caster alive, connected and in the same world, the
 * target still valid and imported glyphs still enabled. Anything else cancels it: the context stops
 * and no trailing Ars effect runs.
 */
public final class GlyphContinuation {
    public enum State { IDLE, HELD, QUEUED, RESUMED, CANCELLED }

    private final SpellResolver resolver;
    private final HitResult hit;
    private final Level level;
    private final UUID sessionId;
    private final UUID owner;
    private DelayedSpellEvent hold;
    private State state = State.IDLE;
    /** The tick it was queued in: it never runs in that same tick. */
    long queuedTick = -1;

    public GlyphContinuation(SpellResolver resolver, HitResult hit, Level level, UUID sessionId, UUID owner) {
        this.resolver = resolver;
        this.hit = hit;
        this.level = level;
        this.sessionId = sessionId;
        this.owner = owner;
    }

    /** Holds made by continuations, so session bookkeeping never mistakes one for an Ars Delay. */
    private static final java.util.Set<DelayedSpellEvent> HOLDS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public static boolean isHold(DelayedSpellEvent event) {
        return event != null && HOLDS.contains(event);
    }

    /** Stop the current resolution after this part; nothing has been delayed until this is called. */
    public void suspend() {
        if (state != State.IDLE || hold != null) return;
        if (!resolver.spellContext.hasNextPart()) return;
        hold = new DelayedSpellEvent(Integer.MAX_VALUE, hit, level, resolver);
        HOLDS.add(hold);
        resolver.spellContext.delay(hold);
        state = State.HELD;
    }

    public boolean suspended() {
        return hold != null;
    }

    public State state() {
        return state;
    }

    /**
     * The child completed: queue the held tail for the next server tick, taking a pending reference
     * on the session first. Returns whether anything was queued; a continuation with no tail ends
     * here with nothing to run.
     */
    public boolean resume(GlyphCastSession session) {
        if (state == State.IDLE) {
            state = State.RESUMED;
            return false;
        }
        if (state != State.HELD) return false;
        state = State.QUEUED;
        if (session != null) GlyphSessions.pendingStarted(session);
        GlyphSessions.queue(this);
        return true;
    }

    /** Run from the tick queue: re-authorize on the server thread, then resume or cancel. */
    void runQueued() {
        if (state != State.QUEUED) return;
        GlyphCastSession session = GlyphSessions.byId(sessionId);
        ServerPlayer player = session == null ? null : session.ownerRef.get();
        String refusal = player == null ? "message.ars_n_spells.glyph.refused.no_session"
            : GlyphExecutionPolicy.casterRefusal(player, session, level);
        if (refusal == null) refusal = GlyphExecutionPolicy.featureRefusal();
        if (refusal == null && hit != null) refusal = GlyphExecutionPolicy.targetRefusal(level, hit, false);
        if (refusal != null) {
            state = State.CANCELLED;
            stopHold();
            release(session);
            GlyphSessions.traceContinuation(owner, sessionId, "tail_cancelled " + refusal);
            return;
        }
        state = State.RESUMED;
        hold.duration = 0;
        try {
            resolver.resume(level);
        } finally {
            release(session);
        }
    }

    /** Drop the remaining parts: the charged cast did not complete, or its session ended. Idempotent. */
    public void cancel() {
        State before = state;
        if (before == State.RESUMED || before == State.CANCELLED) return;
        state = State.CANCELLED;
        stopHold();
        if (before == State.QUEUED) release(GlyphSessions.byId(sessionId));
    }

    public boolean finished() {
        return state == State.RESUMED || state == State.CANCELLED;
    }

    UUID sessionId() {
        return sessionId;
    }

    UUID owner() {
        return owner;
    }

    private void stopHold() {
        if (hold != null) {
            hold.duration = 0;
            resolver.spellContext.stop();
        }
    }

    /** Give back the pending reference taken when the tail was queued. */
    private void release(GlyphCastSession session) {
        if (session != null) GlyphSessions.pendingEnded(session);
    }
}
