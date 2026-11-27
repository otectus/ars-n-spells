package com.otectus.arsnspells.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bootstrap-free coverage for the pure unit-conversion math in {@link ManaRegenBridge}: Ars's
 * nominal regen and Iron's percentage-of-pool attribute, each converted to the real mana per
 * second its native scheduler delivers (audit ANS-011). The config-aware {@code convert*} wrappers
 * need a loaded config and a player; {@code RegenCadenceGameTests} measures them against the native
 * schedulers in a running server. A round trip returning its input is not, on its own, evidence
 * that the model is right.
 */
class ManaRegenBridgeTest {

    /** Calls per eligible tick: 2 on Forge 1.20.1 (Ars 4.12.7, both phases), 1 on NeoForge 1.21.1 (Ars 5.13.1, Pre). */
    private static final double CALLS = ManaRegenBridge.ARS_CALLS_PER_ELIGIBLE_TICK;

    @Test
    void nativeFactorsFollowThePinnedSchedulers() {
        // Interval 5, MEAN_TPS 20 -> 4 eligible ticks/s, divisor 4.
        assertEquals(CALLS, ManaRegenBridge.arsRealPerNominal(5, 20.0), 1e-12);
        // Integer division in Ars's divisor: interval 3 -> 20/3 ticks/s / 6.
        assertEquals(CALLS * (20.0 / 3.0) / 6.0, ManaRegenBridge.arsRealPerNominal(3, 20.0), 1e-12);
        assertEquals(CALLS * (20.0 / 7.0) / 2.0, ManaRegenBridge.arsRealPerNominal(7, 20.0), 1e-12);
        // Interval 20: divisor clamps to 1.
        assertEquals(CALLS, ManaRegenBridge.arsRealPerNominal(20, 20.0), 1e-12);
        // A lagging server (Ars 5 measures MEAN_TPS): Ars raises each call's amount, so 20 game ticks deliver more.
        assertEquals(CALLS * 4.0 / 2.0, ManaRegenBridge.arsRealPerNominal(5, 10.0), 1e-12);
        // Iron's: 0.01 per call, every 10 ticks, times its own multiplier.
        assertEquals(0.02, ManaRegenBridge.ironsRealPerSecondFactor(10, 1.0), 1e-12);
        assertEquals(0.04, ManaRegenBridge.ironsRealPerSecondFactor(10, 2.0), 1e-12);
        assertEquals(0.0, ManaRegenBridge.ironsRealPerSecondFactor(10, 0.0), 0.0);
    }

    @Test
    void defaultSettingsMatchRealManaPerSecond() {
        // MANA_REGEN=1 on MAX_MANA=1000 is 20 real Iron's mana a second. On Forge the native doublings
        // cancel and that is worth 10 nominal Ars points, what the earlier "0.01 per second" helper
        // returned; on NeoForge Ars does not double, so it is worth 20.
        assertEquals(20.0 / CALLS, ManaRegenBridge.ironsToArsRegen(1.0, 1000.0), 1e-9);
        assertEquals(CALLS / 2.0, ManaRegenBridge.arsToIronsRegen(10.0, 1000.0), 1e-9);
    }

    @Test
    void nonDefaultSettingsMatchRealManaPerSecond() {
        double max = 1000.0;
        double ars = ManaRegenBridge.arsRealPerNominal(7, 20.0);
        double irons = ManaRegenBridge.ironsRealPerSecondFactor(10, 2.0);
        double attr = ManaRegenBridge.arsToIronsRegen(10.0, max, ars, irons);
        // Real Ars mana/s from 10 nominal points equals real Iron's mana/s from the converted attribute.
        assertEquals(10.0 * ars, attr * max * irons, 1e-9);
        double nominal = ManaRegenBridge.ironsToArsRegen(1.5, max, ars, irons);
        assertEquals(1.5 * max * irons, nominal * ars, 1e-9);
    }

    @Test
    void disabledOrEmptyRegenIsZeroNeverNonFinite() {
        assertEquals(0.0, ManaRegenBridge.arsToIronsRegen(10.0, 0.0));
        assertEquals(0.0, ManaRegenBridge.ironsToArsRegen(1.0, 0.0));
        assertEquals(0.0, ManaRegenBridge.arsToIronsRegen(0.0, 1000.0));
        assertEquals(0.0, ManaRegenBridge.ironsToArsRegen(0.0, 1000.0));
        // Iron's multiplier 0: Iron's delivers nothing, so no attribute can carry Ars regen across.
        assertEquals(0.0, ManaRegenBridge.arsToIronsRegen(10.0, 1000.0, 2.0, 0.0));
        assertEquals(0.0, ManaRegenBridge.ironsToArsRegen(1.0, 1000.0, 2.0, 0.0));
        assertEquals(0.0, ManaRegenBridge.ironsToArsRegen(1.0, 1000.0, 0.0, 0.02));
        assertTrue(Double.isFinite(ManaRegenBridge.arsToIronsRegen(1.0e300, 1.0e-300, 2.0, 1.0e-300)));
        assertEquals(0.0, ManaRegenBridge.arsToIronsRegen(10.0, Double.NaN, 2.0, 0.02));
    }
}
