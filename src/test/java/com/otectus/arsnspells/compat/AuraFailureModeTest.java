package com.otectus.arsnspells.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit D2 — the Covenant aura bridge fails OPEN when reflection is degraded
 * (casts become free), which is an economy exploit pack makers previously
 * could neither detect nor control. These tests pin the remediation:
 * a server config key {@code aura_failure_mode} (open/closed, default open =
 * historical behavior) consulted at every degraded decision, plus a
 * once-per-session WARN so degradation is visible in logs.
 */
class AuraFailureModeTest {

    private static String compatSource() throws IOException {
        return Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java"));
    }

    private static String configSource() throws IOException {
        return Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/config/AnsConfig.java"));
    }

    @Test
    void config_definesAuraFailureMode_defaultOpen() throws IOException {
        String src = configSource();
        assertTrue(src.contains("\"aura_failure_mode\", \"open\""),
            "aura_failure_mode must exist and default to \"open\" (historical "
                + "fail-open behavior preserved for existing installs)");
        assertTrue(src.contains("equalsIgnoreCase(\"closed\")"),
            "the key must be validated to open/closed so a typo cannot silently "
                + "fall back to an unintended mode");
    }

    @Test
    void degradedDecisions_routeThroughConfigConsultingHelper() throws IOException {
        String src = compatSource();
        int methodIdx = src.indexOf("public static boolean hasEnoughCovenantAura");
        assertTrue(methodIdx > 0, "hasEnoughCovenantAura must exist");
        int methodEnd = src.indexOf("\n    }", methodIdx);
        String body = src.substring(methodIdx, methodEnd);
        assertFalse(body.contains("return true; // degraded"),
            "no degraded path may hardcode fail-open anymore; it must route "
                + "through degradedAuraAnswer so aura_failure_mode is honored");
        assertTrue(body.contains("degradedAuraAnswer("),
            "degraded paths must call degradedAuraAnswer");
        assertTrue(src.contains("AURA_FAILURE_MODE.get()"),
            "degradedAuraAnswer must consult AnsConfig.AURA_FAILURE_MODE");
    }

    @Test
    void closedMode_blocksAndOpenModeAllows() throws IOException {
        String src = compatSource();
        int helperIdx = src.indexOf("private static boolean degradedAuraAnswer");
        assertTrue(helperIdx > 0, "degradedAuraAnswer helper must exist");
        int helperEnd = src.indexOf("\n    }", helperIdx);
        String body = src.substring(helperIdx, helperEnd);
        assertTrue(body.contains("!\"closed\".equalsIgnoreCase"),
            "\"closed\" must invert the answer (return false = block the cast); "
                + "anything else stays open");
        assertTrue(body.contains("return failOpen"),
            "the helper's return value must be the mode decision itself");
    }

    @Test
    void firstDegradedDecision_logsAtWarn() throws IOException {
        String src = compatSource();
        int helperIdx = src.indexOf("private static boolean degradedAuraAnswer");
        int helperEnd = src.indexOf("\n    }", helperIdx);
        String body = src.substring(helperIdx, helperEnd);
        assertTrue(body.contains("compareAndSet(false, true)") && body.contains("LOGGER.warn"),
            "the first degraded decision per session must log at WARN "
                + "(once-per-session gate) so pack makers can see free/blocked casts");
    }
}
