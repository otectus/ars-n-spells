package com.otectus.arsnspells.util;

/**
 * Pure chunk-coverage math for area scans (no Minecraft imports so it is
 * unit-testable without a bootstrap).
 *
 * <p>ANS-CRIT-005: {@code RegenSynergyHandler} must verify every chunk covered
 * by its scan volume is already loaded (via the non-loading {@code hasChunk})
 * before reading block states, or the server thread can stall in
 * {@code ServerChunkCache.getChunkBlocking}. This class owns the "which chunks
 * does a scan of pos ± radius touch" computation that guard depends on.
 */
public final class ChunkScanUtil {

    private ChunkScanUtil() {
    }

    /**
     * Chunk positions covered by a horizontal scan of {@code blockX/blockZ ± radius},
     * packed as {@code ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL)}.
     *
     * <p>Uses arithmetic shift ({@code >> 4}) for block→chunk conversion, which
     * floors correctly at negative coordinates (integer division does not).
     */
    public static long[] coveredChunkKeys(int blockX, int blockZ, int radius) {
        int minCX = (blockX - radius) >> 4;
        int maxCX = (blockX + radius) >> 4;
        int minCZ = (blockZ - radius) >> 4;
        int maxCZ = (blockZ + radius) >> 4;
        long[] keys = new long[(maxCX - minCX + 1) * (maxCZ - minCZ + 1)];
        int i = 0;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                keys[i++] = ((long) cz << 32) | (cx & 0xFFFFFFFFL);
            }
        }
        return keys;
    }

    /** Unpack the chunk X coordinate from a key produced by {@link #coveredChunkKeys}. */
    public static int chunkX(long key) {
        return (int) key;
    }

    /** Unpack the chunk Z coordinate from a key produced by {@link #coveredChunkKeys}. */
    public static int chunkZ(long key) {
        return (int) (key >> 32);
    }
}
