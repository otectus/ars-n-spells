package com.otectus.arsnspells.compat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ANS-MED-042 — verifies {@link ScrollLPTracker} queues per-player entries FIFO
 * instead of overwriting a single slot. With the single slot, a second scroll
 * HEAD before the first's RETURN clobbered the first's staged cost, so the
 * first commit charged the wrong amount (the same clobbering the ring handlers'
 * ANS-3.0.0 deque migration fixed).
 *
 * <p>ANS-MED-043 — entries also carry the staged mana cost for "full" scroll
 * cost mode.
 *
 * <p>Expiry is expressed in server game time (ticks), so these tests pass explicit
 * tick values instead of depending on wall-clock timing — which also makes the
 * expiry behaviour deterministically testable rather than requiring a sleep.
 */
class ScrollLPTrackerFifoTest {

    /** An arbitrary but fixed starting tick; the values only matter relative to each other. */
    private static final long T0 = 1_000L;

    @Test
    void backToBackStages_commitInOrder() {
        UUID id = UUID.randomUUID();
        ScrollLPTracker.stage(id, 50, false, T0);
        ScrollLPTracker.stage(id, 75, true, T0);

        ScrollLPTracker.Entry first = ScrollLPTracker.take(id, T0);
        ScrollLPTracker.Entry second = ScrollLPTracker.take(id, T0);

        assertEquals(50, first.lpCost, "first RETURN must commit the first staged cost");
        assertEquals(false, first.deathMode);
        assertEquals(75, second.lpCost, "second RETURN must commit the second staged cost");
        assertEquals(true, second.deathMode);
        assertNull(ScrollLPTracker.take(id, T0), "queue must be drained after both commits");
    }

    @Test
    void manaEntries_carryTheStagedCost() {
        UUID id = UUID.randomUUID();
        ScrollLPTracker.stage(id, 0, false, 42.5f, T0);

        ScrollLPTracker.Entry entry = ScrollLPTracker.take(id, T0);
        assertEquals(42.5f, entry.manaCost, 1e-6f);
        assertEquals(0, entry.lpCost);

        ScrollLPTracker.clear(id);
    }

    @Test
    void lpOnlyStage_hasZeroManaCost() {
        UUID id = UUID.randomUUID();
        ScrollLPTracker.stage(id, 30, false, T0);
        assertEquals(0.0f, ScrollLPTracker.take(id, T0).manaCost, 1e-6f,
            "the four-arg stage overload must not stage a mana cost");
    }

    @Test
    void entryWithinTtl_survives() {
        UUID id = UUID.randomUUID();
        ScrollLPTracker.stage(id, 40, false, T0);

        // 99 ticks later: still inside the 100-tick window, so the commit must find it.
        assertNotNull(ScrollLPTracker.take(id, T0 + 99),
            "a cost staged 99 ticks ago must still be committable; expiring it makes the cast free");
        ScrollLPTracker.clear(id);
    }

    @Test
    void entryPastTtl_isEvicted() {
        UUID id = UUID.randomUUID();
        ScrollLPTracker.stage(id, 40, false, T0);

        assertNull(ScrollLPTracker.take(id, T0 + 101),
            "a cost staged beyond the TTL must be evicted rather than charged to a later cast");
        ScrollLPTracker.clear(id);
    }

    /**
     * The expiry clock must be the caller-supplied game time, not wall-clock. A frozen or
     * lagging server advances game time slowly (or not at all), and a staged cost must not
     * expire out from under its own cast just because real seconds elapsed.
     */
    @Test
    void expiryTracksGameTimeNotWallClock() throws InterruptedException {
        UUID id = UUID.randomUUID();
        ScrollLPTracker.stage(id, 60, false, T0);

        Thread.sleep(50); // real time passes; game time does not

        ScrollLPTracker.Entry entry = ScrollLPTracker.take(id, T0);
        assertNotNull(entry, "wall-clock elapsed time must not expire a game-time transaction");
        assertEquals(60, entry.lpCost);
        assertEquals(T0, entry.stagedGameTime, "entries record the game time they were staged at");
    }
}
