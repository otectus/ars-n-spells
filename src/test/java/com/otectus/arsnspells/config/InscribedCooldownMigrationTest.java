package com.otectus.arsnspells.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 3.3.5 - {@code inscribed_ars_default_cooldown_ticks}: a new install gets the shipped 40-tick
 * cooldown, an existing world keeps casting inscribed Ars spells without one, and a value a pack
 * author typed is never overwritten.
 */
class InscribedCooldownMigrationTest {
    private static final int DEFAULT = AnsConfig.INSCRIBED_ARS_COOLDOWN_DEFAULT_TICKS;

    @Test
    void freshlyGeneratedConfigKeepsTheShippedDefault() {
        assertEquals(40, DEFAULT);
        assertEquals(DEFAULT, AnsConfig.resolveInscribedCooldownTicks(DEFAULT, 0, true));
    }

    @Test
    void everyPre335SchemaMigratesTheCorrectedDefaultToZero() {
        for (int schema = 0; schema < AnsConfig.INSCRIBED_COOLDOWN_SCHEMA; schema++) {
            assertEquals(0, AnsConfig.resolveInscribedCooldownTicks(DEFAULT, schema, false),
                "schema " + schema + ": Forge inserted the default into a file that predates the key");
        }
    }

    @Test
    void aHandWrittenValueInAnOldFileSurvives() {
        assertEquals(100, AnsConfig.resolveInscribedCooldownTicks(100, 2, false));
        assertEquals(0, AnsConfig.resolveInscribedCooldownTicks(0, 2, false));
    }

    @Test
    void aFileStampedBy335IsTakenAsWritten() {
        assertEquals(DEFAULT, AnsConfig.resolveInscribedCooldownTicks(DEFAULT, 3, false));
        assertEquals(0, AnsConfig.resolveInscribedCooldownTicks(0, 3, false));
        assertEquals(12000, AnsConfig.resolveInscribedCooldownTicks(12000, 4, false));
    }

    @Test
    void outOfRangeValuesAreBounded() {
        assertEquals(0, AnsConfig.resolveInscribedCooldownTicks(-5, 3, false));
        assertEquals(AnsConfig.INSCRIBED_ARS_COOLDOWN_MAX_TICKS,
            AnsConfig.resolveInscribedCooldownTicks(Integer.MAX_VALUE, 3, false));
    }

    @Test
    void settingsRowCyclesPresetsAndDescribesSeconds() {
        assertEquals("Off", InscribedCooldownPresets.describe(0));
        assertEquals("40t (2s)", InscribedCooldownPresets.describe(40));
        assertEquals("10t (0.5s)", InscribedCooldownPresets.describe(10));
        assertEquals(10, InscribedCooldownPresets.next(0));
        assertEquals(60, InscribedCooldownPresets.next(40));
        assertEquals(60, InscribedCooldownPresets.next(45), "an off-preset value advances to the next preset");
        assertEquals(0, InscribedCooldownPresets.next(400), "the largest preset wraps to Off");
        assertEquals(0, InscribedCooldownPresets.next(12000));
    }
}
