package com.otectus.arsnspells.compat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ANS-HIGH-025 — verifies the {@code ScrollLPTracker.clear(UUID)} hook added so
 * {@code CursedRingHandler.onPlayerLoggedOut} can drain a leaked entry whose
 * companion {@code MixinScrollItem} RETURN inject was suppressed.
 *
 * <p>Mirrors {@link ScrollAuraTracker} which already had a {@code clear(UUID)}
 * method.
 */
class ScrollLPTrackerClearTest {

    @Test
    void stageThenClear_removesTheEntry() {
        UUID id = UUID.randomUUID();
        ScrollLPTracker.stage(id, 50, false);
        assertNotNull(ScrollLPTracker.take(id), "preconditions: stage and take work");

        // Re-stage and clear via the new clear() hook.
        ScrollLPTracker.stage(id, 25, true);
        ScrollLPTracker.clear(id);
        assertNull(ScrollLPTracker.take(id),
            "clear(id) must remove the staged entry so subsequent take() returns null");
    }

    @Test
    void clearOnUnknownUuid_isANoOp() {
        // Defensive: never throw on an unknown UUID.
        ScrollLPTracker.clear(UUID.randomUUID());
        // No assertion needed — the test passes if no exception is thrown.
    }
}
