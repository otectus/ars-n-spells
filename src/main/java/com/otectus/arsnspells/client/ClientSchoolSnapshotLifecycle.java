package com.otectus.arsnspells.client;

import com.otectus.arsnspells.util.SchoolMappings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** Clear the previous server semantic snapshot before another world is joined. */
@EventBusSubscriber(modid = "ars_n_spells", value = Dist.CLIENT)
public final class ClientSchoolSnapshotLifecycle {
    private ClientSchoolSnapshotLifecycle() {}
    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        SchoolMappings.reset();
    }
}
