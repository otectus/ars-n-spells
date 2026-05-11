package com.otectus.arsnspells.compat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player pending LP cost staged by {@code MixinScrollItem}'s HEAD inject
 * and consumed by its RETURN inject. Lives outside the mixin package because
 * Sponge Mixin forbids direct references to classes (including inner classes
 * of a mixin) inside a defined mixin package — the mixin's own bytecode is
 * merged into the target class at runtime, so an inner class
 * {@code MixinX$Y} would have no resolvable home.
 *
 * <p>Keyed by player UUID so concurrent multiplayer scroll uses don't collide.
 * Pre-cast HEAD calls {@link #stage}; post-cast RETURN calls {@link #take} to
 * atomically remove and act on the entry.
 */
public final class ScrollLPTracker {
    private static final Map<UUID, Entry> PENDING = new ConcurrentHashMap<>();

    private ScrollLPTracker() {}

    public static void stage(UUID uuid, int lpCost, boolean deathMode) {
        PENDING.put(uuid, new Entry(lpCost, deathMode, System.currentTimeMillis()));
    }

    public static Entry take(UUID uuid) {
        return PENDING.remove(uuid);
    }

    public static final class Entry {
        public final int lpCost;
        public final boolean deathMode;
        public final long timestampMs;

        Entry(int lpCost, boolean deathMode, long timestampMs) {
            this.lpCost = lpCost;
            this.deathMode = deathMode;
            this.timestampMs = timestampMs;
        }
    }
}
