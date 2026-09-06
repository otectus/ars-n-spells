package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * ANS 2.0.1 — verifies the live mode-switch seam. The test seam rebuilds the routing
 * snapshot without constructing real bridges, so {@link BridgeManager#getCurrentMode}
 * reflects a runtime change. Bootstrap-free: no ForgeConfigSpec load, no {@code Player}.
 * ({@code refreshMode()} additionally reads the config and the mod list, and is exercised
 * in-game, not here.)
 *
 * <p>Audit V04 changed what {@code getCurrentMode()} reports: it is now a thin read of the
 * routing snapshot's <em>effective</em> mode. With Iron's absent there is no second pool, so
 * every unified mode is effectively disabled — the invariant {@code ModeRoutingSnapshot}
 * enforces, and the reason SEPARATE-without-Iron's can no longer expose a stale secondary.
 * The old code half-did this already, falling back to ARS_PRIMARY, which billed the same pool
 * while still reporting a unified mode to every caller that asked.
 */
class BridgeManagerModeTest {

    /** Adapters are irrelevant here; only the snapshot is under test. */
    private static final class NoopBridge implements IManaBridge {
        private final String type;

        NoopBridge(String type) { this.type = type; }

        @Override public float getMana(Player p) { return 0.0f; }
        @Override public void setMana(Player p, float a) { }
        @Override public boolean consumeMana(Player p, float a) { return false; }
        @Override public float getMaxMana(Player p) { return 0.0f; }
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
        // The command + screen identify modes by getConfigName(); fromString must invert it
        // so a mode written by one path is parsed identically by another.
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            assertSame(mode, ManaUnificationMode.fromString(mode.getConfigName()),
                "fromString(getConfigName()) must round-trip for " + mode);
        }
    }
}
