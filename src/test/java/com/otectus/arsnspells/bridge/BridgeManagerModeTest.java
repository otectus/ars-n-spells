package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.ManaUnificationMode;
import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.player.Player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The live mode-switch seam. The test seam rebuilds the routing snapshot without constructing
 * real bridges, so {@link BridgeManager#getCurrentMode} reflects a runtime change.
 * Bootstrap-free: no config spec load.
 *
 * <p>({@code refreshMode()} additionally re-runs bridge selection and is exercised in game, not
 * here, since that constructs mod-API-backed bridges.)
 */
class BridgeManagerModeTest {

    /** Adapters are irrelevant here; only the immutable routing snapshot is under test. */
    private static final class NoopBridge implements IManaBridge {
        private final String type;
        NoopBridge(String type) { this.type = type; }
        @Override public float getMana(Player player) { return 0.0f; }
        @Override public void setMana(Player player, float amount) { }
        @Override public boolean consumeMana(Player player, float amount) { return false; }
        @Override public float getMaxMana(Player player) { return 0.0f; }
        @Override public String getBridgeType() { return type; }
    }

    @Test
    void testSetRouting_isReflectedByGetCurrentMode_withIronsPresent() {
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            BridgeManager.testSetRouting(mode, true,
                new NoopBridge("ARS_NATIVE"), new NoopBridge("IRONS_SPELLS"));
            assertSame(mode, BridgeManager.getCurrentMode(),
                "getCurrentMode() must return the mode set via the runtime seam");
            assertEquals(mode.getConfigName(),
                BridgeManager.getRoutingSnapshot().requestedMode(),
                "the snapshot must also remember what was asked for");
        }
    }

    @Test
    void withoutIrons_everyUnifiedModeIsEffectivelyDisabled() {
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            BridgeManager.testSetRouting(mode, false, new NoopBridge("ARS_NATIVE"), null);
            assertSame(ManaUnificationMode.DISABLED, BridgeManager.getCurrentMode(),
                mode.getConfigName() + " has nothing to unify with Iron's absent");
            assertEquals(mode.getConfigName(),
                BridgeManager.getRoutingSnapshot().requestedMode(),
                "the requested mode is still reported, so an operator can see what was asked");
        }
    }

    @Test
    void configName_roundTripsThroughFromString() {
        // The command and the config screen identify modes by getConfigName(); fromString
        // must invert it so a mode written by one path is parsed identically by another.
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            assertSame(mode, ManaUnificationMode.fromString(mode.getConfigName()),
                "fromString(getConfigName()) must round-trip for " + mode);
        }
    }
}
