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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RegenSynergyHandler {
    private static final Map<UUID, SourceJarCache> sourceJarCacheMap = new HashMap<>();

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
                nearSource = scanForSourceJar(level, pos);
                sourceJarCacheMap.put(playerId, new SourceJarCache(pos, nearSource, level.dimension()));
            } else {
                nearSource = cached.nearSource;
            }

            if (nearSource) {
                try {
                    float boost = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get().floatValue()
                        * AnsConfig.SOURCE_JAR_SYNERGY_MULTIPLIER.get().floatValue();
                    MagicData data = MagicData.getPlayerMagicData(player);
                    if (data != null) {
                        data.addMana(boost);
                    }
                } catch (Exception e) {
                    // Silently fail if Iron's API is unavailable
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

    private static boolean scanForSourceJar(Level level, BlockPos pos) {
        for (BlockPos checkPos : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 2, 4))) {
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
