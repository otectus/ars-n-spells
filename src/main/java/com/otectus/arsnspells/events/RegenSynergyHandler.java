package com.otectus.arsnspells.events;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.ChunkScanUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

    /** Audit D4: log the first boost failure per session so a broken bridge isn't invisible. */
    private static final java.util.concurrent.atomic.AtomicBoolean loggedBoostFailure =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) return;
        Player player = event.player;
        UUID playerId = player.getUUID();
        if (!IronsCompat.isLoaded() || !BridgeManager.isUnificationEnabled()
                || !AnsConfig.ENABLE_SOURCE_JAR_SYNERGY.get()) {
            sourceJarCacheMap.remove(playerId);
            return;
        }
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        long now = level.getGameTime();
        int scanInterval = Math.max(1, AnsConfig.SOURCE_JAR_SCAN_INTERVAL_TICKS.get());
        int radius = Math.min(8, Math.max(1, AnsConfig.SOURCE_JAR_SCAN_RADIUS.get()));
        double threshold = AnsConfig.SOURCE_JAR_CACHE_MOVE_THRESHOLD.get();
        SourceJarCache cached = sourceJarCacheMap.get(playerId);
        boolean needsScan = cached == null || !cached.dimension.equals(level.dimension())
            || cached.radius != radius || cached.generation != tagGeneration
            || pos.distSqr(cached.scanPosition) > threshold * threshold
            || com.otectus.arsnspells.util.SourceSynergyPolicy.expired(cached.scannedAt, now, scanInterval);
        boolean nearSource = cached != null && cached.nearSource;
        if (needsScan) {
            // Guard every covered chunk before any block read. An unavailable chunk is
            // not cached as a negative result, and no stale positive earns mana meanwhile.
            if (areScanChunksLoaded(level, pos, radius)) {
                long startNanos = System.nanoTime();
                nearSource = scanForSourceJar(level, pos, radius);
                long elapsed = System.nanoTime() - startNanos;
                scansRun.incrementAndGet();
                if (nearSource) jarsFound.incrementAndGet();
                if (elapsed > SLOW_SCAN_WARN_NANOS && isDebugMode()) {
                    LOGGER.warn("[ANS] SourceJar scan took {} ms (radius {})", elapsed / 1_000_000L, radius);
                }
                sourceJarCacheMap.put(playerId,
                    new SourceJarCache(pos.immutable(), nearSource, level.dimension(), now, radius, tagGeneration));
            } else {
                scansSkippedUnloaded.incrementAndGet();
                sourceJarCacheMap.remove(playerId);
                nearSource = false;
            }
        }
        maybeLogDebugSummary(now);
        if (!nearSource) return;
        try {
            double rate = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get()
                * AnsConfig.SOURCE_JAR_SYNERGY_MULTIPLIER.get();
            float boost = (float) com.otectus.arsnspells.util.SourceSynergyPolicy.income(rate, 1);
            com.otectus.arsnspells.bridge.IManaBridge bridge = BridgeManager.getBridge();
            float current = bridge.getMana(player);
            float max = bridge.getMaxMana(player);
            bridge.setMana(player, Math.min(current + boost, max));
        } catch (Exception e) {
            if (loggedBoostFailure.compareAndSet(false, true)) {
                LOGGER.warn("[ANS] Source Jar synergy mana boost failed; further failures logged at debug", e);
            } else {
                LOGGER.debug("[ANS] Source Jar synergy mana boost failed", e);
            }
        }
    }

    private static volatile long tagGeneration;

    @SubscribeEvent
    public void onTagsUpdated(net.minecraftforge.event.TagsUpdatedEvent event) {
        tagGeneration++;
        sourceJarCacheMap.clear();
    }

    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        sourceJarCacheMap.clear();
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
            // Audit F-2: tag-driven (ars_n_spells:source_jars, datapack-extensible)
            // instead of a per-block registry-key lookup + substring match — also
            // cheaper: BlockState.is(TagKey) is a set lookup with no allocation.
            if (level.getBlockState(checkPos).is(com.otectus.arsnspells.registry.ModTags.SOURCE_JARS)) {
                return true;
            }
        }
        return false;
    }

    private static class SourceJarCache {
        final BlockPos scanPosition;
        final boolean nearSource;
        final ResourceKey<Level> dimension;
        final long scannedAt;
        final int radius;
        final long generation;

        SourceJarCache(BlockPos scanPosition, boolean nearSource, ResourceKey<Level> dimension,
                       long scannedAt, int radius, long generation) {
            this.scanPosition = scanPosition;
            this.nearSource = nearSource;
            this.dimension = dimension;
            this.scannedAt = scannedAt;
            this.radius = radius;
            this.generation = generation;
        }
    }
}