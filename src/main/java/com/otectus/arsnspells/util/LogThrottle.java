package com.otectus.arsnspells.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A per-key rate limiter for log lines that a player can trigger as fast as they can click.
 *
 * <p>Server-side diagnostics written for a rare desync become log floods the moment the same
 * branch turns out to be reachable from an ordinary player action - a dud scroll being
 * right-clicked repeatedly, say. Gating the {@code LOGGER.warn} on {@link #allow} keeps the
 * first line of every burst, which is the one that carries the diagnostic value, and drops the
 * rest.
 *
 * <p>Modelled on {@code CastValidationScope.throttledLog}, which does the same thing inline for
 * its own INFO logger. This class exists because the callers here need their own logger and
 * their own level, which that method hardcodes; the two are worth folding together, but that is
 * a refactor rather than part of the fix this was added for.
 *
 * <p><b>Bounded.</b> Entries are swept opportunistically - every 64th admitted call, anything
 * older than a minute is dropped - so the map cannot grow without limit across player churn on
 * a long-running server. This is the same idiom as ANS-MED-003.
 */
public final class LogThrottle {
    private static final Map<UUID, Long> LAST_MS = new ConcurrentHashMap<>();

    /** Entries older than this are dead state: only the most recent timestamp per key is read. */
    private static final long EVICT_AFTER_MS = 60_000L;

    private LogThrottle() {}

    /**
     * True when {@code key} has not been admitted within the last {@code windowMs}, in which
     * case this call is recorded as the newest admission.
     *
     * <p>A null key is always admitted rather than silently swallowed: losing a diagnostic is
     * worse than logging one extra line, and a null key cannot be tracked anyway.
     */
    public static boolean allow(UUID key, long windowMs) {
        if (key == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_MS.get(key);
        if (last != null && now - last < windowMs) {
            return false;
        }
        if ((LAST_MS.size() & 63) == 0) {
            long cutoff = now - EVICT_AFTER_MS;
            LAST_MS.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
        LAST_MS.put(key, now);
        return true;
    }

    /** Drop every entry. Server stop, alongside the other per-player stores. */
    public static void clearAll() {
        LAST_MS.clear();
    }
}
