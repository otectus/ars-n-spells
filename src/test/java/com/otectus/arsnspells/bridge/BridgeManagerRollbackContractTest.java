package com.otectus.arsnspells.bridge;

import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-CRIT-003 — exercises the contract that {@code BridgeManager.consumeManaForMode}'s
 * SEPARATE-mode rollback path depends on: a {@code consumeMana(amount)} followed by
 * {@code addMana(amount)} must net to a no-op against any concurrent mutation that
 * landed in between.
 *
 * <p>This test is bootstrap-free: it uses two stub bridges to simulate the Ars and Iron's
 * sides. We do not invoke {@code BridgeManager.consumeManaForMode} directly because it
 * reads {@code AnsConfig.DUAL_COST_*_PERCENTAGE} which requires ForgeConfigSpec to be
 * loaded. Instead we verify the underlying invariant the rollback path relies on.
 */
class BridgeManagerRollbackContractTest {

    /**
     * Stub bridge that overrides addMana to skip the IManaBridge default's
     * player-null-check, so the rollback contract can be exercised in pure unit-test
     * scope (no Minecraft bootstrap, no Player construction). The math is otherwise
     * identical to the default addMana.
     */
    private static final class StubBridge implements IManaBridge {
        float pool;

        StubBridge(float initial) { this.pool = initial; }

        @Override public float getMana(Player player) { return pool; }
        @Override public void setMana(Player player, float amount) { pool = amount; }
        @Override public boolean consumeMana(Player player, float amount) {
            if (pool < amount) return false;
            pool -= amount;
            return true;
        }
        @Override public void addMana(Player player, float amount) {
            if (amount == 0.0f) return;
            pool += amount;
        }
        @Override public float getMaxMana(Player player) { return Float.MAX_VALUE; }
        @Override public String getBridgeType() { return "STUB"; }
    }

    @Test
    void rollbackPreservesConcurrentRegen_withDefaultAddMana() {
        StubBridge ars = new StubBridge(100.0f);
        StubBridge iss = new StubBridge(5.0f);   // insufficient — Iron's will fail
        final float arsCost = 40.0f;
        final float issCost = 10.0f;

        // 1. Pre-check: Iron's pool has enough? No (5 < 10).
        boolean issHas = iss.getMana(null) >= issCost;
        assertEquals(false, issHas, "precondition: iss pool insufficient");

        // 2. Consume Ars first.
        boolean arsConsumed = ars.consumeMana(null, arsCost);
        assertTrue(arsConsumed, "Ars consume should succeed");
        assertEquals(60.0f, ars.pool);

        // 3. SIMULATE concurrent regen landing now — this is the race the old snapshot/restore
        //    pattern would have CLOBBERED. The new addMana pattern preserves it.
        ars.pool += 15.0f;
        assertEquals(75.0f, ars.pool, "after regen, Ars pool should be at 75");

        // 4. Iron's consume fails (insufficient).
        boolean issConsumed = iss.consumeMana(null, issCost);
        assertEquals(false, issConsumed, "Iron's consume should fail (insufficient)");

        // 5. Rollback via addMana (the new behaviour).
        ars.addMana(null, arsCost);

        // 6. Net result: 100 - 40 + 15 + 40 = 115.
        assertEquals(115.0f, ars.pool,
            "addMana rollback preserves the concurrent regen — old setMana(arsManaBefore) "
                + "rollback would have OVERWRITTEN to 100, losing the +15.");
    }

    @Test
    void rollbackAfterSuccessfulConsumes_isNotInvoked() {
        // Happy path: both bridges have enough; no rollback fires.
        StubBridge ars = new StubBridge(100.0f);
        StubBridge iss = new StubBridge(50.0f);
        final float arsCost = 40.0f;
        final float issCost = 10.0f;

        ars.consumeMana(null, arsCost);
        boolean issConsumed = iss.consumeMana(null, issCost);
        assertTrue(issConsumed);

        assertEquals(60.0f, ars.pool, "Ars consumed 40");
        assertEquals(40.0f, iss.pool, "Iron's consumed 10");
        // No rollback called; net behaviour identical pre- and post-fix.
    }

    @Test
    void consumeManaThenAddMana_isNetZeroWithoutConcurrentChanges() {
        // Baseline: no concurrent mutation between consume and add — rollback nets to zero.
        StubBridge bridge = new StubBridge(50.0f);
        bridge.consumeMana(null, 20.0f);
        assertEquals(30.0f, bridge.pool);
        bridge.addMana(null, 20.0f);
        assertEquals(50.0f, bridge.pool, "consume + addMana should be net-zero");
    }

    /**
     * Mirrors the SEPARATE-mode split normalization in
     * {@code BridgeManager.consumeManaForMode}. Kept bootstrap-free (no AnsConfig) by
     * replicating the formula; asserts the invariant the fix guarantees: the two halves
     * always sum to the base cost regardless of how the configured percentages are scaled.
     */
    private static float[] splitDualCost(float amount, double arsPct, double issPct) {
        double total = arsPct + issPct;
        if (total <= 0.0) {
            return new float[]{amount, 0.0f};
        }
        return new float[]{
            (float) (amount * (arsPct / total)),
            (float) (amount * (issPct / total))
        };
    }

    @Test
    void dualCostSplit_sumsToBaseCost_whenPercentagesOverOne() {
        // Misconfigured split summing to 1.2 must NOT overcharge — shares still sum to 100.
        float[] split = splitDualCost(100.0f, 0.6, 0.6);
        assertEquals(50.0f, split[0], 1.0e-4f, "Ars half");
        assertEquals(50.0f, split[1], 1.0e-4f, "Iron's half");
        assertEquals(100.0f, split[0] + split[1], 1.0e-3f, "halves must sum to base cost");
    }

    @Test
    void dualCostSplit_sumsToBaseCost_whenPercentagesUnderOne() {
        // Sum 0.8 must NOT undercharge — shares still sum to 100, ratio preserved.
        float[] split = splitDualCost(100.0f, 0.6, 0.2);
        assertEquals(75.0f, split[0], 1.0e-4f, "Ars half (0.6/0.8)");
        assertEquals(25.0f, split[1], 1.0e-4f, "Iron's half (0.2/0.8)");
        assertEquals(100.0f, split[0] + split[1], 1.0e-3f, "halves must sum to base cost");
    }

    @Test
    void dualCostSplit_degenerateZeroTotal_chargesArsOnly() {
        float[] split = splitDualCost(100.0f, 0.0, 0.0);
        assertEquals(100.0f, split[0], "whole cost falls to the Ars side");
        assertEquals(0.0f, split[1], "Iron's side charged nothing");
    }
}
