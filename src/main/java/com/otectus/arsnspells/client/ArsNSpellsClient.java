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
 * <p>NeoForge 1.21.1 changed the config-screen registration API: Forge's
 * {@code ConfigScreenHandler.ConfigScreenFactory} extension point is gone.
 * In NeoForge 1.21.1 the equivalent is {@code IConfigScreenFactory}
 * registered via {@code ModContainer.registerExtensionPoint(...)} from the
 * mod constructor (not from a client-only event handler). Wiring is
 * deferred to Phase 11 — until then the in-game config screen is
 * unreachable from the Mods menu, but {@code config/ars_n_spells-common.toml}
 * is editable directly and {@code /arsnspells} commands expose the same
 * toggles.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ArsNSpellsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArsNSpellsClient.class);

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("Initializing Ars 'n' Spells client-side features");

        if (AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("Debug mode enabled - activating overlay diagnostics");
            OverlayDiagnostics.enable();
        }

        LOGGER.info("Client-side initialization complete");
    }
}
