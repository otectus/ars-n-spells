package com.otectus.arsnspells.events;

import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.network.AffinitySyncPacket;
import com.otectus.arsnspells.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Sends one {@link AffinitySyncPacket} per non-zero {@link AffinityType} when
 * a player joins. Without this, the client capability stays at 0/null until
 * the next cast triggers a sync — so the HUD and any UI that reads affinity
 * shows incorrect zero values immediately after login.
 *
 * <p>{@link com.otectus.arsnspells.events.ProgressionHandler#onPlayerLogin}
 * already re-applies progression attribute modifiers (which are auto-synced),
 * so progression doesn't need its own packet sweep.
 *
 * <p>Iron's-independent: registered unconditionally. Affinity exists on both
 * Ars-only and Ars+Iron's installs.
 */
public class AffinitySyncOnLoginHandler {

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!AnsConfig.ENABLE_AFFINITY_SYSTEM.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
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
}
