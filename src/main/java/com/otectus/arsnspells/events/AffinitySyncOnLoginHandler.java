package com.otectus.arsnspells.events;

import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.network.AffinitySyncPayload;
import com.otectus.arsnspells.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Sends one {@link AffinitySyncPayload} per non-zero {@link AffinityType} when
 * a player joins. Without this, the client attachment stays at 0/null until
 * the next cast triggers a sync — so the HUD and any UI that reads affinity
 * shows incorrect zero values immediately after login.
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
        AffinityData data = player.getData(AttachmentTypes.AFFINITY.get());
        for (AffinityType type : AffinityType.values()) {
            int level = data.getLevel(type);
            if (level > 0) {
                PacketHandler.sendToClient(new AffinitySyncPayload(type, level), player);
            }
        }
    }
}
