package com.otectus.arsnspells.events;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-CRIT-005 follow-up — verifies the Source Jar synergy scan is fully
 * config-driven: a kill switch gates the whole handler, and the scan cadence
 * and radius come from config keys instead of hardcoded constants, so server
 * owners can tune or disable the feature without disabling mana unification.
 */
class RegenSynergyHandlerConfigGateTest {

    private static String source() throws IOException {
        return Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/RegenSynergyHandler.java"));
    }

    @Test
    void killSwitch_gatesTickBeforeScan() throws IOException {
        String src = source();
        int tickIdx = src.indexOf("public void onPlayerTick");
        int gateIdx = src.indexOf("ENABLE_SOURCE_JAR_SYNERGY.get()", tickIdx);
        int scanIdx = src.indexOf("scanForSourceJar(level, pos", tickIdx);
        assertTrue(gateIdx > tickIdx,
            "onPlayerTick must check ENABLE_SOURCE_JAR_SYNERGY (server-owner kill switch)");
        assertTrue(scanIdx > gateIdx,
            "the kill-switch check must precede the scan call");
    }

    @Test
    void scanCadence_isConfigDriven() throws IOException {
        String src = source();
        assertTrue(src.contains("SOURCE_JAR_SCAN_INTERVAL_TICKS"),
            "scan cadence must come from source_jar_scan_interval_ticks");
        assertFalse(src.contains("tickCount % 20"),
            "the hardcoded 20-tick cadence must be gone");
    }

    @Test
    void radius_isConfigDriven() throws IOException {
        String src = source();
        assertTrue(src.contains("SOURCE_JAR_SCAN_RADIUS"),
            "scan radius must come from source_jar_scan_radius");
        assertFalse(src.contains("SCAN_RADIUS = 4"),
            "the hardcoded SCAN_RADIUS constant must be gone");
    }

    @Test
    void debugLogging_isCountersOnly() throws IOException {
        String src = source();
        // The summary must be rate-limited and the per-block loop must not log.
        assertTrue(src.contains("maybeLogDebugSummary"),
            "debug output must go through the rate-limited summary");
        int scanIdx = src.indexOf("private static boolean scanForSourceJar");
        int scanEnd = src.indexOf("\n    }", scanIdx);
        String scanBody = src.substring(scanIdx, scanEnd);
        assertFalse(scanBody.contains("LOGGER."),
            "scanForSourceJar must never log per block");
    }
}
