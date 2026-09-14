package com.otectus.arsnspells.events;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.cooldown.CooldownCategory;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.data.CooldownData;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import com.otectus.arsnspells.network.AffinityBulkSyncPayload;
import com.otectus.arsnspells.network.CooldownSyncPayload;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.ResonanceSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;

/**
 * Single owner of per-player capability/attribute resync across the two
 * player-state transitions that drop transient state: <b>respawn</b> and
 * <b>dimension change</b>.
 *
 * <p>The NeoForge attachment data itself survives death via {@code copyOnDeath}
 * ({@link AttachmentTypes}); what is lost is (i) the <em>transient</em> Iron's
 * attribute modifiers that progression and equipment apply with
 * {@code addTransientModifier} and (ii) the client-side mirror of affinity /
 * cooldown / resonance. This handler replays both.
 *
 * <p>Login is intentionally NOT handled here — it is already covered by
 * {@link AffinitySyncOnLoginHandler}, {@link ProgressionHandler#onPlayerLogin},
 * {@link EquipmentHandler#onPlayerLogin}, and {@link ResonanceEvents} — so
 * adding it here would double-send. This restores the respawn/dimension parity
 * the Forge 1.20.1 {@code CapabilityResyncHandler} (2.0.0) provided.
 *
 * <p>Registered unconditionally: it holds no direct Iron's imports (resonance
 * and equipment are reached through port classes that gate Iron's access
 * internally), so it is dedicated-server safe without Iron's.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class CapabilityResyncHandler {

    private CapabilityResyncHandler() {}

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resync(player);
        }
    }

    public static void resync(ServerPlayer player) {
        syncAffinity(player);
        syncCooldowns(player);
        syncResonance(player);
        ProgressionHandler.reapplyAll(player);
        EquipmentIntegration.recomputeFor(player);
    }

    private static void syncAffinity(ServerPlayer player) {
        AffinityData data = player.getData(AttachmentTypes.AFFINITY.get());
        // One packet, not one per school. Affinity is keyed by full school id so addon
        // schools are tracked, which makes the school count unbounded - and this resync runs
        // on login, on respawn and on every dimension change.
        Map<String, Integer> tracked = new java.util.LinkedHashMap<>();
        data.getAllLevels().forEach((key, level) -> {
            if (AnsConfig.ENABLE_AFFINITY_SYSTEM.get() && level != null && level > 0) {
                tracked.put(key, level);
            }
        });
        PacketHandler.sendToClient(new AffinityBulkSyncPayload(tracked), player);
    }

    private static void syncCooldowns(ServerPlayer player) {
        long now = player.level().getGameTime();
        CooldownData data = player.getData(AttachmentTypes.COOLDOWN.get());
        for (CooldownCategory cat : CooldownCategory.values()) {
            long end = data.getLastCast(cat);
            PacketHandler.sendToClient(new CooldownSyncPayload(cat,
                AnsConfig.ENABLE_COOLDOWN_SYSTEM.get() && end > now ? end : 0), player);
        }
    }

    private static void syncResonance(ServerPlayer player) {
        if (!AnsConfig.ENABLE_RESONANCE_SYSTEM.get() || !IronsCompat.isLoaded()) {
            PacketHandler.sendToClient(new ResonanceSyncPayload(1.0f), player);
            return;
        }
        ResonanceManager.computeResonance(player);
        PacketHandler.sendToClient(
            new ResonanceSyncPayload((float) ResonanceManager.getResonance(player)), player);
    }
}
