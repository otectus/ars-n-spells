package com.otectus.arsnspells.contract;

import java.util.Objects;

/**
 * What one compatibility adapter actually knows about the mod it bridges to (audit V24).
 *
 * <p>Closes the finding that adapter health was a single boolean, {@code isAvailable()}, set
 * from a mod-list check. "The mod is loaded" was then treated as "its API resolved and its
 * methods are callable", so a version bump that moved a method left the adapter reporting
 * available while every call through it threw and was swallowed. Alternative payment then
 * appeared to succeed and the spell was cast for free.
 *
 * <p>Four states replace the boolean, and each carries the reason it is in that state, so the
 * self-check log and the {@code /ans} diagnostic say something a pack author can act on.
 *
 * @param adapterId the adapter this describes, matching the named ids on {@link ModeRoutingSnapshot}
 * @param state     what is known about the adapter
 * @param reason    a short human-readable explanation, never {@code null}, empty only for {@link State#VERIFIED}
 */
public record CompatibilityStatus(String adapterId, CompatibilityStatus.State state, String reason) {

    /** How far an adapter got. */
    public enum State {
        /** The target mod is not loaded. Nothing was attempted. */
        ABSENT,
        /** The mod is loaded but the adapter has not yet proved it can call into it. */
        PRESENT_UNVERIFIED,
        /** The adapter resolved the API and a probe call succeeded. */
        VERIFIED,
        /** The mod is loaded but the adapter cannot use it. Treat as unavailable, and say why. */
        DEGRADED
    }

    public CompatibilityStatus {
        Objects.requireNonNull(adapterId, "adapterId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(reason, "reason");
        if (adapterId.isEmpty()) {
            throw new IllegalArgumentException("adapterId must be named");
        }
        if (state != State.VERIFIED && reason.isEmpty()) {
            throw new IllegalArgumentException("state " + state + " must carry a reason");
        }
    }

    /** The adapter proved itself. */
    public static CompatibilityStatus verified(String adapterId) {
        return new CompatibilityStatus(adapterId, State.VERIFIED, "");
    }

    /**
     * Whether it is safe to route a payment through this adapter. Only {@link State#VERIFIED}
     * qualifies: an unverified adapter has not shown it can charge anybody.
     */
    public boolean isUsable() {
        return state == State.VERIFIED;
    }
}
