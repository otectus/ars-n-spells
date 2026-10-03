package com.otectus.arsnspells.config;

import com.otectus.arsnspells.contract.ConversionKind;
import com.otectus.arsnspells.contract.PaymentOpenFailurePolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 3.3.0 T1.2 - the config schema version and the defaults it resolves.
 *
 * <p>The point of the schema key is the distinction between a file written before 3.3.0 and one
 * this build generated: an existing world must keep the behaviour it had, and a new install must
 * get the safe policy. Both halves are asserted here.
 *
 * <p>The resolvers are pure static methods precisely so this can run without a mod loading
 * context, matching {@link AnsConfigStructureTest}'s constraint.
 */
class ConfigSchemaMigrationTest {

    @Test
    void schemaZeroFile_resolvesLegacyPaymentPolicy() {
        assertSame(PaymentOpenFailurePolicy.LEGACY_OPEN,
            AnsConfig.resolvePaymentOpenFailurePolicy(AnsConfig.PAYMENT_POLICY_AUTO, 0, false),
            "a config written before 3.3.0 must keep the open-on-failure behaviour it had");
    }

    @Test
    void freshlyGeneratedFile_resolvesSafePaymentPolicy() {
        assertSame(PaymentOpenFailurePolicy.REFUSE,
            AnsConfig.resolvePaymentOpenFailurePolicy(AnsConfig.PAYMENT_POLICY_AUTO, 0, true),
            "a freshly generated config must get the safe policy, not the legacy one");
    }

    @Test
    void currentSchemaFile_resolvesSafePaymentPolicy() {
        // Once a fresh file has been stamped, the next load reads the version from the file and
        // must reach the same answer without probing the filesystem again.
        assertSame(PaymentOpenFailurePolicy.REFUSE,
            AnsConfig.resolvePaymentOpenFailurePolicy(
                AnsConfig.PAYMENT_POLICY_AUTO, AnsConfig.CURRENT_SCHEMA_VERSION, false),
            "a file already stamped with the current schema must not fall back to legacy");
    }

    @Test
    void explicitPaymentPolicy_beatsMigration() {
        assertSame(PaymentOpenFailurePolicy.REFUSE,
            AnsConfig.resolvePaymentOpenFailurePolicy("refuse", 0, false));
        assertSame(PaymentOpenFailurePolicy.NATIVE_FALLBACK,
            AnsConfig.resolvePaymentOpenFailurePolicy("native_fallback", 0, true));
        assertSame(PaymentOpenFailurePolicy.LEGACY_OPEN,
            AnsConfig.resolvePaymentOpenFailurePolicy("legacy_open", AnsConfig.CURRENT_SCHEMA_VERSION, true));
    }

    @Test
    void unknownPaymentPolicy_fallsBackToTheMigrationAnswer() {
        assertSame(PaymentOpenFailurePolicy.LEGACY_OPEN,
            AnsConfig.resolvePaymentOpenFailurePolicy("nonsense", 0, false));
        assertSame(PaymentOpenFailurePolicy.REFUSE,
            AnsConfig.resolvePaymentOpenFailurePolicy("nonsense", 0, true));
    }

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
    void schemaOneExplicitPolicySurvivesSourceMigration() {
        assertSame(PaymentOpenFailurePolicy.LEGACY_OPEN,
            AnsConfig.resolvePaymentOpenFailurePolicy("legacy_open", 1, false));
        assertSame(PaymentOpenFailurePolicy.REFUSE,
            AnsConfig.resolvePaymentOpenFailurePolicy("refuse", 1, false));
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
        for (String name : new String[] {
            "CONFIG_SCHEMA_VERSION", "CONVERSION_POLICY", "PAYMENT_OPEN_FAILURE_POLICY",
        }) {
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
