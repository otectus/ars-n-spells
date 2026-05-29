package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.ManaUnificationMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * ANS 2.0.1 — verifies the live mode-switch seam. {@link BridgeManager#testSetMode}
 * sets the cached mode without constructing real bridges, so {@link BridgeManager#getCurrentMode}
 * reflects a runtime change. Bootstrap-free: no ForgeConfigSpec load, no {@code Player}.
 * ({@code refreshMode()} additionally re-runs bridge selection and is exercised in-game,
 * not here, since that constructs mod-API-backed bridges.)
 */
class BridgeManagerModeTest {

    @Test
    void testSetMode_isReflectedByGetCurrentMode() {
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            BridgeManager.testSetMode(mode);
            assertSame(mode, BridgeManager.getCurrentMode(),
                "getCurrentMode() must return the mode set via the runtime seam");
        }
    }

    @Test
    void configName_roundTripsThroughFromString() {
        // The command + screen identify modes by getConfigName(); fromString must invert it
        // so a mode written by one path is parsed identically by another.
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            assertSame(mode, ManaUnificationMode.fromString(mode.getConfigName()),
                "fromString(getConfigName()) must round-trip for " + mode);
        }
    }
}
