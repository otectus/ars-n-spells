package com.otectus.arsnspells.events;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.IManaBridge;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Source Jar proximity regen. Standing within a fixed 9×3×9 box of an Ars
 * Nouveau Source Jar feeds the unified mana pool once per second; the boost is
 * {@code CONVERSION_RATE_ARS_TO_IRON × SOURCE_JAR_SYNERGY_MULTIPLIER} routed
 * through {@link BridgeManager#getBridge()} so it lands in whichever pool the
 * active mode owns.
 *
 * <p>The scan is the hot path, so results are cached per player and only
 * re-scanned when the player moves past {@code SOURCE_JAR_CACHE_MOVE_THRESHOLD}
 * blocks or changes dimension; the cache is evicted on logout / dimension change.
 *
 * <p>This restores the Forge 1.20.1 behaviour. The pre-parity NeoForge port had
 * mistakenly rewired this handler to re-feed Iron's own {@code MANA_REGEN}
 * attribute back into the pool every second (a double-feed on top of Iron's
 * native regen tick) and dropped the Source-Jar feature entirely.
 *
 * <p>Holds no Iron's imports — it gates on {@link IronsCompat#isLoaded()} to
 * match the canonical "synergy only in Iron's setups" scope, but is otherwise
 * dedicated-server safe.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class RegenSynergyHandler {

    private static final Map<UUID, SourceJarCache> sourceJarCacheMap = new ConcurrentHashMap<>();

    private RegenSynergyHandler() {}

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!IronsCompat.isLoaded()) {
            return;
        }
        if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }

        Level level = player.level();
        BlockPos pos = player.blockPosition();
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

    private static boolean scanForSourceJar(Level level, BlockPos pos) {
        for (BlockPos checkPos : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 2, 4))) {
            Block block = level.getBlockState(checkPos).getBlock();
            ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(block);
            if (blockKey != null && blockKey.getPath().contains("source_jar")) {
                return true;
            }
        }
        return false;
    }

    private static final class SourceJarCache {
        final BlockPos scanPosition;
        final boolean nearSource;
        final ResourceKey<Level> dimension;

        SourceJarCache(BlockPos scanPosition, boolean nearSource, ResourceKey<Level> dimension) {
            this.scanPosition = scanPosition.immutable();
            this.nearSource = nearSource;
            this.dimension = dimension;
        }
    }
}
