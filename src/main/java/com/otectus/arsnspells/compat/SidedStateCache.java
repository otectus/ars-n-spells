package com.otectus.arsnspells.compat;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;

/**
 * A short-lived per-player cache partitioned by logical side and by player instance (audit ANS-003).
 *
 * <p>In an integrated server the local client player and the authoritative server player share a
 * UUID but have independent, asynchronously synchronized inventories. One UUID-keyed map let a
 * client HUD scan answer a server payment decision and the reverse. Here each side has its own map,
 * an entry answers only the exact player object that produced it (held weakly, so no {@code Player}
 * or {@code Level} is retained), and entries expire after {@code ttl} ticks. Removal is explicit:
 * per player, per side, and a sweep down to the players still online.
 *
 * @param <V> the cached state
 */
final class SidedStateCache<V> {
    private record Entry<V>(V value, WeakReference<Object> owner, long at) {}

    private final ConcurrentHashMap<UUID, Entry<V>> server = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Entry<V>> client = new ConcurrentHashMap<>();
    private final long ttl;

    SidedStateCache(long ttl) {
        this.ttl = ttl;
    }

    private ConcurrentHashMap<UUID, Entry<V>> side(boolean clientSide) {
        return clientSide ? client : server;
    }

    /** The cached value for this exact {@code owner} on its side, or a fresh {@code scan(now)} stored for it. */
    V get(boolean clientSide, UUID id, Object owner, long now, LongFunction<V> scan) {
        ConcurrentHashMap<UUID, Entry<V>> map = side(clientSide);
        Entry<V> cached = map.get(id);
        if (cached != null && cached.owner().get() == owner && now >= cached.at() && now - cached.at() < ttl) {
            return cached.value();
        }
        V fresh = scan.apply(now);
        map.put(id, new Entry<>(fresh, new WeakReference<>(owner), now));
        return fresh;
    }

    /** A fresh value that also replaces the cached one, for a decision that must not read a stale entry. */
    V refresh(boolean clientSide, UUID id, Object owner, long now, LongFunction<V> scan) {
        V fresh = scan.apply(now);
        side(clientSide).put(id, new Entry<>(fresh, new WeakReference<>(owner), now));
        return fresh;
    }

    void remove(boolean clientSide, UUID id) {
        if (id != null) side(clientSide).remove(id);
    }

    void removeBoth(UUID id) {
        if (id == null) return;
        server.remove(id);
        client.remove(id);
    }

    void clear(boolean clientSide) {
        side(clientSide).clear();
    }

    /** Drop every server entry whose player is not online, and any whose player object is gone. */
    void retainServer(Collection<UUID> online) {
        Set<UUID> keep = Set.copyOf(online);
        server.entrySet().removeIf(entry -> !keep.contains(entry.getKey()) || entry.getValue().owner().get() == null);
    }

    int size(boolean clientSide) {
        return side(clientSide).size();
    }
}
