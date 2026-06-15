package com.otectus.arsnspells.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the client aura-peak ratchet. {@code updatePeak}/{@code getPeak}/{@code reset}
 * touch no Minecraft classes (only an {@link java.util.concurrent.atomic.AtomicInteger}), so
 * they are genuinely unit-testable without a Mod-loading bootstrap — unlike the tick/render
 * paths. Covers the monotonic ratchet, the divisor floor (the mixin uses the value as an
 * integer divisor, so it must never reach 0), and reset-on-disconnect.
 */
class ClientAuraPeakTrackerTest {

    @BeforeEach
    void resetTracker() {
        ClientAuraPeakTracker.reset();
    }

    @Test
    void getPeak_neverBelowFloor() {
        assertEquals(1, ClientAuraPeakTracker.getPeak(), "initial peak is the floor");
        ClientAuraPeakTracker.updatePeak(-50);
        assertEquals(1, ClientAuraPeakTracker.getPeak(), "negative samples must not drop the divisor below 1");
        ClientAuraPeakTracker.updatePeak(0);
        assertEquals(1, ClientAuraPeakTracker.getPeak(), "zero must not drop the divisor below 1");
    }

    @Test
    void updatePeak_ratchetsUpOnly() {
        ClientAuraPeakTracker.updatePeak(500);
        assertEquals(500, ClientAuraPeakTracker.getPeak());
        ClientAuraPeakTracker.updatePeak(200); // lower sample — ignored
        assertEquals(500, ClientAuraPeakTracker.getPeak(), "ratchet must not decrease");
        ClientAuraPeakTracker.updatePeak(900); // higher sample — adopted
        assertEquals(900, ClientAuraPeakTracker.getPeak());
    }

    @Test
    void reset_returnsToFloor() {
        ClientAuraPeakTracker.updatePeak(12345);
        assertEquals(12345, ClientAuraPeakTracker.getPeak());
        ClientAuraPeakTracker.reset();
        assertEquals(1, ClientAuraPeakTracker.getPeak(), "disconnect reset returns the peak to the floor");
    }
}
