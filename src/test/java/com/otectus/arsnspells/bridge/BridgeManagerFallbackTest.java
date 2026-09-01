package com.otectus.arsnspells.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * ANS-OPT-016 - {@link BridgeManager#getBridge()} must return a cached singleton fallback when
 * {@code activeBridge} is null, rather than allocating a new {@link ArsNativeBridge} per call.
 *
 * <p>The null window is real, not theoretical: bridges are built at common setup and rebuilt on
 * config load, so every call before that - including early client render frames during world
 * load - lands on the fallback.
 */
class BridgeManagerFallbackTest {

    @Test
    void getBridge_returnsNonNullEvenBeforeInit() {
        IManaBridge bridge = BridgeManager.getBridge();
        assertNotNull(bridge,
            "getBridge() must never return null - the fallback exists exactly for this case");
    }

    @Test
    void getBridge_returnsSameInstanceOnRepeatedCalls() {
        IManaBridge first = BridgeManager.getBridge();
        IManaBridge second = BridgeManager.getBridge();
        assertSame(first, second,
            "the fallback must be a cached singleton, not allocated per call");
    }

    @Test
    void getBridge_returnsArsNativeBridge_whenUninitialized() {
        // Asserted through the type string rather than instanceof: ArsNativeBridge is not
        // guaranteed to be loaded in this static context.
        assertEquals("ARS_NATIVE", BridgeManager.getBridge().getBridgeType());
    }
}
