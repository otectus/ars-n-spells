package com.otectus.arsnspells.events;

import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.SchoolMappingsSyncPayload;
import com.otectus.arsnspells.util.SchoolMappings;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Forge fires this after datapacks are ready, both on login and /reload. */
@EventBusSubscriber(modid = "ars_n_spells")
public final class SchoolMappingSyncHandler {
    private SchoolMappingSyncHandler() {}
    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        SchoolMappingsSyncPayload packet = new SchoolMappingsSyncPayload(SchoolMappings.get());
        if (event.getPlayer() != null) PacketHandler.sendToClient(packet, event.getPlayer());
        else event.getPlayerList().getPlayers().forEach(player -> PacketHandler.sendToClient(packet, player));
    }
}
