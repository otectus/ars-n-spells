package com.otectus.arsnspells.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ANS-CRIT-005 follow-up — direct unit coverage for the chunk-coverage math
 * behind the non-loading scan guard in {@code RegenSynergyHandler}. Pure int
 * math, so this runs without any Minecraft bootstrap.
 */
class ChunkScanUtilTest {

    private static Set<String> chunks(int blockX, int blockZ, int radius) {
        return Arrays.stream(ChunkScanUtil.coveredChunkKeys(blockX, blockZ, radius))
            .mapToObj(k -> ChunkScanUtil.chunkX(k) + "," + ChunkScanUtil.chunkZ(k))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void singleChunk_whenScanFitsInsideOne() {
        // Center of chunk (0,0): blocks 4..12 all fall inside chunk 0.
        assertEquals(Set.of("0,0"), chunks(8, 8, 4));
    }

    @Test
    void fourChunks_atChunkCorner() {
        // Block (0,0) with radius 4 reaches -4..4, spanning chunks -1..0 on both axes.
        assertEquals(Set.of("-1,-1", "-1,0", "0,-1", "0,0"), chunks(0, 0, 4));
    }

    @Test
    void negativeCoords_floorCorrectly() {
        // Block -1 lies in chunk -1 (floor semantics); integer division would
        // wrongly place it in chunk 0. -1 ± 4 spans blocks -5..3 → chunks -1..0.
        assertEquals(Set.of("-1,-1", "-1,0", "0,-1", "0,0"), chunks(-1, -1, 4));
        // Deep negative, fully interior: blocks -28..-20 all inside chunk -2.
        assertEquals(Set.of("-2,-2"), chunks(-24, -24, 4));
    }

    @Test
    void radius8_coversExactlyFourChunks() {
        // A radius-8 scan spans 17 blocks (> 16), so it always crosses exactly
        // one chunk boundary per axis: 2x2 chunks at every alignment. This is
        // the guard's worst case — the config caps the radius at 8.
        for (int x = -16; x <= 16; x++) {
            assertEquals(4, chunks(x, x, 8).size(),
                "radius 8 must cover exactly 2x2 chunks at every alignment");
        }
    }

    @Test
    void packUnpack_roundTrips() {
        long[] keys = ChunkScanUtil.coveredChunkKeys(-100_000, 100_000, 4);
        assertTrue(keys.length > 0);
        for (long key : keys) {
            int cx = ChunkScanUtil.chunkX(key);
            int cz = ChunkScanUtil.chunkZ(key);
            assertEquals(key, ((long) cz << 32) | (cx & 0xFFFFFFFFL));
            assertTrue(cx <= ((-100_000 + 4) >> 4) && cx >= ((-100_000 - 4) >> 4));
            assertTrue(cz <= ((100_000 + 4) >> 4) && cz >= ((100_000 - 4) >> 4));
        }
    }
}
