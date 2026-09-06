package com.otectus.arsnspells.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One in-flight cast, from request to settlement.
 *
 * <p>Closes the audit finding that a cast's identity, its price, and its payment lived in three
 * unrelated places: a static per-player context, a set of mutable float fields, and whichever
 * handler happened to be running. Nothing tied a refund to the charge it was reversing, so a
 * long-cast that ended twice refunded twice.
 *
 * <p>An attempt is identified by its own {@code attemptId}, not by the player and not by the
 * carrier, so two overlapping casts from the same book are two attempts. The carrier is held as
 * an opaque {@code String} identity supplied by a loader adapter; this package never sees an
 * item stack. {@code payloadRevision} lets a caller detect that the carrier's spell data changed
 * under an open attempt.
 *
 * <p>Every state change goes through a method here, and an illegal edge throws
 * {@link IllegalStateException} rather than being ignored. Release is guarded by
 * {@link #tryMarkReleased()}, which succeeds at most once for the life of the attempt.
 *
 * <p>Not thread-safe by design: attempts are created, moved and settled on the server thread.
 */
public final class CastAttempt {

    private final UUID attemptId;
    private final UUID playerId;
    private final String carrierIdentity;
    private final int payloadRevision;
    private final CostQuote quote;

    private List<ResourceAmount> reservedLegs = Collections.emptyList();
    private List<ResourceAmount> paidLegs = Collections.emptyList();
    private AttemptState state = AttemptState.REQUESTED;
    private boolean released;

    public CastAttempt(UUID attemptId, UUID playerId, String carrierIdentity, int payloadRevision, CostQuote quote) {
        this.attemptId = Objects.requireNonNull(attemptId, "attemptId");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.carrierIdentity = Objects.requireNonNull(carrierIdentity, "carrierIdentity");
        this.payloadRevision = payloadRevision;
        this.quote = Objects.requireNonNull(quote, "quote");
    }

    public UUID attemptId() {
        return attemptId;
    }

    public UUID playerId() {
        return playerId;
    }

    /** Opaque, loader-supplied identity of the item the cast came from. */
    public String carrierIdentity() {
        return carrierIdentity;
    }

    /** Revision of the carrier's spell payload at the moment the attempt opened. */
    public int payloadRevision() {
        return payloadRevision;
    }

    public CostQuote quote() {
        return quote;
    }

    public AttemptState state() {
        return state;
    }

    /** What was actually taken, per unit. Empty until the attempt reserves. */
    public List<ResourceAmount> reservedLegs() {
        return reservedLegs;
    }

    /** What was actually kept, per unit. Empty until the attempt commits. */
    public List<ResourceAmount> paidLegs() {
        return paidLegs;
    }

    /** Whether this attempt's reservation has already been released. */
    public boolean isReleased() {
        return released;
    }

    /**
     * Claim the one release this attempt is allowed.
     *
     * @return {@code true} on the first call, {@code false} on every call after it. A caller
     *         that gets {@code false} must not refund: the refund already happened.
     */
    public boolean tryMarkReleased() {
        if (released) {
            return false;
        }
        released = true;
        return true;
    }

    /** {@link AttemptState#REQUESTED} to {@link AttemptState#VALIDATED}. */
    public void validate() {
        transitionTo(AttemptState.VALIDATED);
    }

    /** {@link AttemptState#VALIDATED} to {@link AttemptState#QUOTED}. */
    public void markQuoted() {
        transitionTo(AttemptState.QUOTED);
    }

    /** {@link AttemptState#QUOTED} to {@link AttemptState#RESERVED}, recording what was taken. */
    public void reserve(List<ResourceAmount> actuallyReserved) {
        Objects.requireNonNull(actuallyReserved, "actuallyReserved");
        transitionTo(AttemptState.RESERVED);
        this.reservedLegs = Collections.unmodifiableList(new ArrayList<>(actuallyReserved));
    }

    /** {@link AttemptState#RESERVED} to {@link AttemptState#COMMITTED}; the reservation becomes payment. */
    public void commit() {
        transitionTo(AttemptState.COMMITTED);
        this.paidLegs = this.reservedLegs;
    }

    /** {@link AttemptState#COMMITTED} to {@link AttemptState#COMPLETED}. */
    public void complete() {
        transitionTo(AttemptState.COMPLETED);
    }

    /** Move to the {@link AttemptState#FAILED} sink. */
    public void fail() {
        transitionTo(AttemptState.FAILED);
    }

    /** Move to the {@link AttemptState#CANCELLED} sink. */
    public void cancel() {
        transitionTo(AttemptState.CANCELLED);
    }

    private void transitionTo(AttemptState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException(
                "Illegal cast-attempt transition " + state + " -> " + next + " for attempt " + attemptId);
        }
        state = next;
    }
}
