package com.otectus.arsnspells.events;

import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegenSynergyHandler {
    // ANS-HIGH-015: ConcurrentHashMap. Forge serialises player events on the main
    // thread in 1.20.1, but the field is shared across all online players and the
    // design had zero thread-safety guarantee; matches the pattern at
    // ResonanceManager.resonanceCache.
    private static final Map<UUID, SourceJarCache> sourceJarCacheMap = new ConcurrentHashMap<>();

    private static final int SCAN_RADIUS = 4;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            return;
        }
        if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
            return;
        }

        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide() && event.player.tickCount % 20 == 0) {
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

            boolean nearSource;
            if (needsScan) {
                // ANS-CRIT-005: never scan while the covered chunks are still loading.
                // getBlockState on an unloaded chunk forces a synchronous chunk load on
                // the server thread (ServerChunkCache.getChunkBlocking), which deadlocked
                // 2.6.1 during login/teleport chunk streaming. Skip the cycle and leave
                // the cache untouched so the scan retries next second once chunks arrive;
                // caching a result now would pin a false negative for the whole move
                // threshold.
                if (areScanChunksLoaded(level, pos)) {
                    nearSource = scanForSourceJar(level, pos);
                    sourceJarCacheMap.put(playerId, new SourceJarCache(pos, nearSource, level.dimension()));
                } else {
                    nearSource = false;
                }
            } else {
                nearSource = cached.nearSource;
            }

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

    // The scan volume is pos ± 4 horizontally, so it spans at most 4 chunks.
    // Checking those once is far cheaper than per-position isLoaded calls over
    // all 324 blocks, and guarantees scanForSourceJar cannot trigger a load.
    private static boolean areScanChunksLoaded(Level level, BlockPos pos) {
        int minCX = (pos.getX() - SCAN_RADIUS) >> 4;
        int maxCX = (pos.getX() + SCAN_RADIUS) >> 4;
        int minCZ = (pos.getZ() - SCAN_RADIUS) >> 4;
        int maxCZ = (pos.getZ() + SCAN_RADIUS) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean scanForSourceJar(Level level, BlockPos pos) {
        int minY = Math.max(pos.getY() - 1, level.getMinBuildHeight());
        int maxY = Math.min(pos.getY() + 2, level.getMaxBuildHeight() - 1);
        BlockPos min = new BlockPos(pos.getX() - SCAN_RADIUS, minY, pos.getZ() - SCAN_RADIUS);
        BlockPos max = new BlockPos(pos.getX() + SCAN_RADIUS, maxY, pos.getZ() + SCAN_RADIUS);
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
