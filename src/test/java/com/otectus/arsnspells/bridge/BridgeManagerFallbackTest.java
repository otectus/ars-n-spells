package com.otectus.arsnspells.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * ANS-OPT-016 piggyback — verifies that {@link BridgeManager#getBridge()} returns
 * a cached singleton fallback when {@code activeBridge} is null, rather than
 * allocating a new {@link ArsNativeBridge} per call.
 */
class BridgeManagerFallbackTest {

    @Test
    void getBridge_returnsNonNullEvenBeforeInit() {
        IManaBridge bridge = BridgeManager.getBridge();
        assertNotNull(bridge,
            "getBridge() must never return null — the fallback exists exactly for this case");
    }

    @Test
    void getBridge_returnsSameInstanceOnRepeatedCalls() {
        // ANS-OPT-016: cached fallback, no per-call allocation.
        IManaBridge first = BridgeManager.getBridge();
        IManaBridge second = BridgeManager.getBridge();
        assertSame(first, second,
            "the fallback must be a cached singleton, not allocated per call");
    }

    @Test
    void getBridge_returnsArsNativeBridge_whenUninitialized() {
        IManaBridge bridge = BridgeManager.getBridge();
        // The fallback is ArsNativeBridge; type assertion via bridge type string
        // since ArsNativeBridge isn't always loaded in the static context.
        assertEquals("ARS_NATIVE", bridge.getBridgeType());
    }
}
