package com.otectus.arsnspells.config;

import com.otectus.arsnspells.contract.ConversionKind;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The portable half of the Forge 1.20.1 suite of the same name. The payment-open-failure policy
 * cases are not here: that key belongs to the Covenant of the Seven LP/aura payment, which has
 * no 1.21.1 build.
 */
class ConfigSchemaMigrationTest {

    @Test
    void conversionPolicy_defaultsToTodaysPricing() {
        assertSame(ConversionKind.FLAT_LEGACY, AnsConfig.parseConversionPolicy("flat_legacy"));
        assertSame(ConversionKind.EQUAL_PERCENT, AnsConfig.parseConversionPolicy("equal_percent"));
        assertSame(ConversionKind.FLAT_LEGACY, AnsConfig.parseConversionPolicy("EQUAL PERCENT"),
            "a typo must not silently reprice the pack");
        assertSame(ConversionKind.FLAT_LEGACY, AnsConfig.parseConversionPolicy(null));
    }

    @Test
    void freshnessProbe_failsSafeTowardsExistingWorlds() throws Exception {
        Path missing = Path.of("does", "not", "exist", "ars_n_spells-server.toml");
        assertFalse(AnsConfig.isFreshlyGeneratedConfig(missing),
            "an unreadable path must resolve as not-fresh, preserving existing behaviour");
        assertFalse(AnsConfig.isFreshlyGeneratedConfig(null));

        Path created = Files.createTempFile("ans-schema-probe", ".toml");
        try {
            assertTrue(AnsConfig.isFreshlyGeneratedConfig(created),
                "a file created during this JVM's lifetime is a freshly generated config");
        } finally {
            Files.deleteIfExists(created);
        }
    }

    @Test
    void sourceRateMigrationPreservesNondefaultIncomeAtOldCadence() {
        for (int ticks : new int[]{1, 20, 100, 200}) {
            for (double oldMultiplier : new double[]{.1, 5, 100}) {
                double migrated = com.otectus.arsnspells.util.SourceSynergyPolicy.migratePerScanMultiplier(oldMultiplier, ticks);
                double oneOldScanIncome = com.otectus.arsnspells.util.SourceSynergyPolicy.income(migrated, 1) * ticks;
                assertEquals(oldMultiplier, oneOldScanIncome, 1e-9,
                    "new per-tick income must equal one historical payment over its configured scan interval");
                assertTrue(migrated >= .01 && migrated <= 2000, "migration must fit the persisted config bounds");
            }
        }
    }

    @Test
    void schemaKeys_exist() {
        for (String name : new String[] {"CONFIG_SCHEMA_VERSION", "CONVERSION_POLICY"}) {
            try {
                AnsConfig.class.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                fail("AnsConfig." + name + " must exist (3.3.0 T1.2)");
            }
        }
        assertEquals(3, AnsConfig.CURRENT_SCHEMA_VERSION,
            "schema 2 migrates Source income from per scan to per second; schema 3 adds the "
                + "inscribed Ars spell cooldown");
    }
}
