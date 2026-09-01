package com.otectus.arsnspells.data;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.util.SchoolMappings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Game-bus lifecycle for the datapack-driven glyph → school overrides.
 *
 * <p>Its own class rather than methods on {@link ArsNSpells}: that class is deliberately
 * mod-bus only (its constructor documents why it is never registered on the game bus), and
 * {@code @EventBusSubscriber} keeps the two handlers together with the loader they drive.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class SchoolDataEvents {

    private SchoolDataEvents() {}

    /**
     * Register the datapack-driven glyph → school overrides. Fires on world load and on
     * {@code /reload}, so pack authors can iterate without restarting.
     */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new GlyphSchoolReloadListener());
    }

    /**
     * Drop datapack overrides when the server stops, so a single-player session that loads a
     * pack with overrides does not leak them into the next world opened without it.
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SchoolMappings.reset();
    }
}
