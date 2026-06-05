package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.config.AnsConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side initialization for Ars 'n' Spells.
 *
 * <p>The in-game config screen is the per-world server config
 * ({@code <world>/serverconfig/ars_n_spells-server.toml}); it is also editable
 * directly, and the {@code /ans} commands expose the same toggles. The gameplay
 * config is a SERVER config, so it is NOT loaded yet at {@link FMLClientSetupEvent}
 * (it loads/syncs on world join) — any config read here must be defensive.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ArsNSpellsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArsNSpellsClient.class);

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("Initializing Ars 'n' Spells client-side features");

        // DEBUG_MODE lives on the SERVER config, which is not loaded at client setup
        // (it syncs on world join). Read defensively so a not-yet-loaded spec cannot
        // abort client init; the diagnostics overlay can be toggled later in-world.
        boolean debugMode = false;
        try {
            debugMode = AnsConfig.DEBUG_MODE.get();
        } catch (Exception ignored) {}
        if (debugMode) {
            LOGGER.info("Debug mode enabled - activating overlay diagnostics");
            OverlayDiagnostics.enable();
        }

        LOGGER.info("Client-side initialization complete");
    }
}
