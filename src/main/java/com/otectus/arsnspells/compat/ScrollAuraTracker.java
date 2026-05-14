package com.otectus.arsnspells.compat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player pending aura cost staged by {@code MixinScrollItem}'s HEAD inject for
 * the Virtue Ring path, and consumed by its RETURN inject. Mirrors
 * {@link ScrollLPTracker}.
 *
 * <p>Lives outside the mixin package because Sponge Mixin forbids direct references
 * to classes (including inner classes of a mixin) inside a defined mixin package.
 *
 * <p>Keyed by player UUID so concurrent multiplayer scroll uses don't collide.
 * Pre-cast HEAD calls {@link #stage}; post-cast RETURN calls {@link #take} to
 * atomically remove and act on the entry.
 */
public final class ScrollAuraTracker {
    private static final Map<UUID, Entry> PENDING = new ConcurrentHashMap<>();

    private ScrollAuraTracker() {}

    public static void stage(UUID uuid, int auraCost) {
        PENDING.put(uuid, new Entry(auraCost, System.currentTimeMillis()));
    }

    public static Entry take(UUID uuid) {
        return PENDING.remove(uuid);
    }

    public static void clear(UUID uuid) {
        PENDING.remove(uuid);
    }

    public static final class Entry {
        public final int auraCost;
        public final long timestampMs;

        Entry(int auraCost, long timestampMs) {
            this.auraCost = auraCost;
            this.timestampMs = timestampMs;
        }
    }
}
