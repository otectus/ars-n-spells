package com.otectus.arsnspells.compat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Audit ANS-003: the ring-state cache keeps the integrated client's and the server's answers apart,
 * answers only the player object that produced an entry, and is bounded by explicit removal.
 */
class SidedStateCacheTest {
    private static final UUID SAME = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void clientAndServerWithTheSameUuidKeepTheirOwnResult() {
        SidedStateCache<String> cache = new SidedStateCache<>(20);
        Object serverPlayer = new Object(), clientPlayer = new Object();
        assertEquals("server:virtue", cache.get(false, SAME, serverPlayer, 100, t -> "server:virtue"));
        assertEquals("client:none", cache.get(true, SAME, clientPlayer, 100, t -> "client:none"));
        // Within the TTL each side is served its own entry, never the other side's.
        assertEquals("server:virtue", cache.get(false, SAME, serverPlayer, 105, t -> "rescanned"));
        assertEquals("client:none", cache.get(true, SAME, clientPlayer, 105, t -> "rescanned"));
    }

    @Test
    void aClientScanAfterServerInvalidationCannotAnswerTheServer() {
        SidedStateCache<String> cache = new SidedStateCache<>(20);
        Object serverPlayer = new Object(), clientPlayer = new Object();
        cache.get(false, SAME, serverPlayer, 100, t -> "server:old");
        cache.remove(false, SAME);
        cache.get(true, SAME, clientPlayer, 101, t -> "client:stale-ring");
        assertEquals("server:now", cache.get(false, SAME, serverPlayer, 102, t -> "server:now"));
    }

    @Test
    void aReplacedPlayerObjectIsScannedAfresh() {
        SidedStateCache<String> cache = new SidedStateCache<>(20);
        AtomicInteger scans = new AtomicInteger();
        Object before = new Object(), afterClone = new Object();
        cache.get(false, SAME, before, 100, t -> "a" + scans.incrementAndGet());
        assertEquals("a2", cache.get(false, SAME, afterClone, 101, t -> "a" + scans.incrementAndGet()),
            "a clone or respawn is a new player object and must not inherit the old body's rings");
    }

    @Test
    void ttlAndNonMonotonicTimeRescan() {
        SidedStateCache<String> cache = new SidedStateCache<>(20);
        Object player = new Object();
        cache.get(false, SAME, player, 100, t -> "first");
        assertEquals("expired", cache.get(false, SAME, player, 120, t -> "expired"));
        assertEquals("rewound", cache.get(false, SAME, player, 50, t -> "rewound"));
    }

    @Test
    void refreshBypassesTheTtl() {
        SidedStateCache<String> cache = new SidedStateCache<>(20);
        Object player = new Object();
        cache.get(false, SAME, player, 100, t -> "cached");
        assertEquals("fresh", cache.refresh(false, SAME, player, 101, t -> "fresh"));
        assertEquals("fresh", cache.get(false, SAME, player, 102, t -> "unused"));
    }

    @Test
    void sweepReturnsToTheOnlineBoundAndStopEmptiesEachSide() {
        SidedStateCache<String> cache = new SidedStateCache<>(20);
        Object[] players = new Object[50];
        UUID online = null;
        for (int i = 0; i < 50; i++) {
            players[i] = new Object();
            UUID id = new UUID(0, i);
            if (i == 7) online = id;
            cache.get(false, id, players[i], 100, t -> "v");
        }
        cache.get(true, SAME, new Object(), 100, t -> "client");
        assertEquals(50, cache.size(false));
        cache.retainServer(List.of(online));
        assertEquals(1, cache.size(false), "only the online player's entry survives the sweep");
        assertEquals(1, cache.size(true), "the server sweep leaves the client's cache alone");
        cache.clear(false);
        assertEquals(0, cache.size(false));
        cache.clear(true);
        assertEquals(0, cache.size(true));
    }
}
