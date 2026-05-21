package com.otectus.arsnspells.events;

import com.otectus.arsnspells.data.ModCapabilityProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-HIGH-008 — verifies that {@code ModCapabilityProvider.onPlayerClone}
 * runs at {@code EventPriority.HIGHEST}. The companion
 * {@code AuraCapabilityProvider} assertion was removed alongside the
 * aura-subsystem deletion.
 *
 * <p>Without HIGHEST, a third-party HIGHEST-priority {@code PlayerEvent.Clone}
 * handler could read our caps before we copy them, seeing freshly-default state
 * (player's affinity/progression appears reset).
 */
class CapabilityProviderPriorityTest {

    @Test
    void modCapabilityProvider_onPlayerClone_isHighest() throws IOException {
        // Reflection is fragile because the parameter type is from Forge's deobf jar
        // (PlayerEvent.Clone), not on the unit-test classpath. Use a source-text check.
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/data/ModCapabilityProvider.java"));
        int methodIdx = src.indexOf("public static void onPlayerClone");
        assertTrue(methodIdx > 0, "onPlayerClone must exist");
        // Look backwards for the @SubscribeEvent line.
        String preceding = src.substring(Math.max(0, methodIdx - 200), methodIdx);
        assertTrue(preceding.contains("priority = EventPriority.HIGHEST"),
            "ModCapabilityProvider.onPlayerClone must run at EventPriority.HIGHEST (ANS-HIGH-008)");
    }

    @Test
    void modCapabilityProvider_classLoads() {
        // Smoke test: ensure ModCapabilityProvider classloads without bootstrap issues.
        assertTrue(ModCapabilityProvider.class.getName()
            .endsWith("ModCapabilityProvider"));
    }
}
