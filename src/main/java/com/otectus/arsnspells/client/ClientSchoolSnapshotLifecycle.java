package com.otectus.arsnspells.client;

import com.otectus.arsnspells.util.SchoolMappings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Clear the previous server's semantic snapshots (school mappings, imported-glyph view) before another world is joined. */
@Mod.EventBusSubscriber(modid = "ars_n_spells", value = Dist.CLIENT)
public final class ClientSchoolSnapshotLifecycle {
    private ClientSchoolSnapshotLifecycle() {}
    @SubscribeEvent public static void connect(ClientPlayerNetworkEvent.LoggingIn event) {
        GlyphClientSync.onLoggingIn();
    }
    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        SchoolMappings.reset();
        GlyphClientSync.onLoggingOut();
        ClientSettings.reset();
    }
    /** 3.4.1 review SYNC-01: the server's tags arrived; tag-derived views (catalyst members) are re-read. */
    @SubscribeEvent public static void tags(net.minecraftforge.event.TagsUpdatedEvent event) {
        if (event.getUpdateCause() == net.minecraftforge.event.TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) GlyphClientSync.onTagsUpdated();
    }
}
