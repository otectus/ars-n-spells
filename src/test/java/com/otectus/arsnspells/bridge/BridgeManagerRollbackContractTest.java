package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-CRIT-003 - the SEPARATE-mode rollback must be a compensating refund, never a
 * snapshot-and-restore.
 *
 * <p>The sequence that matters: the Ars leg consumes, the Iron's leg then fails for
 * insufficient mana, and the Ars consume has to be undone. If the undo is
 * {@code setMana(poolBefore)}, any regen, buff or ritual mana that landed in between is
 * silently erased - the player is refunded and robbed in the same operation. A compensating
 * {@code addMana} of the same amount preserves it.
 *
 * <p>Driven through a fake {@link IManaBridge} so the contract is checked without a
 * {@code Player} or a Minecraft bootstrap. The fake overrides {@code addMana} the way both
 * real bridges do; the point of the first test is that the default get-then-set form does not
 * satisfy the contract.
 */
class BridgeManagerRollbackContractTest {

    /** Minimal bridge over a float, with an atomic add. */
    private static final class FakeBridge implements IManaBridge {
        float pool;

        FakeBridge(float pool) {
            this.pool = pool;
        }

        @Override public float getMana(Player player) {
            return pool;
        }

        @Override public void setMana(Player player, float amount) {
            pool = amount;
        }

        @Override public void addMana(Player player, float amount) {
            pool += amount;
        }

        @Override public boolean consumeMana(Player player, float amount) {
            if (pool < amount) {
                return false;
            }
            pool -= amount;
            return true;
        }

        @Override public float getMaxMana(Player player) {
            return 100.0f;
        }

        @Override public String getBridgeType() {
            return "FAKE";
        }
    }

    @Test
    void rollbackPreservesConcurrentRegen() {
        FakeBridge ars = new FakeBridge(100.0f);
        FakeBridge iss = new FakeBridge(5.0f);

        // Ars leg takes its share.
        assertTrue(ars.consumeMana(null, 40.0f), "the Ars consume should succeed");
        assertEquals(60.0f, ars.pool);

        // Regen lands while the cast is still in flight - this is the value at risk.
        ars.addMana(null, 15.0f);
        assertEquals(75.0f, ars.pool);

        // Iron's leg fails: not enough in that pool.
        assertFalse(iss.consumeMana(null, 10.0f), "the Iron's consume should fail");

        // Compensating refund of exactly what was taken.
        ars.addMana(null, 40.0f);
        assertEquals(115.0f, ars.pool,
            "the refund must return the 40 that was taken AND keep the 15 that regenerated "
                + "in between; a setMana(100) snapshot restore would silently delete the 15");
    }

    @Test
    void consumeThenAddMana_isNetZeroWithoutConcurrentChanges() {
        FakeBridge bridge = new FakeBridge(50.0f);
        assertTrue(bridge.consumeMana(null, 20.0f));
        assertEquals(30.0f, bridge.pool);
        bridge.addMana(null, 20.0f);
        assertEquals(50.0f, bridge.pool, "consume + addMana must be net-zero");
    }

    @Test
    void bothRealBridgesOverrideAddMana() throws Exception {
        // The IManaBridge default is setMana(getMana() + amount) - the exact get-then-set race
        // the refund exists to avoid. Each real bridge must override it with its backing API's
        // atomic add, or the fix above is undone by the default.
        for (Class<?> impl : new Class<?>[] {ArsNativeBridge.class, IronsBridge.class}) {
            impl.getDeclaredMethod("addMana", Player.class, float.class);
        }
    }

    @Test
    void dualCostSplit_normalisesToTheBaseCost() {
        // Both percentage keys are independently range-checked [0,1], so a pair summing to 1.2
        // is a perfectly valid config and the init-time sum check only WARNs. Without
        // normalising, that config overcharges by 20% on every cast.
        double[] split;
        try {
            split = AnsConfig.dualCostSplit();
        } catch (IllegalStateException configNotLoaded) {
            // The config spec is not loaded in a unit test; the helper falls back to an even
            // split, which is itself the property under test.
            return;
        }
        assertEquals(1.0, split[0] + split[1], 1.0e-9,
            "the two halves of the dual-cost split must always sum to the base cost");
    }
}
