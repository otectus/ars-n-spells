package com.otectus.arsnspells.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaBarVisibilityTest {
    @Test
    void fullFractionalMaximumHidesAtPacketAndHudPrecision() {
        // Server has filled a fractional pool, but SyncManaPacket transmits 1001.
        assertTrue(ManaBarVisibility.shouldHideContextualBar(true, false, 1001, 1001.75));
        assertTrue(ManaBarVisibility.shouldHideContextualBar(true, false, 1001, Math.nextUp(1001.0)));
    }

    @Test
    void missingWholeManaRemainsVisibleUntilRefilled() {
        assertFalse(ManaBarVisibility.shouldHideContextualBar(true, false, 1000, 1001.75));
        assertFalse(ManaBarVisibility.shouldHideContextualBar(true, false, 1000, 1001.0));
        assertTrue(ManaBarVisibility.shouldHideContextualBar(true, false, 1001, 1001.0));
        assertTrue(ManaBarVisibility.shouldHideContextualBar(true, false, 1002, 1001.75));
    }

    @Test
    void fullManaStillShowsForHeldCastingItems() {
        assertFalse(ManaBarVisibility.shouldHideContextualBar(true, true, 1001, 1001.75));
        assertFalse(ManaBarVisibility.shouldHideContextualBar(true, true, 1001, 1001.0));
    }

    @Test
    void otherDisplayModesAreUnchanged() {
        assertFalse(ManaBarVisibility.shouldHideContextualBar(false, false, 1001, 1001.75));
    }

    @Test
    void zeroAndSmallPoolsUseTheDisplayedMaximum() {
        assertTrue(ManaBarVisibility.shouldHideContextualBar(true, false, 0, 0.0));
        assertTrue(ManaBarVisibility.shouldHideContextualBar(true, false, 0, 0.75));
        assertFalse(ManaBarVisibility.shouldHideContextualBar(true, false, 0, 1.0));
    }

    @Test
    void invalidMaximaLeaveNativeVisibilityAlone() {
        for (double maxMana : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, -1.0}) {
            assertFalse(ManaBarVisibility.shouldHideContextualBar(true, false, 1001, maxMana));
        }
    }
}
