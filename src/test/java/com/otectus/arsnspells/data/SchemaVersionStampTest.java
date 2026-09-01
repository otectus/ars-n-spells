package com.otectus.arsnspells.data;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit E3 - ANS-written data must carry a schema version, so a future format change has
 * something to branch on instead of silently misreading old keys.
 *
 * <p><b>Port divergence.</b> The 1.20.1 line stamped one {@code DATA_VERSION} into the player
 * capability tag. NeoForge splits that data into three independent attachments with their own
 * codecs, so the version lives per-attachment: {@code AffinityData} carries a
 * {@code schema_version} field (it has a real migration to branch on), and inscribed items
 * carry the {@code SCHEMA_VERSION} data component that {@code CarrierReconciler} stamps.
 */
class SchemaVersionStampTest {

    private static String read(String path) throws IOException {
        return Files.readString(TestPaths.of(path));
    }

    @Test
    void affinityDataCarriesAVersionedRecord() throws IOException {
        String src = read("src/main/java/com/otectus/arsnspells/data/AffinityData.java");
        assertTrue(src.contains("SCHEMA_VERSION"),
            "AffinityData must declare a schema version");
        assertTrue(src.contains("\"schema_version\""),
            "the version must actually be written into the encoded record, not just declared");
    }

    @Test
    void inscribedItemsCarryASchemaComponent() throws IOException {
        String components = read("src/main/java/com/otectus/arsnspells/spell/ModDataComponents.java");
        assertTrue(components.contains("SCHEMA_VERSION"),
            "inscribed items must have a schema-version component to branch a migration on");

        String accessors =
            read("src/main/java/com/otectus/arsnspells/spell/CrossModSpellComponents.java");
        assertTrue(accessors.contains("stampSchemaVersion") && accessors.contains("schemaVersion("),
            "the component needs both a reader and a writer or it can never be acted on");
    }

    @Test
    void theReconcilerActuallyStampsIt() throws IOException {
        String reconciler =
            read("src/main/java/com/otectus/arsnspells/spell/irons/CarrierReconciler.java");
        assertTrue(reconciler.contains("stampSchemaVersion"),
            "CarrierReconciler is the one place that looks an item over, so it is the place "
                + "that must record having done so - otherwise the stamp is never written and "
                + "the version is decoration");
    }
}
