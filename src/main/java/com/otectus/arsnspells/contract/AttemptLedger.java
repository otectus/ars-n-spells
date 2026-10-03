package com.otectus.arsnspells.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The server-thread registry of open cast attempts, and the only place resources move.
 *
 * <p>Closes two audit findings at once:
 *
 * <ul>
 *   <li><b>Double refund.</b> A long cast that was interrupted and then also ended normally ran
 *       the release path twice and credited the player twice. Release here is idempotent: it
 *       goes through {@link CastAttempt#tryMarkReleased()}, so the second call is a silent
 *       no-op, never a second credit.</li>
 *   <li><b>Leaked attempts.</b> Recast and long-cast paths could open an attempt that no exit
 *       path ever closed, leaving a reservation held against the player forever.
 *       {@link #expireOlderThan} is the sweep entry point a per-tick handler calls to cancel
 *       and release anything older than its TTL.</li>
 * </ul>
 *
 * <p>This is the one stateful type in the contract package. It is not thread-safe and must be
 * touched only from the server thread; that is the same constraint the loader code it replaces
 * already lived under.
 */
public final class AttemptLedger {

    /** An open attempt plus the tick it opened on, which is what the TTL sweep reads. */
    private static final class Entry {
        final CastAttempt attempt;
        final long openedTick;

        Entry(CastAttempt attempt, long openedTick) {
            this.attempt = attempt;
            this.openedTick = openedTick;
        }
    }

    private final Map<UUID, Map<UUID, Entry>> byPlayer = new HashMap<>();

    /**
     * Register a new attempt in {@link AttemptState#REQUESTED}.
     *
     * @param openedTick the game tick used by {@link #expireOlderThan}
     */
    public CastAttempt open(UUID playerId, String carrierIdentity, int payloadRevision,
                            CostQuote quote, long openedTick) {
        Objects.requireNonNull(playerId, "playerId");
        CastAttempt attempt =
            new CastAttempt(UUID.randomUUID(), playerId, carrierIdentity, payloadRevision, quote);
        byPlayer.computeIfAbsent(playerId, k -> new LinkedHashMap<>())
            .put(attempt.attemptId(), new Entry(attempt, openedTick));
        return attempt;
    }

    /** The open attempt for this player and carrier, if there is one. */
    public Optional<CastAttempt> findOpen(UUID playerId, String carrierIdentity) {
        Map<UUID, Entry> open = byPlayer.get(playerId);
        if (open == null) {
            return Optional.empty();
        }
        for (Entry entry : open.values()) {
            if (entry.attempt.carrierIdentity().equals(carrierIdentity)) {
                return Optional.of(entry.attempt);
            }
        }
        return Optional.empty();
    }

    /** Every open attempt for a player, in the order they opened. */
    public List<CastAttempt> openFor(UUID playerId) {
        Map<UUID, Entry> open = byPlayer.get(playerId);
        if (open == null) {
            return Collections.emptyList();
        }
        List<CastAttempt> attempts = new ArrayList<>(open.size());
        for (Entry entry : open.values()) {
            attempts.add(entry.attempt);
        }
        return Collections.unmodifiableList(attempts);
    }

    /**
     * Debit the quoted legs and hold them.
     *
     * <p>Each leg records protected native before/after observations, including a failed or
     * throwing API call. Compensation owns only the observed debit and retains unpaid credit.
     *
     * @return what was actually reserved, per leg
     */
    public List<ResourceAmount> reserve(CastAttempt attempt, ResourceAccess access) {
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(access, "access");
        // Check the transition before touching a pool. Debiting first and discovering the
        // attempt was in the wrong state afterwards leaves the player short with no
        // reservation recorded to refund from.
        if (!attempt.state().canTransitionTo(AttemptState.RESERVED)) {
            throw new IllegalStateException(
                "Illegal cast-attempt transition " + attempt.state() + " -> " + AttemptState.RESERVED
                    + " for attempt " + attempt.attemptId());
        }
        attempt.reserve(List.of());
        attempt.beginReservation();
        try {
        for (ResourceAmount leg : attempt.quote().legs()) {
            ResourceMovement move = access.observeDebit(attempt.playerId(), leg.unit(), leg.amount());
            boolean accepted;
            try { accepted = access.acceptsDebit(leg.unit(), move); }
            catch (RuntimeException error) {
                if (move.error() != null) error.addSuppressed(move.error());
                move = new ResourceMovement(move.before(), move.requested(), move.after(), move.reported(), move.attempted(), error);
                accepted = false;
            }
            // Publish before another API call can throw or re-enter the ledger.
            attempt.recordDebit(leg.unit(), move, accepted);
            if (!accepted || attempt.releaseRequested()) break;
        }
        } finally { attempt.endReservation(); }
        if (attempt.releaseRequested()) settle(attempt, access, attempt.failureRequested());
        return attempt.reservedLegs();
    }

    /** Turn a reservation into a payment. The attempt stays open until {@link #complete}. */
    public void commit(CastAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        attempt.commit();
        // Reservation ownership ends at commit. A late cancel/failure callback must
        // never turn an already executed paid cast into a refund, including COMPLETED.
        attempt.tryMarkReleased();
    }

    /** Settle a committed attempt and drop it from the registry. Nothing is refunded. */
    public void complete(CastAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        attempt.complete();
        forget(attempt);
    }

    /**
     * Release any reservation and move the attempt to {@link AttemptState#FAILED}.
     *
     * @return the amounts actually credited back, empty if this attempt was already released
     */
    public List<ResourceAmount> fail(CastAttempt attempt, ResourceAccess access) {
        return settle(attempt, access, true);
    }

    /**
     * Release any reservation and move the attempt to {@link AttemptState#CANCELLED}.
     *
     * @return the amounts actually credited back, empty if this attempt was already released
     */
    public List<ResourceAmount> cancel(CastAttempt attempt, ResourceAccess access) {
        return settle(attempt, access, false);
    }

    /**
     * Cancel and release every attempt opened more than {@code ttlTicks} ago.
     *
     * @return the attempts that were swept
     */
    public List<CastAttempt> expireOlderThan(long nowTicks, long ttlTicks, ResourceAccess access) {
        Objects.requireNonNull(access, "access");
        List<CastAttempt> expired = new ArrayList<>();
        for (Map<UUID, Entry> open : byPlayer.values()) {
            for (Entry entry : new ArrayList<>(open.values())) {
                if (nowTicks - entry.openedTick > ttlTicks) {
                    expired.add(entry.attempt);
                }
            }
        }
        for (CastAttempt attempt : expired) {
            cancel(attempt, access);
        }
        return Collections.unmodifiableList(expired);
    }

    public void restore(UUID id, UUID player, List<ResourceAmount> owed, java.util.Set<ResourceUnit> unknown) {
        CostQuote quote = new CostQuote(ResourceAmount.zero(ResourceUnit.ARS_MANA), List.of(), List.of(), 0);
        CastAttempt attempt = new CastAttempt(id, player, "recovery:" + id, 0, quote);
        attempt.restoreObligation(owed, unknown);
        byPlayer.computeIfAbsent(player, ignored -> new LinkedHashMap<>()).put(id, new Entry(attempt, 0));
    }
    public List<CastAttempt> allOpen() {
        return byPlayer.values().stream().flatMap(entries -> entries.values().stream()).map(e -> e.attempt).toList();
    }
    /** Detach only when the server has saved unresolved obligations. */
    public void clear() { byPlayer.clear(); }

    /** Number of attempts still open, across every player. */
    public int openCount() {
        int count = 0;
        for (Map<UUID, Entry> open : byPlayer.values()) {
            count += open.size();
        }
        return count;
    }

    private List<ResourceAmount> settle(CastAttempt attempt, ResourceAccess access, boolean failed) {
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(access, "access");
        // A re-entrant cancellation during an adapter call cannot release an observation
        // that has not returned yet. Defer compensation until the after-read is recorded.
        if (attempt.reserving()) { attempt.deferRelease(failed); return List.of(); }
        // Move first, refund second. An illegal settlement must throw before any resource
        // has been credited, or a rejected call still hands the player their mana back.
        if (!attempt.state().isTerminal()) {
            if (failed) {
                attempt.fail();
            } else {
                attempt.cancel();
            }
        }
        // Guard the credit, not the state change: a second caller still needs the attempt to end
        // up terminal, it just must not be paid twice.
        List<ResourceAmount> refunded = Collections.emptyList();
        if (attempt.beginCompensation()) {
            try {
                List<ResourceAmount> credits = new ArrayList<>();
                for (ResourceAmount leg : attempt.remainingRefunds()) {
                    if (attempt.unknownUnits().contains(leg.unit())) continue;
                    ResourceMovement move = access.observeCredit(attempt.playerId(), leg.unit(), leg.amount());
                    attempt.recordCredit(leg.unit(), move);
                    credits.add(new ResourceAmount(leg.unit(), move.credited()));
                }
                refunded = Collections.unmodifiableList(credits);
                if (attempt.compensationComplete()) attempt.tryMarkReleased();
            } finally { attempt.endCompensation(); }
        }
        if (attempt.isReleased()) forget(attempt);
        return refunded;
    }

    private void forget(CastAttempt attempt) {
        Map<UUID, Entry> open = byPlayer.get(attempt.playerId());
        if (open == null) {
            return;
        }
        open.remove(attempt.attemptId());
        if (open.isEmpty()) {
            byPlayer.remove(attempt.playerId());
        }
    }
}
