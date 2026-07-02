package com.otectus.arsnspells.compat;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Per-player pending scroll costs staged by {@code MixinScrollItem}'s HEAD inject
 * and consumed by its RETURN inject. Lives outside the mixin package because
 * Sponge Mixin forbids direct references to classes (including inner classes
 * of a mixin) inside a defined mixin package — the mixin's own bytecode is
 * merged into the target class at runtime, so an inner class
 * {@code MixinX$Y} would have no resolvable home.
 *
 * <p>Keyed by player UUID so concurrent multiplayer scroll uses don't collide.
 * Pre-cast HEAD calls {@link #stage}; post-cast RETURN calls {@link #take} to
 * atomically remove and act on the oldest entry.
 *
 * <p>ANS-MED-042: per-player FIFO deque instead of a single overwriting slot,
 * matching the ring handlers' ANS-3.0.0 migration. With the single slot, a
 * second scroll HEAD before the first's RETURN clobbered the first's staged
 * cost, so the first commit charged the wrong amount. Entries older than
 * {@link #PENDING_TTL_MS} are evicted opportunistically on stage/take (a RETURN
 * suppressed by another mod would otherwise leak its entry until logout).
 */
public final class ScrollLPTracker {
    private static final long PENDING_TTL_MS = 5000; // 100 ticks at 20 TPS, matching CrossCastContext

    private static final Map<UUID, Deque<Entry>> PENDING = new ConcurrentHashMap<>();

    private ScrollLPTracker() {}

    public static void stage(UUID uuid, int lpCost, boolean deathMode) {
        stage(uuid, lpCost, deathMode, 0.0f);
    }

    /**
     * ANS-MED-043: entries also carry the staged mana cost for "full" scroll cost
     * mode — the HEAD inject validates it, the RETURN inject consumes it only if
     * Iron's actually accepted the use.
     */
    public static void stage(UUID uuid, int lpCost, boolean deathMode, float manaCost) {
        Deque<Entry> queue = PENDING.computeIfAbsent(uuid, k -> new ConcurrentLinkedDeque<>());
        evictStale(queue);
        queue.addLast(new Entry(lpCost, deathMode, manaCost, System.currentTimeMillis()));
    }

    public static Entry take(UUID uuid) {
        Deque<Entry> queue = PENDING.get(uuid);
        if (queue == null) {
            return null;
        }
        evictStale(queue);
        // Empty deques are intentionally left in the map: removing them here races
        // with a concurrent stage() on the same queue object. They are bounded by
        // online-player count and drained by clear() on logout.
        return queue.pollFirst();
    }

    /**
     * ANS-HIGH-025: explicit clear hook for logout cleanup. {@link #take} would
     * also work, but {@code clear} reads better at the call site (drains state
     * unconditionally rather than implying a value return).
     */
    public static void clear(UUID uuid) {
        PENDING.remove(uuid);
    }

    private static void evictStale(Deque<Entry> queue) {
        long now = System.currentTimeMillis();
        Entry head;
        while ((head = queue.peekFirst()) != null && now - head.timestampMs > PENDING_TTL_MS) {
            queue.pollFirst();
        }
    }

    public static final class Entry {
        public final int lpCost;
        public final boolean deathMode;
        public final float manaCost;
        public final long timestampMs;

        Entry(int lpCost, boolean deathMode, float manaCost, long timestampMs) {
            this.lpCost = lpCost;
            this.deathMode = deathMode;
            this.manaCost = manaCost;
            this.timestampMs = timestampMs;
        }
    }
}
