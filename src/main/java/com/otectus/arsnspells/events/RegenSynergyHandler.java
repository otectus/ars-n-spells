package com.otectus.arsnspells.events;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.IManaBridge;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.registry.ModTags;
import com.otectus.arsnspells.util.ChunkScanUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Source Jar proximity regen. Standing within {@code source_jar_scan_radius}
 * blocks (Y −1..+2) of an Ars Nouveau Source Jar feeds the unified mana pool
 * once per {@code source_jar_scan_interval_ticks}; the boost is
 * {@code CONVERSION_RATE_ARS_TO_IRON × SOURCE_JAR_SYNERGY_MULTIPLIER} routed
 * through {@link BridgeManager#getBridge()} so it lands in whichever pool the
 * active mode owns.
 *
 * <p>The scan is the hot path, so results are cached per player and only
 * re-scanned when the player moves past {@code SOURCE_JAR_CACHE_MOVE_THRESHOLD}
 * blocks or changes dimension; the cache is evicted on logout / dimension change.
 *
 * <p>ANS-CRIT-005: the scan never runs while any covered chunk is still loading
 * ({@code hasChunk} guard) — {@code getBlockState} on an unloaded chunk forces a
 * synchronous chunk load on the server thread, which deadlocked during
 * login/teleport chunk streaming. Skipped cycles retry on the next interval
 * without caching a result.
 *
 * <p>Holds no Iron's imports — it gates on {@link IronsCompat#isLoaded()} to
 * match the canonical "synergy only in Iron's setups" scope, but is otherwise
 * dedicated-server safe.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class RegenSynergyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegenSynergyHandler.class);

    private static final Map<UUID, SourceJarCache> sourceJarCacheMap = new ConcurrentHashMap<>();

    // Debug counters (logged only when debug_mode is on, at most once per
    // DEBUG_LOG_INTERVAL_TICKS; incrementing an AtomicLong is effectively free).
    private static final AtomicLong scansRun = new AtomicLong();
    private static final AtomicLong scansSkippedUnloaded = new AtomicLong();
    private static final AtomicLong jarsFound = new AtomicLong();
    private static final long DEBUG_LOG_INTERVAL_TICKS = 1200; // one minute
    private static final long SLOW_SCAN_WARN_NANOS = 5_000_000L; // 5 ms
    private static long lastDebugLogGameTime = Long.MIN_VALUE;

    private RegenSynergyHandler() {}

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        // Entity check first: this fires for client-side players too, where the SERVER config may
        // not be loaded, and the instanceof is the cheapest gate here.
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!IronsCompat.isLoaded()) {
            return;
        }
        // F5: route the unification gate through BridgeManager (single source of
        // truth for mode-dependent logic), not a raw config read.
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }
        // ANS-CRIT-005 follow-up: server-owner kill switch for this feature only.
        if (!AnsConfig.flag(AnsConfig.ENABLE_SOURCE_JAR_SYNERGY, false)) {
            return;
        }
        int scanInterval = Math.max(1, AnsConfig.SOURCE_JAR_SCAN_INTERVAL_TICKS.get());
        if (player.tickCount % scanInterval != 0) {
            return;
        }

        Level level = player.level();
        BlockPos pos = player.blockPosition();
        UUID playerId = player.getUUID();

        SourceJarCache cached = sourceJarCacheMap.get(playerId);
        double threshold = AnsConfig.SOURCE_JAR_CACHE_MOVE_THRESHOLD.get();
        double thresholdSq = threshold * threshold;

        boolean sameDimension = cached != null && cached.dimension.equals(level.dimension());
        boolean needsScan = !sameDimension || pos.distSqr(cached.scanPosition) > thresholdSq;

        // Defensive clamp even though the config spec already enforces 1..8.
        int radius = Math.min(8, Math.max(1, AnsConfig.SOURCE_JAR_SCAN_RADIUS.get()));

        // Before paying for the full sweep, re-check the one block the last scan actually
        // found. While the player is working near their jars — which is the whole point of
        // the feature — that single read answers the question and the cache is simply
        // re-centred on the new position.
        if (needsScan && sameDimension
            && cachedJarStillValid(level, cached.jarPosition, pos, radius)) {
            cached = new SourceJarCache(pos, cached.jarPosition, level.dimension());
            sourceJarCacheMap.put(playerId, cached);
            needsScan = false;
        }

        boolean nearSource;
        if (needsScan) {
            // ANS-CRIT-005: never scan while the covered chunks are still loading.
            // Skip the cycle and leave the cache untouched so the scan retries next
            // interval once chunks arrive; caching a result now would pin a false
            // negative for the whole move threshold. The guard stays all-or-nothing:
            // a partial scan result cannot be cached safely, and an uncached partial
            // scan is behaviourally identical to skip-and-retry.
            if (areScanChunksLoaded(level, pos, radius)) {
                long startNanos = System.nanoTime();
                BlockPos jar = findSourceJar(level, pos, radius);
                nearSource = jar != null;
                long elapsed = System.nanoTime() - startNanos;
                scansRun.incrementAndGet();
                if (nearSource) {
                    jarsFound.incrementAndGet();
                }
                if (elapsed > SLOW_SCAN_WARN_NANOS && isDebugMode()) {
                    LOGGER.warn("[ANS] SourceJar scan took {} ms (radius {})",
                        elapsed / 1_000_000L, radius);
                }
                sourceJarCacheMap.put(playerId, new SourceJarCache(pos, jar, level.dimension()));
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
                IManaBridge bridge = BridgeManager.getBridge();
                float current = bridge.getMana(player);
                float max = bridge.getMaxMana(player);
                bridge.setMana(player, Math.min(current + boost, max));
            } catch (Exception ignored) {
                // Bridge API unavailable — skip this tick.
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        sourceJarCacheMap.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
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
            return AnsConfig.debugEnabled();
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

    /**
     * First Source Jar in the scan volume, or null.
     *
     * <p>Returns the position rather than a boolean so the cache can remember it. The scan
     * covers {@code radius} horizontally and y-1..y+2, which at the default radius of 4 is
     * 324 block reads — and the cache is invalidated by moving
     * {@code source_jar_cache_move_threshold} blocks, which also defaults to 4. A walking
     * player clears that every interval, so the cached answer almost never survived and the
     * full sweep ran on essentially every scan.
     */
    private static BlockPos findSourceJar(Level level, BlockPos pos, int radius) {
        int minY = Math.max(pos.getY() - 1, level.getMinBuildHeight());
        int maxY = Math.min(pos.getY() + 2, level.getMaxBuildHeight() - 1);
        BlockPos min = new BlockPos(pos.getX() - radius, minY, pos.getZ() - radius);
        BlockPos max = new BlockPos(pos.getX() + radius, maxY, pos.getZ() + radius);
        for (BlockPos checkPos : BlockPos.betweenClosed(min, max)) {
            // Audit F-2: tag-driven (ars_n_spells:source_jars, datapack-extensible)
            // instead of a per-block registry-key lookup + substring match — also
            // cheaper: BlockState.is(TagKey) is a set lookup with no allocation.
            if (level.getBlockState(checkPos).is(ModTags.SOURCE_JARS)) {
                return checkPos.immutable();
            }
        }
        return null;
    }

    /**
     * True when the jar the last scan found is still a jar and still inside the scan volume
     * around {@code pos}. One block read instead of the full sweep.
     *
     * <p>Only ever used to shortcut a <em>positive</em> answer: a jar that has been broken,
     * or that the player has walked away from, falls through to the full scan rather than
     * being reported as absent, so this cannot manufacture a false negative either.
     */
    private static boolean cachedJarStillValid(Level level, BlockPos jar, BlockPos pos, int radius) {
        if (jar == null) {
            return false;
        }
        if (Math.abs(jar.getX() - pos.getX()) > radius
            || Math.abs(jar.getZ() - pos.getZ()) > radius
            || jar.getY() < pos.getY() - 1 || jar.getY() > pos.getY() + 2) {
            return false;
        }
        return level.getBlockState(jar).is(ModTags.SOURCE_JARS);
    }

    private static final class SourceJarCache {
        final BlockPos scanPosition;
        final boolean nearSource;
        final ResourceKey<Level> dimension;
        /** Where the jar was, when one was found. Null when the scan came up empty. */
        final BlockPos jarPosition;

        SourceJarCache(BlockPos scanPosition, BlockPos jarPosition, ResourceKey<Level> dimension) {
            this.scanPosition = scanPosition.immutable();
            this.jarPosition = jarPosition;
            this.nearSource = jarPosition != null;
            this.dimension = dimension;
        }
    }
}
