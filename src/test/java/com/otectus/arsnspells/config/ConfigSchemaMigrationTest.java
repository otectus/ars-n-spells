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
 * 3.3.0 T1.2 - the config schema version and the defaults it resolves.
 *
 * <p>The point of the schema key is the distinction between a file written before 3.3.0 and one
 * this build generated. On 1.21.1 the only key that distinction currently drives is
 * {@code conversion_policy}, whose migrated value must be the one that preserves today's
 * pricing; {@code payment_open_failure_policy} is Forge-only because the LP and aura payment
 * paths were never ported here.
 *
 * <p>The resolvers are pure static methods so this runs without a mod loading context.
 */
class ConfigSchemaMigrationTest {

    @Test
    void conversionPolicy_defaultsToTodaysPricing() {
        assertSame(ConversionKind.FLAT_LEGACY, AnsConfig.parseConversionPolicy("flat_legacy"),
            "a config written before 3.3.0 must keep the flat-rate arithmetic it was priced with");
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
    void schemaKeys_exist() {
        for (String name : new String[] { "CONFIG_SCHEMA_VERSION", "CONVERSION_POLICY" }) {
            try {
                AnsConfig.class.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                fail("AnsConfig." + name + " must exist (3.3.0 T1.2)");
            }
        }
        assertEquals(1, AnsConfig.CURRENT_SCHEMA_VERSION,
            "3.3.0 is schema 1; bumping this is a migration, not a version bump");
    }

    @Test
    void paymentOpenFailurePolicy_isForgeOnly() {
        try {
            AnsConfig.class.getDeclaredField("PAYMENT_OPEN_FAILURE_POLICY");
            fail("payment_open_failure_policy must not exist on 1.21.1: the LP and aura payment "
                + "paths it governs were never ported (see casting/CastingAuthority)");
        } catch (NoSuchFieldException expected) {
            // good - the key stays in the Forge 1.20.1 config only
        }
    }
}
