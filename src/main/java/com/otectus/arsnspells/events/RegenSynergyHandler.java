package com.otectus.arsnspells.events;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.ChunkScanUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RegenSynergyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegenSynergyHandler.class);

    // ANS-HIGH-015: ConcurrentHashMap. Forge serialises player events on the main
    // thread in 1.20.1, but the field is shared across all online players and the
    // design had zero thread-safety guarantee; matches the pattern at
    // ResonanceManager.resonanceCache.
    private static final Map<UUID, SourceJarCache> sourceJarCacheMap = new ConcurrentHashMap<>();

    // Debug counters (logged only when debug_mode is on, at most once per
    // DEBUG_LOG_INTERVAL_TICKS; incrementing an AtomicLong is effectively free).
    private static final AtomicLong scansRun = new AtomicLong();
    private static final AtomicLong scansSkippedUnloaded = new AtomicLong();
    private static final AtomicLong jarsFound = new AtomicLong();
    private static final long DEBUG_LOG_INTERVAL_TICKS = 1200; // one minute
    private static final long SLOW_SCAN_WARN_NANOS = 5_000_000L; // 5 ms
    private static long lastDebugLogGameTime = Long.MIN_VALUE;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            return;
        }
        if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
            return;
        }
        // ANS-CRIT-005 follow-up: server-owner kill switch for this feature only.
        if (!AnsConfig.ENABLE_SOURCE_JAR_SYNERGY.get()) {
            return;
        }

        int scanInterval = Math.max(1, AnsConfig.SOURCE_JAR_SCAN_INTERVAL_TICKS.get());
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide() && event.player.tickCount % scanInterval == 0) {
            Player player = event.player;
            Level level = player.level();
            BlockPos pos = player.blockPosition();

            // Check cache: only re-scan if player moved beyond threshold
            UUID playerId = player.getUUID();
            SourceJarCache cached = sourceJarCacheMap.get(playerId);
            double threshold = AnsConfig.SOURCE_JAR_CACHE_MOVE_THRESHOLD.get();
            double thresholdSq = threshold * threshold;

            boolean needsScan = cached == null
                || !cached.dimension.equals(level.dimension())
                || pos.distSqr(cached.scanPosition) > thresholdSq;

            // Defensive clamp even though the config spec already enforces 1..8.
            int radius = Math.min(8, Math.max(1, AnsConfig.SOURCE_JAR_SCAN_RADIUS.get()));

            boolean nearSource;
            if (needsScan) {
                // ANS-CRIT-005: never scan while the covered chunks are still loading.
                // getBlockState on an unloaded chunk forces a synchronous chunk load on
                // the server thread (ServerChunkCache.getChunkBlocking), which deadlocked
                // 2.6.1 during login/teleport chunk streaming. Skip the cycle and leave
                // the cache untouched so the scan retries next second once chunks arrive;
                // caching a result now would pin a false negative for the whole move
                // threshold. The guard stays all-or-nothing rather than scanning loaded
                // chunks individually: a partial scan result cannot be cached safely
                // (it would pin false negatives for jars in the unloaded portion), and
                // an uncached partial scan is behaviourally identical to skip-and-retry.
                if (areScanChunksLoaded(level, pos, radius)) {
                    long startNanos = System.nanoTime();
                    nearSource = scanForSourceJar(level, pos, radius);
                    long elapsed = System.nanoTime() - startNanos;
                    scansRun.incrementAndGet();
                    if (nearSource) {
                        jarsFound.incrementAndGet();
                    }
                    if (elapsed > SLOW_SCAN_WARN_NANOS && isDebugMode()) {
                        LOGGER.warn("[ANS] SourceJar scan took {} ms (radius {})",
                            elapsed / 1_000_000L, radius);
                    }
                    sourceJarCacheMap.put(playerId, new SourceJarCache(pos, nearSource, level.dimension()));
                } else {
                    scansSkippedUnloaded.incrementAndGet();
                    nearSource = false;
                }
            } else {
                nearSource = cached.nearSource;
            }

            maybeLogDebugSummary(level.getGameTime());

            if (nearSource) {
                try {
                    float boost = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get().floatValue()
                        * AnsConfig.SOURCE_JAR_SYNERGY_MULTIPLIER.get().floatValue();
                    com.otectus.arsnspells.bridge.IManaBridge bridge =
                        com.otectus.arsnspells.bridge.BridgeManager.getBridge();
                    float current = bridge.getMana(player);
                    float max = bridge.getMaxMana(player);
                    bridge.setMana(player, Math.min(current + boost, max));
                } catch (Exception e) {
                    // Silently fail if bridge API is unavailable
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        sourceJarCacheMap.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sourceJarCacheMap.remove(event.getEntity().getUUID());
    }

    // The scan volume is pos ± radius horizontally; with the radius capped at 8
    // (span ≤ 17 blocks) it covers at most 2x2 chunks. Checking those once is
    // far cheaper than per-position isLoaded calls over every block, and
    // guarantees scanForSourceJar cannot trigger a load. hasChunk is the
    // non-loading lookup; the coverage math lives in ChunkScanUtil so it can be
    // unit-tested without a Minecraft bootstrap.
    private static boolean areScanChunksLoaded(Level level, BlockPos pos, int radius) {
        for (long key : ChunkScanUtil.coveredChunkKeys(pos.getX(), pos.getZ(), radius)) {
            if (!level.hasChunk(ChunkScanUtil.chunkX(key), ChunkScanUtil.chunkZ(key))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDebugMode() {
        try {
            return AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get();
        } catch (IllegalStateException e) {
            return false; // config not yet loaded
        }
    }

    /** Rate-limited counter summary; counters only, never per-block logging. */
    private static void maybeLogDebugSummary(long gameTime) {
        if (!isDebugMode()) {
            return;
        }
        if (lastDebugLogGameTime != Long.MIN_VALUE
            && gameTime - lastDebugLogGameTime < DEBUG_LOG_INTERVAL_TICKS) {
            return;
        }
        lastDebugLogGameTime = gameTime;
        LOGGER.info("[ANS] SourceJar synergy: {} scans, {} skipped (unloaded chunks), {} jar hits",
            scansRun.getAndSet(0), scansSkippedUnloaded.getAndSet(0), jarsFound.getAndSet(0));
    }

    private static boolean scanForSourceJar(Level level, BlockPos pos, int radius) {
        int minY = Math.max(pos.getY() - 1, level.getMinBuildHeight());
        int maxY = Math.min(pos.getY() + 2, level.getMaxBuildHeight() - 1);
        BlockPos min = new BlockPos(pos.getX() - radius, minY, pos.getZ() - radius);
        BlockPos max = new BlockPos(pos.getX() + radius, maxY, pos.getZ() + radius);
        for (BlockPos checkPos : BlockPos.betweenClosed(min, max)) {
            Block block = level.getBlockState(checkPos).getBlock();
            var blockKey = ForgeRegistries.BLOCKS.getKey(block);
            if (blockKey != null && blockKey.getPath().contains("source_jar")) {
                return true;
            }
        }
        return false;
    }

    private static class SourceJarCache {
        final BlockPos scanPosition;
        final boolean nearSource;
        final ResourceKey<Level> dimension;

        SourceJarCache(BlockPos scanPosition, boolean nearSource, ResourceKey<Level> dimension) {
            this.scanPosition = scanPosition;
            this.nearSource = nearSource;
            this.dimension = dimension;
        }
    }
}
