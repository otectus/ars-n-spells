package com.otectus.arsnspells.compat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 */
class ScrollLPTrackerFifoTest {

    @Test
    void backToBackStages_commitInOrder() {
        UUID id = UUID.randomUUID();
        ScrollLPTracker.stage(id, 50, false);
        ScrollLPTracker.stage(id, 75, true);

        ScrollLPTracker.Entry first = ScrollLPTracker.take(id);
        ScrollLPTracker.Entry second = ScrollLPTracker.take(id);

        assertEquals(50, first.lpCost, "first RETURN must commit the first staged cost");
        assertEquals(false, first.deathMode);
        assertEquals(75, second.lpCost, "second RETURN must commit the second staged cost");
        assertEquals(true, second.deathMode);
        assertNull(ScrollLPTracker.take(id), "queue must be drained after both commits");
    }

    @Test
    void manaEntries_carryTheStagedCost() {
        UUID id = UUID.randomUUID();
        ScrollLPTracker.stage(id, 0, false, 42.5f);

        ScrollLPTracker.Entry entry = ScrollLPTracker.take(id);
        assertEquals(42.5f, entry.manaCost, 1e-6f);
        assertEquals(0, entry.lpCost);

        ScrollLPTracker.clear(id);
    }

    @Test
    void lpOnlyStage_hasZeroManaCost() {
        UUID id = UUID.randomUUID();
        ScrollLPTracker.stage(id, 30, false);
        assertEquals(0.0f, ScrollLPTracker.take(id).manaCost, 1e-6f,
            "the three-arg stage overload must not stage a mana cost");
    }
}
