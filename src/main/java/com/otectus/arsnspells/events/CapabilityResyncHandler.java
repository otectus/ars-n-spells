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
 * snapshot of every bridge-owned client-visible capability. {@link
 * com.otectus.arsnspells.aura.AuraCapabilityProvider} already covers Aura on
 * all three events (added in 1.10.0), so we deliberately skip Aura here to
 * avoid double-firing.
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
        // also recomputes the value). Aura login is handled by
        // AuraCapabilityProvider.onPlayerLoggedInEvent.
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        syncAffinity(player);
        syncCooldowns(player);
        syncResonance(player);
        // Aura respawn is handled by AuraCapabilityProvider.onPlayerClone.
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        syncAffinity(player);
        syncCooldowns(player);
        syncResonance(player);
        // Aura dimension is handled by AuraCapabilityProvider.onPlayerChangedDimension.
    }

    private static void syncAffinity(ServerPlayer player) {
        if (!AnsConfig.ENABLE_AFFINITY_SYSTEM.get()) {
            return;
        }
        player.getCapability(AffinityData.AFFINITY_DATA).ifPresent(data -> {
            for (AffinityType type : AffinityType.values()) {
                int level = data.getLevel(type);
                if (level > 0) {
                    PacketHandler.sendToClient(new AffinitySyncPacket(type, level), player);
                }
            }
        });
    }

    private static void syncCooldowns(ServerPlayer player) {
        if (!AnsConfig.ENABLE_COOLDOWN_SYSTEM.get()) {
            return;
        }
        long now = player.level().getGameTime();
        player.getCapability(CooldownData.COOLDOWN_CAP).ifPresent(data -> {
            for (CooldownCategory cat : CooldownCategory.values()) {
                long end = data.getLastCast(cat);
                if (end > now) {
                    PacketHandler.sendToClient(new CooldownSyncPacket(cat, end), player);
                }
            }
        });
    }

    private static void syncResonance(ServerPlayer player) {
        if (!AnsConfig.ENABLE_RESONANCE_SYSTEM.get() || !ModList.get().isLoaded("irons_spellbooks")) {
            return;
        }
        ResonanceManager.computeResonance(player);
        PacketHandler.sendToClient(
            new ResonanceSyncPacket((float) ResonanceManager.getResonance(player)), player);
    }
}
