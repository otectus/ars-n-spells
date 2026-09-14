package com.otectus.arsnspells.events;

import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.SchoolMappingsSyncPacket;
import com.otectus.arsnspells.util.SchoolMappings;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge fires this after datapacks are ready, both on login and /reload. */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public final class SchoolMappingSyncHandler {
    private SchoolMappingSyncHandler() {}
    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        SchoolMappingsSyncPacket packet = new SchoolMappingsSyncPacket(SchoolMappings.get());
        if (event.getPlayer() != null) PacketHandler.sendToClient(packet, event.getPlayer());
        else event.getPlayerList().getPlayers().forEach(player -> PacketHandler.sendToClient(packet, player));
    }
}
