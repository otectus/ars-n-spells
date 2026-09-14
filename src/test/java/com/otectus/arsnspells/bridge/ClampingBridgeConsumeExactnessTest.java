package com.otectus.arsnspells.bridge;

import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A spell must cost the spell's cost — not "everything above the ceiling".
 *
 * <p>Every other bridge stub in this suite returns {@link Float#MAX_VALUE} from
 * {@code getMaxMana}, which is exactly the condition under which the reported hybrid-mode
 * drain is invisible. The stub here instead reproduces Iron's real write semantics,
 * transcribed from the shipped bytecode:
 *
 * <pre>
 *   setMana(v) { mana = v; if (mana &gt; maxAttr) mana = maxAttr; }   // no lower clamp
 *   addMana(v) { setMana(mana + v); }
 * </pre>
 *
 * <p>With those semantics a deduction is only a deduction when the ceiling can hold the
 * pool, which is the invariant {@link SharedPoolCeiling} exists to maintain. The live proof
 * against real Iron's lives in the Iron's-loaded GameTest profile; this keeps the arithmetic
 * honest on every {@code ./gradlew test}.
 */
class ClampingBridgeConsumeExactnessTest {

    /** Bridge stub with Iron's clamping write semantics. */
    private static final class ClampingStubBridge implements IManaBridge {
        float pool;
        float ceiling;

        ClampingStubBridge(float pool, float ceiling) {
            this.pool = pool;
            this.ceiling = ceiling;
        }

        @Override public float getMana(Player player) { return pool; }

        @Override public void setMana(Player player, float amount) {
            pool = amount;
            if (pool > ceiling) {
                pool = ceiling;   // Iron's clamps down on every write, unconditionally.
            }
        }

        @Override public void addMana(Player player, float amount) {
            setMana(player, pool + amount);
        }

        @Override public boolean consumeMana(Player player, float amount) {
            if (pool < amount) {
                return false;
            }
            addMana(player, -amount);
            return true;
        }

        @Override public float getMaxMana(Player player) { return ceiling; }
        @Override public String getBridgeType() { return "STUB_CLAMPING"; }
    }

    @Test
    void ceilingBelowPool_makesACheapSpellCostEverything() {
        // The reported scenario: 2000 in the pool, a 300-mana spell, ceiling still at
        // Iron's bare base. This documents the upstream hazard; it is not desired behaviour.
        ClampingStubBridge bridge = new ClampingStubBridge(2000.0f, 100.0f);

        assertTrue(bridge.consumeMana(null, 300.0f),
            "the sufficiency check reads the raw pool, so the cast is authorised");
        assertEquals(100.0f, bridge.pool,
            "…and then the clamp collapses the pool to the ceiling, losing 1600 mana");
    }

    @Test
    void ceilingRaisedToCoverPool_makesConsumeLossExact() {
        float arsMax = 2000.0f;
        float ironsBase = 100.0f;
        float ceiling = (float) SharedPoolCeiling.resultingCeiling(ironsBase, arsMax);

        ClampingStubBridge bridge = new ClampingStubBridge(2000.0f, ceiling);

        assertTrue(bridge.consumeMana(null, 300.0f));
        assertEquals(1700.0f, bridge.pool, 1.0e-3f,
            "with the ceiling synced, a 300-mana spell costs exactly 300 mana");
    }

    @Test
    void repeatedCasts_stayLossExact() {
        float ceiling = (float) SharedPoolCeiling.resultingCeiling(100.0, 2000.0);
        ClampingStubBridge bridge = new ClampingStubBridge(2000.0f, ceiling);

        for (int i = 0; i < 5; i++) {
            assertTrue(bridge.consumeMana(null, 300.0f), "cast " + i + " should be affordable");
        }
        assertEquals(500.0f, bridge.pool, 1.0e-3f,
            "five 300-mana casts from 2000 must leave 500");
    }

    @Test
    void insufficientMana_isRefusedWithoutTouchingThePool() {
        ClampingStubBridge bridge = new ClampingStubBridge(200.0f, 2000.0f);

        assertFalse(bridge.consumeMana(null, 300.0f));
        assertEquals(200.0f, bridge.pool,
            "a refused cast must not write at all — a write would clamp and could lose mana");
    }

    @Test
    void ceilingAboveIronsOwnMax_isNeverLowered() {
        // Iron's own gear gives a bigger pool than Ars asks for; syncing must not shrink it.
        float ceiling = (float) SharedPoolCeiling.resultingCeiling(2500.0, 300.0);
        ClampingStubBridge bridge = new ClampingStubBridge(2500.0f, ceiling);

        assertTrue(bridge.consumeMana(null, 300.0f));
        assertEquals(2200.0f, bridge.pool, 1.0e-3f,
            "Iron's own 2500 pool must spend normally, not collapse to the Ars max");
    }
}
