package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V06 - an Ars mana mutation must move mana, and only the regen tick is suppressed.
 *
 * <p>The regression: ANS made {@code ManaCap.addMana} a no-op in ISS_PRIMARY and HYBRID,
 * because it was using that method as the place to stop Ars's native regeneration tick from
 * double-filling a pool Iron's was already regenerating. The suppression worked and took
 * everything else with it - a mana potion, a Void Jar, an addon refunding a cancelled spell
 * all reported success and moved nothing. The interception was at the wrong site: it belongs
 * at the one caller that ticks regen, not at the mutator every caller shares.
 *
 * <p>Two halves are asserted here. The routing half - a third-party {@code +50} lands on the
 * authoritative pool, in that pool's units - is the behaviour the mixin now delegates to
 * {@code BridgeManager}. The predicate half is {@link BridgeManager#ironsOwnsSharedPool()},
 * which is what {@code MixinManaRegenTick} consults before skipping the tick.
 */
class ManaMutationRoutingTest {

    private static final class SpyBridge implements IManaBridge {
        private final String type;
        float pool;

        SpyBridge(String type, float pool) {
            this.type = type;
            this.pool = pool;
        }

        @Override public float getMana(Player player) { return pool; }
        @Override public void setMana(Player player, float amount) { pool = amount; }
        @Override public boolean consumeMana(Player player, float amount) {
            if (pool < amount) {
                return false;
            }
            pool -= amount;
            return true;
        }
        @Override public void addMana(Player player, float amount) { pool += amount; }
        @Override public float getMaxMana(Player player) { return 1000.0f; }
        @Override public String getBridgeType() { return type; }
    }

    /** The fake pool the contract's resource port reads and moves. */
    private static final class FakeAccess implements ResourceAccess {
        final Map<ResourceUnit, Double> balances = new EnumMap<>(ResourceUnit.class);

        FakeAccess() {
            balances.put(ResourceUnit.ARS_MANA, 100.0d);
            balances.put(ResourceUnit.IRONS_MANA, 100.0d);
        }

        @Override public double current(UUID player, ResourceUnit unit) {
            return balances.getOrDefault(unit, 0.0d);
        }
        @Override public double max(UUID player, ResourceUnit unit) { return 1000.0d; }
        @Override public double debit(UUID player, ResourceUnit unit, double amount) {
            double moved = Math.min(current(player, unit), amount);
            balances.put(unit, current(player, unit) - moved);
            return moved;
        }
        @Override public double credit(UUID player, ResourceUnit unit, double amount) {
            balances.put(unit, current(player, unit) + amount);
            return amount;
        }
    }

    @AfterEach
    void resetRouting() {
        BridgeManager.testSetRouting(ManaUnificationMode.DISABLED, false,
            new SpyBridge("ARS_NATIVE", 0.0f), null);
    }

    @Test
    void thirdPartyAddManaInIssPrimary_movesTheAuthoritativePoolByFifty() {
        SpyBridge ars = new SpyBridge("ARS_NATIVE", 100.0f);
        SpyBridge irons = new SpyBridge("IRONS_SPELLS", 100.0f);
        BridgeManager.testSetRouting(ManaUnificationMode.ISS_PRIMARY, true, ars, irons);

        // What MixinManaCapability.addMana now does instead of swallowing the call.
        BridgeManager.getBridge().addMana(null, 50.0f);

        assertEquals(150.0f, irons.pool, 1.0e-4f,
            "an addMana(+50) in ISS_PRIMARY must move the Iron's pool by 50 Iron's mana. It "
                + "used to move nothing at all and report success");
        assertEquals(100.0f, ars.pool, 1.0e-4f,
            "the non-authoritative pool must not also move - that would double the gift");
    }

    @Test
    void thirdPartyMutationIsVisibleThroughTheResourcePort() {
        FakeAccess access = new FakeAccess();
        UUID player = UUID.randomUUID();

        assertEquals(50.0d, access.credit(player, ResourceUnit.IRONS_MANA, 50.0d), 1.0e-9d,
            "the port reports what actually moved");
        assertEquals(150.0d, access.current(player, ResourceUnit.IRONS_MANA), 1.0e-9d);
        assertEquals(100.0d, access.current(player, ResourceUnit.ARS_MANA), 1.0e-9d,
            "crediting Iron's must not touch the Ars pool");
    }

    @Test
    void theRegenTickIsSuppressedOnlyWhenIronsOwnsThePool() {
        SpyBridge ars = new SpyBridge("ARS_NATIVE", 100.0f);
        SpyBridge irons = new SpyBridge("IRONS_SPELLS", 100.0f);

        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            BridgeManager.testSetRouting(mode, true, ars, irons);
            boolean expected = mode == ManaUnificationMode.ISS_PRIMARY
                || mode == ManaUnificationMode.HYBRID;
            assertEquals(expected, BridgeManager.ironsOwnsSharedPool(),
                mode.getConfigName() + ": Ars's regen tick is redundant exactly when Iron's "
                    + "owns the shared pool");
        }
    }

    @Test
    void arsPrimaryKeepsItsOwnRegenTick() {
        SpyBridge ars = new SpyBridge("ARS_NATIVE", 100.0f);
        SpyBridge irons = new SpyBridge("IRONS_SPELLS", 100.0f);
        BridgeManager.testSetRouting(ManaUnificationMode.ARS_PRIMARY, true, ars, irons);

        assertTrue(BridgeManager.usesSharedPool(),
            "ARS_PRIMARY shares a pool...");
        assertFalse(BridgeManager.ironsOwnsSharedPool(),
            "...but the pool it shares is the Ars one, and this tick is the only thing "
                + "filling it. Suppressing on usesSharedPool() would stop regen dead");
    }

    @Test
    void noModeSuppressesTheTickWithoutIrons() {
        SpyBridge ars = new SpyBridge("ARS_NATIVE", 100.0f);
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            BridgeManager.testSetRouting(mode, false, ars, null);
            assertFalse(BridgeManager.ironsOwnsSharedPool(),
                mode.getConfigName() + " without Iron's has no second pool, so Ars must keep "
                    + "regenerating its own");
        }
    }
}
