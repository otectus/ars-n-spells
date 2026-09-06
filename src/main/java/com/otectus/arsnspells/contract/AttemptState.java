package com.otectus.arsnspells.contract;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The lifecycle of one cast attempt.
 *
 * <p>Closes the audit finding that a cast's progress was tracked by a scatter of booleans
 * ({@code costsReady}, {@code multiplierApplied}, {@code consumed}) on a shared context. Nothing
 * stopped a handler from charging an attempt that had already been refunded, or refunding one
 * that had never been charged, because no combination of those booleans was illegal. Here the
 * legal edges are declared, and {@link CastAttempt} refuses every other one.
 *
 * <p>{@link #COMPLETED}, {@link #FAILED} and {@link #CANCELLED} are terminal sinks: an attempt
 * that reaches one never moves again.
 */
public enum AttemptState {
    /** The cast was asked for. Nothing has been read or charged. */
    REQUESTED,
    /** Preconditions checked: the caster exists, the carrier is real, the payload parses. */
    VALIDATED,
    /** A {@link CostQuote} has been computed and attached. */
    QUOTED,
    /** The quoted legs have been debited and are held pending the cast's outcome. */
    RESERVED,
    /** The cast succeeded; the reservation is now a payment and will not be refunded. */
    COMMITTED,
    /** Terminal. Everything the cast owed is settled. */
    COMPLETED,
    /** Terminal. The cast failed; any reservation was released exactly once. */
    FAILED,
    /** Terminal. The cast was abandoned or swept by TTL; any reservation was released once. */
    CANCELLED;

    private static final Map<AttemptState, Set<AttemptState>> LEGAL;

    static {
        Map<AttemptState, Set<AttemptState>> legal = new EnumMap<>(AttemptState.class);
        legal.put(REQUESTED, EnumSet.of(VALIDATED, FAILED, CANCELLED));
        legal.put(VALIDATED, EnumSet.of(QUOTED, FAILED, CANCELLED));
        legal.put(QUOTED, EnumSet.of(RESERVED, FAILED, CANCELLED));
        legal.put(RESERVED, EnumSet.of(COMMITTED, FAILED, CANCELLED));
        legal.put(COMMITTED, EnumSet.of(COMPLETED, FAILED));
        legal.put(COMPLETED, EnumSet.noneOf(AttemptState.class));
        legal.put(FAILED, EnumSet.noneOf(AttemptState.class));
        legal.put(CANCELLED, EnumSet.noneOf(AttemptState.class));
        LEGAL = Collections.unmodifiableMap(legal);
    }

    /** Whether an attempt in this state can still move. */
    public boolean isTerminal() {
        return LEGAL.get(this).isEmpty();
    }

    /** Whether {@code next} is a legal successor of this state. */
    public boolean canTransitionTo(AttemptState next) {
        return next != null && LEGAL.get(this).contains(next);
    }

    /** The legal successors of this state, unmodifiable. */
    public Set<AttemptState> successors() {
        return Collections.unmodifiableSet(LEGAL.get(this));
    }
}
