package com.otectus.arsnspells.augmentation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResonanceStateTest {
    @Test void thresholdTriggersAndLingerExpiresWithoutBeingRefreshedByLowMana() {
        var state = ResonanceState.update(null, 10, 94, 100, .95, 100, 1, 5);
        assertEquals(1, state.multiplier());
        state = ResonanceState.update(state, 11, 95, 100, .95, 100, 1, 5);
        assertEquals(1.19, state.multiplier(), 1e-8);
        for (int tick = 12; tick < 111; tick++) {
            state = ResonanceState.update(state, tick, 0, 100, .95, 100, 1, 5);
            assertEquals(111, state.expiresAt());
            assertEquals(1.19, state.multiplier(), 1e-8);
        }
        assertEquals(1, ResonanceState.update(state, 111, 0, 100, .95, 100, 1, 5).multiplier());
    }

    @Test void zeroDurationAndCapsApplyAtTheNextTick() {
        var state = ResonanceState.update(null, 0, 100, 100, .95, 0, 10, 1.5);
        assertEquals(1.5, state.multiplier());
        assertEquals(1, ResonanceState.update(state, 1, 94, 100, .95, 0, 10, 1.5).multiplier());
        assertEquals(1, ResonanceState.update(state, 1, Double.NaN, 100, .95, 0, 10, 1.5).multiplier());
    }
}
