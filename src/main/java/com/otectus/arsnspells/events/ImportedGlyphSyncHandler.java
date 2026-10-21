package com.otectus.arsnspells.events;

import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import com.otectus.arsnspells.network.GlyphCatalogHandshakePacket;
import com.otectus.arsnspells.network.GlyphMappingSyncPacket;
import com.otectus.arsnspells.network.PacketHandler;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge fires this when a player joins (with that player) and after {@code /reload} (for every
 * player). A joining player first gets the catalog identity, then the current behaviour view.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public final class ImportedGlyphSyncHandler {
    private ImportedGlyphSyncHandler() {}

    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        GlyphMappingSyncPacket view = new GlyphMappingSyncPacket(GlyphMappingSnapshot.current().clientView());
        if (event.getPlayer() != null) {
            PacketHandler.sendToClient(GlyphCatalogHandshakePacket.current(), event.getPlayer());
            PacketHandler.sendToClient(view, event.getPlayer());
        } else {
            event.getPlayerList().getPlayers().forEach(player -> PacketHandler.sendToClient(view, player));
        }
    }
}
