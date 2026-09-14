package com.otectus.arsnspells.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceSynergyPolicyTest {
    @Test void migratingEverySupportedCadencePreservesOneMinuteOfIncome() {
        for (int interval : new int[]{1, 20, 200}) for (double old : new double[]{.1, 5, 100}) {
            double oldMinute = old * 1200.0 / interval;
            double migrated = SourceSynergyPolicy.migratePerScanMultiplier(old, interval);
            assertEquals(oldMinute, migrated * 60, 1e-8);
        }
    }
    @Test void discoveredIncomeIsIndependentOfScanCadence() {
        for (int interval : new int[]{1,20,200}) {
            double mana = 0;
            long scanned = 0;
            int scans = 0;
            for (int tick = 0; tick < 2000; tick++) {
                if (SourceSynergyPolicy.expired(scanned, tick, interval)) { scanned = tick; scans++; }
                mana += SourceSynergyPolicy.income(5, 1);
            }
            assertEquals(500, mana, 1e-8);
            assertTrue(scans > 0);
        }
    }

    @Test void positiveAndNegativeCacheEntriesExpireAtDeclaredInterval() {
        assertFalse(SourceSynergyPolicy.expired(5, 24, 20));
        assertTrue(SourceSynergyPolicy.expired(5, 25, 20));
        assertTrue(SourceSynergyPolicy.expired(5, 4, 20));
        assertEquals(5, SourceSynergyPolicy.income(5, 2000));
        assertEquals(0, SourceSynergyPolicy.income(Double.NaN, 1));
    }
}
