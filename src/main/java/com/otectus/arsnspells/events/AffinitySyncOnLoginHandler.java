package com.otectus.arsnspells.events;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.network.AffinitySyncPayload;
import com.otectus.arsnspells.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;

/**
 * Sends one {@link AffinitySyncPayload} per non-zero affinity track when a
 * player joins. Without this, the client attachment stays at 0/null until the
 * next cast triggers a sync — so the HUD and any UI that reads affinity shows
 * incorrect zero values immediately after login. Only non-zero tracks are sent,
 * so payload count scales with schools the player has cast, not schools registered.
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
        for (Map.Entry<String, Integer> entry : data.getAllLevels().entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                PacketHandler.sendToClient(new AffinitySyncPayload(entry.getKey(), entry.getValue()), player);
            }
        }
    }
}
