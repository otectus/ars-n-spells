package com.otectus.arsnspells.events;

import com.otectus.arsnspells.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-CRIT-005 - the Source Jar scan must never force a synchronous chunk load.
 *
 * <p>The scan reads a 9x4x9 block volume. With no loaded-chunk check, a player near an
 * unloaded chunk border - login, teleport, any chunk streaming - stalled the server thread
 * inside {@code ServerChunkCache.getChunkBlocking}. That was the reported world-load deadlock.
 */
class RegenSynergyHandlerChunkGuardTest {

    private static String source() throws IOException {
        return Files.readString(
            TestPaths.of("src/main/java/com/otectus/arsnspells/events/RegenSynergyHandler.java"));
    }

    @Test
    void scan_isGuardedByChunkLoadCheck() throws IOException {
        String src = source();
        assertTrue(src.contains("areScanChunksLoaded"),
            "the handler must check chunk-loaded state before scanning (ANS-CRIT-005)");
        assertTrue(src.contains("hasChunk"),
            "the guard must use the non-loading hasChunk lookup, not getChunk (ANS-CRIT-005)");
    }

    @Test
    void guardIsEvaluatedBeforeTheScan() throws IOException {
        String src = source();
        int tickIdx = src.indexOf("public static void onPlayerTickPost");
        assertTrue(tickIdx > 0, "the player tick handler must exist");
        int guardIdx = src.indexOf("areScanChunksLoaded(level, pos", tickIdx);
        int scanCallIdx = src.indexOf("findSourceJar(level, pos", tickIdx);
        assertTrue(guardIdx > 0 && scanCallIdx > guardIdx,
            "the tick must evaluate areScanChunksLoaded before invoking the scan");
    }

    @Test
    void aSkippedScanDoesNotPoisonTheCache() throws IOException {
        // Caching a false negative would pin "no jar here" for the whole move threshold,
        // so the chunk-unloaded branch must leave the cache untouched and retry next interval.
        String src = source();
        // Anchor inside the tick method, not on the field declaration of the same name.
        int tickIdx = src.indexOf("public static void onPlayerTickPost");
        assertTrue(tickIdx > 0, "the player tick handler must exist");
        int elseIdx = src.indexOf("scansSkippedUnloaded.incrementAndGet()", tickIdx);
        assertTrue(elseIdx > 0, "the skipped-scan branch must be counted");
        int methodEnd = src.indexOf("\n    }", elseIdx);
        int nextPut = src.indexOf("sourceJarCacheMap.put", elseIdx);
        assertTrue(nextPut < 0 || nextPut > methodEnd,
            "the chunk-unloaded branch must not write to the proximity cache: a cached "
                + "false negative would pin \"no jar here\" for the whole move threshold");
    }
}
