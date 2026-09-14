package com.otectus.arsnspells.client;

import com.otectus.arsnspells.util.SchoolMappings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Clear the previous server semantic snapshot before another world is joined. */
@Mod.EventBusSubscriber(modid = "ars_n_spells", value = Dist.CLIENT)
public final class ClientSchoolSnapshotLifecycle {
    private ClientSchoolSnapshotLifecycle() {}
    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        SchoolMappings.reset();
    }
}
