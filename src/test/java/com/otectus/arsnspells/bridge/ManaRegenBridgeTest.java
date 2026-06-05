package com.otectus.arsnspells.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bootstrap-free coverage for the pure unit-conversion math in {@link ManaRegenBridge}
 * (the Ars absolute mana/sec ↔ Iron's percentage-of-pool conversion that the
 * cross-system regen feature depends on). The config-aware {@code convert*} wrappers
 * are not exercised here — they require a loaded config + player.
 */
class ManaRegenBridgeTest {

    @Test
    void ironsFormulaMatchesObservedBaseline() {
        // MANA_REGEN=1, MAX_MANA=1000 -> 10 mana/sec (factor 0.01).
        assertEquals(10.0, ManaRegenBridge.ironsToArsRegen(1.0, 1000.0), 1e-9);
    }

    @Test
    void arsToIronsIsTheInverseOfIronsToArs() {
        double max = 1000.0;
        double arsPerSec = 10.0;
        double ironsAttr = ManaRegenBridge.arsToIronsRegen(arsPerSec, max);
        assertEquals(arsPerSec, ManaRegenBridge.ironsToArsRegen(ironsAttr, max), 1e-9);
    }

    @Test
    void nonPositivePoolOrZeroInputIsSafe() {
        assertEquals(0.0, ManaRegenBridge.arsToIronsRegen(10.0, 0.0));
        assertEquals(0.0, ManaRegenBridge.ironsToArsRegen(1.0, 0.0));
        assertEquals(0.0, ManaRegenBridge.arsToIronsRegen(0.0, 1000.0));
        assertEquals(0.0, ManaRegenBridge.ironsToArsRegen(0.0, 1000.0));
    }
}
