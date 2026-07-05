package com.otectus.arsnspells.events;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-CRIT-005 — verifies the Source-Jar scan cannot force a synchronous chunk
 * load. In 2.6.1, {@code scanForSourceJar} called {@code level.getBlockState}
 * across a 9×4×9 volume with no loaded-chunk check; near an unloaded chunk
 * border (login/teleport chunk streaming) the server thread stalled inside
 * {@code ServerChunkCache.getChunkBlocking} — the reported world-load deadlock.
 */
class RegenSynergyHandlerChunkGuardTest {

    @Test
    void scan_isGuardedByChunkLoadCheck() throws IOException {
        String src = Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/events/RegenSynergyHandler.java"));

        assertTrue(src.contains("areScanChunksLoaded"),
            "RegenSynergyHandler must check chunk-loaded state before scanning (ANS-CRIT-005)");
        assertTrue(src.contains("hasChunk"),
            "the guard must use the non-loading hasChunk lookup (ANS-CRIT-005)");

        // The guard must gate the scan call site, and a skipped scan must not
        // poison the cache (a cached false negative would persist for the whole
        // move threshold).
        int tickIdx = src.indexOf("public void onPlayerTick");
        // Prefix match (no closing paren) so the assertions tolerate the
        // configurable radius parameter added in 3.0.1.
        int scanCallIdx = src.indexOf("scanForSourceJar(level, pos", tickIdx);
        int guardIdx = src.indexOf("areScanChunksLoaded(level, pos", tickIdx);
        assertTrue(guardIdx >= 0 && scanCallIdx > guardIdx,
            "onPlayerTick must evaluate areScanChunksLoaded before invoking scanForSourceJar");
    }
}
