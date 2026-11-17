package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.ResourceAmount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A bounded, in-memory timeline of what moved a player's resources and why (3.4.1 review CAST-01).
 *
 * <p>Answers "did this cast debit me again?" with identities rather than an action-bar reading:
 * every ledger reservation, commitment, refund and native payment is recorded with the attempt's
 * label (an imported glyph's session and recipe index, a native payment id), next to the native
 * cast invocations and imported-glyph emissions around it. A ceiling clamp or a client resync moves
 * a displayed balance without appearing here; a new debit always appears here.
 *
 * <p>Off unless {@code debug_mode} is on or a test enables it, so normal play allocates nothing and
 * logs nothing. Each player keeps at most {@link #CAPACITY} entries, oldest dropped first, and is
 * forgotten on logout and server stop. Lines are logged at DEBUG only.
 */
public final class PaymentTimeline {
    private static final Logger LOGGER = LoggerFactory.getLogger("ArsNSpells/PaymentTimeline");
    public static final int CAPACITY = 512;
    private static volatile boolean testing;
    private static final Map<UUID, ArrayDeque<Entry>> ENTRIES = new ConcurrentHashMap<>();

    /** What kind of thing happened. */
    public enum Kind {
        /** Resources were taken and held (a reservation, or a native payment). */
        DEBIT,
        /** A held reservation became final payment. */
        COMMIT,
        /** A held reservation was given back. */
        REFUND,
        /** One native Iron's {@code castSpell} invocation (a release, a channel pulse). */
        NATIVE_INVOCATION,
        /** A native invocation reached its effect. */
        NATIVE_EFFECT,
        /** A native invocation ended before its effect. */
        NATIVE_ABORT,
        /** One imported-glyph execution and its outcome. */
        EMISSION
    }

    /** One timeline entry: what, which attempt or spell, the amounts, and when. */
    public record Entry(long tick, Kind kind, String label, String detail) {
        @Override
        public String toString() {
            return tick + " " + kind + " " + label + (detail.isEmpty() ? "" : " " + detail);
        }
    }

    private PaymentTimeline() {}

    /** Whether anything is recorded now. */
    public static boolean enabled() {
        if (testing) return true;
        try {
            return com.otectus.arsnspells.config.AnsConfig.DEBUG_MODE.get();
        } catch (IllegalStateException | NullPointerException notLoaded) {
            return false;
        }
    }

    /** For the GameTest harness: record regardless of {@code debug_mode}. */
    public static void enableForTesting(boolean on) {
        testing = on;
        if (!on && !enabled()) ENTRIES.clear();
    }

    public static void record(UUID player, long tick, Kind kind, String label, String detail) {
        if (player == null || !enabled()) return;
        if (tick < 0) tick = now();
        ArrayDeque<Entry> entries = ENTRIES.computeIfAbsent(player, ignored -> new ArrayDeque<>());
        Entry entry = new Entry(tick, kind, label == null ? "" : label, detail == null ? "" : detail);
        synchronized (entries) {
            if (entries.size() >= CAPACITY) entries.removeFirst();
            entries.addLast(entry);
        }
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("[PaymentTimeline] player={} {}", com.otectus.arsnspells.util.LogPrivacy.token(player), entry);
    }

    /** A ledger movement of {@code attempt}: what was asked for and what moved. */
    public static void ledger(CastAttempt attempt, Kind kind, List<ResourceAmount> moved, long tick) {
        if (attempt == null || !enabled()) return;
        record(attempt.playerId(), tick, kind, attempt.carrierIdentity(),
            "attempt=" + attempt.attemptId() + " quote=" + legs(attempt.quote().legs()) + " moved=" + legs(moved));
    }

    /** A ledger movement whose amounts are not known separately (a commitment moves nothing). */
    public static void ledger(CastAttempt attempt, Kind kind, long tick) {
        if (attempt == null || !enabled()) return;
        record(attempt.playerId(), tick, kind, attempt.carrierIdentity(),
            "attempt=" + attempt.attemptId() + " quote=" + legs(attempt.quote().legs()));
    }

    /** The server's tick count, or -1 without a server. */
    static long now() {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? -1 : server.getTickCount();
    }

    public static List<Entry> entries(UUID player) {
        ArrayDeque<Entry> entries = ENTRIES.get(player);
        if (entries == null) return List.of();
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    public static void clear(UUID player) {
        ENTRIES.remove(player);
    }

    public static void clearAll() {
        ENTRIES.clear();
    }

    static String legs(List<ResourceAmount> legs) {
        if (legs == null || legs.isEmpty()) return "none";
        StringJoiner out = new StringJoiner("+");
        for (ResourceAmount leg : legs) out.add(leg.unit() + ":" + leg.amount());
        return out.toString();
    }
}
