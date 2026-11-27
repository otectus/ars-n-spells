package com.otectus.arsnspells.inscription;

import com.otectus.arsnspells.contract.InscriptionPlan;
import org.jetbrains.annotations.Nullable;

/**
 * Which reason the Spell Loom screen shows, and which it enables its buttons by (audit ANS-009).
 *
 * <p>The client plans locally for immediate feedback, but the server decides. The last accepted
 * reply is kept with the action it answered and the complete draft it was made for (inventory
 * revision, school mapping digest, requested school, name, background and icon). While the draft is
 * unchanged, a matching server refusal takes precedence over the local plan for that action's
 * family: Inscribe and Convert (and previews of them), or Apply School. Any change to the draft makes
 * the reply stale, so an old refusal or an old success is never shown for a different plan.
 *
 * <p>Temporary admission refusals ({@code request_rejected}, {@code stale_request}) are retryable:
 * they are shown, but the buttons stay enabled by the local plan, and one fresh preview is requested
 * {@link #RETRY_TICKS} after each, so feedback recovers without a per-tick loop. Pure; no Minecraft
 * client types.
 */
public final class LoomFeedback {
    /** The packet's action codes (SpellLoomExportPacket). */
    public static final int PREVIEW = 0, INSCRIBE = 1, CONVERT = 2, SET_SCHOOL = 3;
    public static final int RETRY_TICKS = 20;
    public static final String STALE = "stale_request", REJECTED = "request_rejected";
    /** Local only: the name fails the server's name budget, so Inscribe and Convert would be refused. */
    public static final String NAME_INVALID = "name invalid";

    private record Outcome(int action, String reason, String draft) {}

    @Nullable private Outcome last;
    private long retryAt = -1;

    /** Everything the server validates for a request, as one comparable fingerprint. */
    public static String draft(String revision, String mappingDigest, String school, String name, String nature, String icon) {
        return String.join("\u0000", nz(revision), nz(mappingDigest), nz(school), nz(name).trim(), nz(nature), nz(icon));
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    public static boolean retryable(String reason) {
        return STALE.equals(reason) || REJECTED.equals(reason);
    }

    /** A reply the screen accepted (for its latest request, menu, session and revision). */
    public void accept(int action, String reason, String draft, long now) {
        String code = reason == null ? InscriptionPlan.REASON_OK : reason;
        last = new Outcome(action, code, draft);
        retryAt = retryable(code) ? now + RETRY_TICKS : -1;
    }

    /** Forget every reply (the menu changed hands, or the screen reopened). */
    public void clear() {
        last = null;
        retryAt = -1;
    }

    /** A matching server refusal for the Inscribe/Convert family, or null. */
    @Nullable
    private String inscribeRefusal(String draft) {
        Outcome outcome = last;
        if (outcome == null || outcome.action == SET_SCHOOL || !outcome.draft.equals(draft)) return null;
        return InscriptionPlan.REASON_OK.equals(outcome.reason) ? null : outcome.reason;
    }

    @Nullable
    private String schoolRefusal(String draft) {
        Outcome outcome = last;
        if (outcome == null || outcome.action != SET_SCHOOL || !outcome.draft.equals(draft)) return null;
        return InscriptionPlan.REASON_OK.equals(outcome.reason) ? null : outcome.reason;
    }

    /** The reason to show for Inscribe/Convert: a matching server refusal, else the local plan. */
    public String inscribeDisplay(String local, String draft) {
        String server = inscribeRefusal(draft);
        return server != null ? server : local;
    }

    /** The reason Inscribe/Convert are enabled by: a retryable refusal never disables them. */
    public String inscribeEnablement(String local, String draft) {
        String server = inscribeRefusal(draft);
        return server != null && !retryable(server) ? server : local;
    }

    public String schoolDisplay(String local, String draft) {
        String server = schoolRefusal(draft);
        return server != null ? server : local;
    }

    public String schoolEnablement(String local, String draft) {
        String server = schoolRefusal(draft);
        return server != null && !retryable(server) ? server : local;
    }

    /**
     * Whether a fresh preview should be requested now: once, {@link #RETRY_TICKS} after a retryable
     * refusal of the current draft. A reply to that preview resets the timer if it is refused again.
     */
    public boolean retryDue(String draft, long now) {
        Outcome outcome = last;
        if (retryAt < 0 || outcome == null || !outcome.draft.equals(draft) || now < retryAt) return false;
        retryAt = -1;
        return true;
    }
}
