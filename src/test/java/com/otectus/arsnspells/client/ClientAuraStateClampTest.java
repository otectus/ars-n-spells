package com.otectus.arsnspells.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-005 (consumer-side defense) — verifies that
 * {@link ClientAuraState#update} clamps both inputs into the invariant range
 * {@code aura in [0, maxAura]} and {@code maxAura in [1, 100_000]}, even if a
 * stray caller bypasses the packet decoder's clamp.
 */
class ClientAuraStateClampTest {

    @BeforeEach
    void setup() {
        ClientAuraState.reset();
    }

    @AfterEach
    void teardown() {
        ClientAuraState.reset();
    }

    @Test
    void normalValues_storedUnchanged() {
        ClientAuraState.update(50, 100);
        assertEquals(50, ClientAuraState.getAura());
        assertEquals(100, ClientAuraState.getMaxAura());
    }

    @Test
    void maxAuraIntegerMaxValue_clampedTo100k() {
        ClientAuraState.update(50, Integer.MAX_VALUE);
        assertEquals(100_000, ClientAuraState.getMaxAura());
    }

    @Test
    void auraGreaterThanMaxAura_clampedDown() {
        ClientAuraState.update(Integer.MAX_VALUE, 10);
        assertEquals(10, ClientAuraState.getMaxAura());
        assertEquals(10, ClientAuraState.getAura(),
            "aura must be clamped to maxAura so the render fraction is in [0,1]");
        assertTrue(ClientAuraState.getAura() <= ClientAuraState.getMaxAura());
    }

    @Test
    void zeroMaxAura_raisedToOne() {
        ClientAuraState.update(0, 0);
        assertEquals(1, ClientAuraState.getMaxAura(),
            "maxAura must be at least 1 to avoid divide-by-zero in HUD render math");
    }

    @Test
    void negativeAura_raisedToZero() {
        ClientAuraState.update(-999, 100);
        assertEquals(0, ClientAuraState.getAura());
    }
}
