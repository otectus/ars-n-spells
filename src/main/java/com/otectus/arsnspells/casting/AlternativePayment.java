package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.contract.CompatibilityStatus;
import com.otectus.arsnspells.contract.PaymentOpenFailurePolicy;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The Living-Point and aura legs of a cast, reserved and committed as part of one transaction
 * (audit V23, V24).
 *
 * <p>Closes the finding that a Covenant ring charge was decided by one query queue and paid
 * from another. The pre-cast gate asked "does this player have enough?" against a pending cost
 * that a FIFO deque had staged; a resolve handler later polled that same deque, computed its
 * own idea of what to take, and drained. Nothing correlated the two, so a cast could be
 * validated against one entry and charged against another, and a drain that came up short was
 * reported to the cast as an unqualified success.
 *
 * <p>Here a leg is reserved once, keyed on the cast's own identity, and committed or released
 * exactly once against that same key. The payment policy is captured at
 * {@link #reserve} - the moment the attempt is initiated - and not re-read at commit, because
 * a config reload between the two must not change the terms of a payment that is already open.
 *
 * <p><b>The three outcomes.</b> A full drain reserves. A short or unresolved drain is not a
 * discount and is never a silent success:
 *
 * <ul>
 *   <li>{@link PaymentOpenFailurePolicy#REFUSE} - release what was taken and deny the cast.</li>
 *   <li>{@link PaymentOpenFailurePolicy#NATIVE_FALLBACK} - release and deny the alternative
 *       leg, leaving the caller to bill the native pool.</li>
 *   <li>{@link PaymentOpenFailurePolicy#LEGACY_OPEN} - keep the short reservation, log it, and
 *       allow the cast. This is the historical behaviour and only an existing world gets it.</li>
 * </ul>
 *
 * <p>Server thread only, like {@link CastLedger} and the contract types it sits on.
 */
public final class AlternativePayment {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlternativePayment.class);

    /** Below this, a shortfall is integer rounding rather than a genuine partial drain. */
    private static final double SHORTFALL_TOLERANCE = 1.0e-6d;

    /** What one open alternative leg holds. Keyed by the attempt it belongs to. */
    public static final class Leg {
        private final UUID attemptId;
        private final UUID playerId;
        private final ResourceUnit unit;
        private final double requested;
        private final double reserved;
        private final PaymentOpenFailurePolicy policy;
        private boolean settled;

        private Leg(UUID attemptId, UUID playerId, ResourceUnit unit,
                    double requested, double reserved, PaymentOpenFailurePolicy policy) {
            this.attemptId = attemptId;
            this.playerId = playerId;
            this.unit = unit;
            this.requested = requested;
            this.reserved = reserved;
            this.policy = policy;
        }

        public UUID attemptId() {
            return attemptId;
        }

        public UUID playerId() {
            return playerId;
        }

        public ResourceUnit unit() {
            return unit;
        }

        /** What the cast was quoted. */
        public double requested() {
            return requested;
        }

        /** What actually moved, which is what a release gives back. */
        public double reserved() {
            return reserved;
        }

        /** The policy in force when this leg opened, not the one in force now. */
        public PaymentOpenFailurePolicy policy() {
            return policy;
        }

        /** Whether the drain came up short of the quote. */
        public boolean isShort() {
            return reserved + SHORTFALL_TOLERANCE < requested;
        }

        /** Whether this leg has already been committed or released. */
        public boolean isSettled() {
            return settled;
        }
    }

    /** The outcome of asking for a leg. */
    public enum Outcome {
        /** The leg was taken in full and is held pending the cast. */
        RESERVED,
        /** The leg came up short, the policy is LEGACY_OPEN, and the short amount is held. */
        RESERVED_SHORT,
        /** Nothing is held and the cast must not proceed on this resource. */
        DENIED
    }

    /** What {@link #reserve} decided, and the leg it opened if it opened one. */
    public record Result(Outcome outcome, Leg leg) {
        /** Whether the cast may proceed on the strength of this result. */
        public boolean allowsCast() {
            return outcome != Outcome.DENIED;
        }
    }

    private static final Map<UUID, Leg> OPEN = new HashMap<>();

    private AlternativePayment() {
    }

    /**
     * Take one alternative leg for {@code attemptId}.
     *
     * <p>Refuses before touching the pool when the adapter is not
     * {@link CompatibilityStatus#isUsable() usable} and the policy is anything but
     * {@code LEGACY_OPEN}: an adapter that has not proved it can charge anybody must not be
     * asked to, because the failure is indistinguishable from a free cast. Under
     * {@code LEGACY_OPEN} the historical behaviour is reproduced and logged.
     *
     * @param policy the policy captured at initiation, from {@code AnsConfig}
     */
    public static Result reserve(UUID attemptId, UUID playerId, ResourceUnit unit, double amount,
                                 ResourceAccess access, CompatibilityStatus status,
                                 PaymentOpenFailurePolicy policy) {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(policy, "policy");
        if (amount <= 0.0d) {
            return new Result(Outcome.RESERVED, null);
        }
        Leg existing = OPEN.get(attemptId);
        if (existing != null) {
            // Reserving twice for one cast is the double-charge this class exists to prevent.
            return new Result(existing.isShort() ? Outcome.RESERVED_SHORT : Outcome.RESERVED, existing);
        }
        if (access == null || status == null || !status.isUsable()) {
            String reason = status == null ? "no adapter" : status.reason();
            if (policy == PaymentOpenFailurePolicy.LEGACY_OPEN) {
                LOGGER.warn("[ANS] {} payment adapter is not usable ({}); payment_open_failure_policy "
                        + "is legacy_open so the cast proceeds unpaid. Set it to 'refuse' to deny instead.",
                    unit, reason);
                return new Result(Outcome.RESERVED_SHORT, null);
            }
            LOGGER.warn("[ANS] {} payment adapter is not usable ({}); denying the cast under "
                + "payment_open_failure_policy={}.", unit, reason, policy);
            return new Result(Outcome.DENIED, null);
        }

        // Check before taking. An insufficient balance denies before initiation, with no debit
        // at all - that is what makes "insufficient LP costs the player nothing" true.
        double available = access.current(playerId, unit);
        if (available + SHORTFALL_TOLERANCE < amount && policy != PaymentOpenFailurePolicy.LEGACY_OPEN) {
            LOGGER.debug("[ANS] {} leg denied before initiation: need {}, have {}", unit, amount, available);
            return new Result(Outcome.DENIED, null);
        }

        double moved = access.debit(playerId, unit, amount);
        Leg leg = new Leg(attemptId, playerId, unit, amount, Math.max(0.0d, moved), policy);
        if (!leg.isShort()) {
            OPEN.put(attemptId, leg);
            return new Result(Outcome.RESERVED, leg);
        }

        // Short drain. No fictitious success under any policy but the legacy one.
        switch (policy) {
            case LEGACY_OPEN -> {
                OPEN.put(attemptId, leg);
                LOGGER.warn("[ANS] {} drain came up short for {}: asked {}, moved {}. "
                        + "payment_open_failure_policy is legacy_open so the cast proceeds; set it to "
                        + "'refuse' to deny a cast that could not be paid for.",
                    unit, playerId, amount, leg.reserved());
                return new Result(Outcome.RESERVED_SHORT, leg);
            }
            default -> {
                if (leg.reserved() > 0.0d) {
                    double back = access.credit(playerId, unit, leg.reserved());
                    if (back + SHORTFALL_TOLERANCE < leg.reserved()) {
                        LOGGER.error("[ANS] {} leg for {} could not be fully released: took {}, "
                                + "gave back {}. The adapter is lossy in both directions.",
                            unit, playerId, leg.reserved(), back);
                    }
                }
                LOGGER.warn("[ANS] {} drain came up short for {}: asked {}, moved {}. Denying the "
                    + "cast under payment_open_failure_policy={}.", unit, playerId, amount,
                    leg.reserved(), policy);
                return new Result(Outcome.DENIED, null);
            }
        }
    }

    /**
     * Turn the held leg into payment. Idempotent: the second call for one attempt is a no-op,
     * which is what stops a spell whose resolve fires twice from being charged twice.
     *
     * @return the amount kept, or 0 when there was nothing open
     */
    public static double commit(UUID attemptId) {
        Leg leg = OPEN.remove(attemptId);
        if (leg == null || leg.settled) {
            return 0.0d;
        }
        leg.settled = true;
        return leg.reserved();
    }

    /**
     * Give the held leg back. Called when the cast is interrupted, when the carrier changes
     * under an open attempt, or when the ring comes off between quote and resolve. Idempotent.
     *
     * @return the amount actually returned to the pool
     */
    public static double release(UUID attemptId, ResourceAccess access) {
        Leg leg = OPEN.remove(attemptId);
        if (leg == null || leg.settled) {
            return 0.0d;
        }
        leg.settled = true;
        if (access == null || leg.reserved() <= 0.0d) {
            return 0.0d;
        }
        double back = access.credit(leg.playerId(), leg.unit(), leg.reserved());
        if (back + SHORTFALL_TOLERANCE < leg.reserved()) {
            LOGGER.error("[ANS] released {} leg for {} but only {} of {} could be returned; "
                    + "the adapter cannot undo its own drain.",
                leg.unit(), leg.playerId(), back, leg.reserved());
        }
        return back;
    }

    /** The leg held for this attempt, or {@code null}. Diagnostic and test seam. */
    public static Leg peek(UUID attemptId) {
        return OPEN.get(attemptId);
    }

    /** How many legs are currently held. Test seam and leak check. */
    public static int openCount() {
        return OPEN.size();
    }

    /** Drop every leg held for a player without refunding. Logout only. */
    public static void forgetPlayer(UUID playerId) {
        OPEN.entrySet().removeIf(e -> e.getValue().playerId().equals(playerId));
    }

    /** Test seam: start from an empty table. */
    static void clearForTest() {
        OPEN.clear();
    }
}
