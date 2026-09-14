package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.ModeRoutingSnapshot;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit V04 - disabling unification must restore native pool routing.
 *
 * <p>The regression: {@code BridgeManager} held the mode, the Iron's-presence flag and the
 * two adapters in three separately-volatile fields, and DISABLED set the Iron's adapter to
 * {@code null} outright. A native Iron's spend then found no Iron's adapter and fell back to
 * whatever sat in the "active" slot - the Ars pool. Turning integration off therefore did not
 * turn it off; it made Iron's spells cost Ars mana. The mirror case was SEPARATE with Iron's
 * absent, where the mode fell back to ARS_PRIMARY but the secondary adapter reference could
 * still be observed naming a pool that did not exist.
 *
 * <p>The routing cases below mirror {@code ans-contract-fixtures/routing.json}, which is the
 * shared expectation both loaders are held to.
 */
class ModeRoutingSnapshotTest {

    /** Records what it was asked to spend, so a test can say which pool actually paid. */
    private static final class SpyBridge implements IManaBridge {
        private final String type;
        float pool;
        float spent;

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
            spent += amount;
            return true;
        }
        @Override public void addMana(Player player, float amount) { pool += amount; }
        @Override public float getMaxMana(Player player) { return Float.MAX_VALUE; }
        @Override public String getBridgeType() { return type; }
    }

    @AfterEach
    void resetRouting() {
        // BridgeManager's routing is process-wide static state; leaving a test's mode behind
        // would silently become the next test's baseline.
        BridgeManager.testSetRouting(ManaUnificationMode.DISABLED, false,
            new SpyBridge("ARS_NATIVE", 0.0f), null);
    }

    @Test
    void disabledWithIronsLoaded_routesANativeIronsSpendToTheIronsPool() {
        SpyBridge ars = new SpyBridge("ARS_NATIVE", 1000.0f);
        SpyBridge irons = new SpyBridge("IRONS_SPELLS", 1000.0f);
        BridgeManager.testSetRouting(ManaUnificationMode.DISABLED, true, ars, irons);

        assertNotNull(BridgeManager.getNativeIronsBridge(),
            "DISABLED must not null the Iron's adapter: 'no integration' means Iron's pays "
                + "for its own spells, not that Iron's stops existing");

        boolean spent = BridgeManager.consumeManaForMode(null, 40.0f, ResourceUnit.IRONS_MANA);

        assertTrue(spent, "the Iron's pool has 1000, a 40 spend must succeed");
        assertEquals(40.0f, irons.spent, 1.0e-4f,
            "a native Iron's spend must come out of the Iron's pool with unification off");
        assertEquals(0.0f, ars.spent, 1.0e-4f,
            "the Ars pool must not pay for an Iron's spell - that is the V04 regression");
    }

    @Test
    void disabledWithIronsLoaded_stillRoutesANativeArsSpendToTheArsPool() {
        SpyBridge ars = new SpyBridge("ARS_NATIVE", 1000.0f);
        SpyBridge irons = new SpyBridge("IRONS_SPELLS", 1000.0f);
        BridgeManager.testSetRouting(ManaUnificationMode.DISABLED, true, ars, irons);

        assertTrue(BridgeManager.consumeManaForMode(null, 25.0f, ResourceUnit.ARS_MANA));
        assertEquals(25.0f, ars.spent, 1.0e-4f);
        assertEquals(0.0f, irons.spent, 1.0e-4f);
    }

    @Test
    void separateWithoutIrons_exposesNoStaleSecondary() {
        SpyBridge ars = new SpyBridge("ARS_NATIVE", 1000.0f);
        SpyBridge stale = new SpyBridge("IRONS_SPELLS", 1000.0f);
        // Ask for the Iron's adapter to be installed even though Iron's is absent: the
        // routing must refuse it rather than keep the reference around.
        BridgeManager.testSetRouting(ManaUnificationMode.SEPARATE, false, ars, stale);

        assertNull(BridgeManager.getNativeIronsBridge(),
            "SEPARATE without Iron's must leave no Iron's adapter behind");

        ModeRoutingSnapshot snapshot = BridgeManager.getRoutingSnapshot();
        assertFalse(snapshot.ironsPresent());
        assertEquals(ModeRoutingSnapshot.DISABLED, snapshot.effectiveMode(),
            "there is nothing to unify without a second pool");
        assertTrue(snapshot.nativeIronsAdapterId().isEmpty());
        assertThrows(IllegalStateException.class, snapshot::routeNativeIronsSpend,
            "no Iron's route may exist when Iron's is not installed");

        assertTrue(BridgeManager.consumeManaForMode(null, 10.0f, ResourceUnit.ARS_MANA));
        assertEquals(0.0f, stale.spent, 1.0e-4f, "the discarded adapter must never be spent from");
    }

    @Test
    void everyModeAndPresenceMatchesTheSharedRoutingFixtures() {
        // Mirrors ans-contract-fixtures/routing.json: effective mode, authoritative pool, and
        // whether an Iron's adapter exists, for all five modes with and without Iron's.
        for (ManaUnificationMode mode : ManaUnificationMode.values()) {
            for (boolean ironsPresent : new boolean[]{true, false}) {
                SpyBridge ars = new SpyBridge("ARS_NATIVE", 1000.0f);
                SpyBridge irons = new SpyBridge("IRONS_SPELLS", 1000.0f);
                BridgeManager.testSetRouting(mode, ironsPresent, ars, irons);
                ModeRoutingSnapshot snapshot = BridgeManager.getRoutingSnapshot();
                String where = mode.getConfigName() + "/irons=" + ironsPresent;

                assertEquals(mode.getConfigName(), snapshot.requestedMode(), where);
                assertEquals(ironsPresent, snapshot.ironsPresent(), where);

                String expectedEffective = ironsPresent
                    ? mode.getConfigName()
                    : ModeRoutingSnapshot.DISABLED;
                assertEquals(expectedEffective, snapshot.effectiveMode(), where);

                ResourceUnit expectedAuthoritative =
                    ironsPresent && (mode == ManaUnificationMode.ISS_PRIMARY
                        || mode == ManaUnificationMode.HYBRID)
                        ? ResourceUnit.IRONS_MANA
                        : ResourceUnit.ARS_MANA;
                assertEquals(expectedAuthoritative, snapshot.authoritativeUnit(), where);

                assertEquals(ironsPresent, snapshot.nativeIronsAdapterId().isPresent(), where);
                assertEquals(ironsPresent, BridgeManager.getNativeIronsBridge() != null, where);
                assertEquals(BridgeManager.ARS_ADAPTER_ID, snapshot.nativeArsAdapterId(), where);
            }
        }
    }

    @Test
    void oneSnapshotAnswersModeAndPresenceAndAuthority() {
        SpyBridge ars = new SpyBridge("ARS_NATIVE", 1000.0f);
        SpyBridge irons = new SpyBridge("IRONS_SPELLS", 1000.0f);
        BridgeManager.testSetRouting(ManaUnificationMode.ISS_PRIMARY, true, ars, irons);

        // The three facts that used to live in three separately-volatile fields must now
        // agree, because they come from one object.
        ModeRoutingSnapshot snapshot = BridgeManager.getRoutingSnapshot();
        assertEquals(ManaUnificationMode.ISS_PRIMARY, BridgeManager.getCurrentMode());
        assertTrue(BridgeManager.isUnificationEnabled());
        assertTrue(snapshot.isUnified());
        assertEquals(ResourceUnit.IRONS_MANA, snapshot.authoritativeUnit());
        assertEquals("IRONS_SPELLS", BridgeManager.getBridge().getBridgeType(),
            "the authoritative adapter is derived from the snapshot's unit, not a slot");

        // A shared mode bills the authoritative pool whichever system asked.
        assertTrue(BridgeManager.consumeManaForMode(null, 30.0f, ResourceUnit.ARS_MANA));
        assertEquals(30.0f, irons.spent, 1.0e-4f);
        assertEquals(0.0f, ars.spent, 1.0e-4f);
    }
}
