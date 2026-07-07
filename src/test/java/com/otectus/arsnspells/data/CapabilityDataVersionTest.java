package com.otectus.arsnspells.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit E3 — the player capability tag must carry a schema version so a future
 * NBT format change has something to branch on instead of silently misreading
 * old keys (previously the only guard was a comment in CooldownData).
 *
 * <p>Source-text assertions because ModCapabilityProvider's data members hold
 * capability tokens that need a Forge runtime transformer (cannot load in
 * plain JUnit).
 */
class CapabilityDataVersionTest {

    private static String source() throws IOException {
        return Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/data/ModCapabilityProvider.java"));
    }

    @Test
    void serializeNBT_stampsDataVersion() throws IOException {
        String src = source();
        int serIdx = src.indexOf("public CompoundTag serializeNBT()");
        assertTrue(serIdx > 0, "serializeNBT must exist");
        int serEnd = src.indexOf("\n    }", serIdx);
        String body = src.substring(serIdx, serEnd);
        assertTrue(body.contains("putInt(DATA_VERSION_KEY, DATA_VERSION)"),
            "serializeNBT must stamp the schema version into every saved tag (audit E3)");
    }

    @Test
    void dataVersionConstant_existsAndIsDocumentedForMigration() throws IOException {
        String src = source();
        assertTrue(src.contains("public static final int DATA_VERSION"),
            "DATA_VERSION constant must exist");
        assertTrue(src.contains("migration"),
            "the version field must document that bumps require migration logic "
                + "in deserializeNBT");
    }
}
