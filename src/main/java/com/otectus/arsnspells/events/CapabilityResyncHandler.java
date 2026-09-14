package com.otectus.arsnspells.events;

import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.cooldown.CooldownCategory;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.CooldownData;
import com.otectus.arsnspells.network.AffinitySyncPacket;
import com.otectus.arsnspells.network.CooldownSyncPacket;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.ResonanceSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Single owner of bridge-capability resync across player-state transitions.
 *
 * <p>Before 2.0.0, only {@link com.otectus.arsnspells.events.AffinitySyncOnLoginHandler}
 * sent affinity on login; cooldown and resonance had partial coverage and no
 * respawn/dimension paths. Players saw stale HUDs after death or a Nether
 * transition until the next cast forced a per-event sync.
 *
 * <p>This handler subscribes to the three transition events and pushes a full
 * snapshot of every bridge-owned client-visible capability. Aura is no longer
 * tracked here — Covenant of the Seven owns the aura state and renders its
 * own HUD as of the aura-subsystem deletion.
 *
 * <p>The existing {@code ResonanceEvents.onPlayerLogin} also computes and
 * sends resonance on login; this handler skips resonance on login for the
 * same de-duplication reason, but adds the respawn/dimension paths it lacked.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public final class CapabilityResyncHandler {

    private CapabilityResyncHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        syncAffinity(player);
        syncCooldowns(player);
        // Resonance login is handled by ResonanceEvents.onPlayerLogin (which
        // also recomputes the value).
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        syncAffinity(player);
        syncCooldowns(player);
        syncResonance(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        syncAffinity(player);
        syncCooldowns(player);
        syncResonance(player);
    }

    private static void syncAffinity(ServerPlayer player) {
        java.util.Map<String, Integer> levels = AnsConfig.ENABLE_AFFINITY_SYSTEM.get()
            ? player.getCapability(AffinityData.AFFINITY_DATA).map(AffinityData::getAllLevels).orElse(java.util.Map.of())
            : java.util.Map.of();
        PacketHandler.sendToClient(new AffinitySyncPacket(levels, true), player);
    }

    private static void syncCooldowns(ServerPlayer player) {
        long now = player.level().getGameTime();
        player.getCapability(CooldownData.COOLDOWN_CAP).ifPresent(data -> {
            for (CooldownCategory cat : CooldownCategory.values()) {
                long end = data.getLastCast(cat);
                PacketHandler.sendToClient(new CooldownSyncPacket(cat,
                    AnsConfig.ENABLE_COOLDOWN_SYSTEM.get() && end > now ? end : 0), player);
            }
        });
    }

    private static void syncResonance(ServerPlayer player) {
        if (!AnsConfig.ENABLE_RESONANCE_SYSTEM.get() || !ModList.get().isLoaded("irons_spellbooks")) {
            PacketHandler.sendToClient(new ResonanceSyncPacket(1.0f), player);
            return;
        }
        ResonanceManager.computeResonance(player);
        PacketHandler.sendToClient(
            new ResonanceSyncPacket((float) ResonanceManager.getResonance(player)), player);
    }

    /** Called after every server config transition as well as ordinary player lifecycle events. */
    public static void syncAll(ServerPlayer player) {
        syncAffinity(player);
        syncCooldowns(player);
        syncResonance(player);
    }
}
