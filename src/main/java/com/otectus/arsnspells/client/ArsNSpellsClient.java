package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.client.screen.ConfigScreenFactory;
import com.otectus.arsnspells.client.screen.SpellLoomScreen;
import com.otectus.arsnspells.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
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
    public static void registerIconReload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(com.otectus.arsnspells.client.icons.SpellIconRegistry.INSTANCE);
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SPELL_LOOM.get(), SpellLoomScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("Initializing Ars 'n' Spells client-side features");

        // Overlay diagnostics are NOT driven from here. DEBUG_MODE lives on the SERVER
        // config, which is not loaded at client setup — the read always throws and defaults
        // to false, so this used to be the only caller of OverlayDiagnostics.enable() and it
        // could never fire. The tool that dumps real GUI layer ids was therefore unreachable,
        // which is a large part of why the mana-bar layer ids went wrong unnoticed.
        //
        // OverlayDiagnostics.syncWithConfig() is now called from the mod's config
        // load/reload listeners instead, so it follows `/ans debug`, a hand-edited TOML and
        // the config screen, and picks up a dedicated server's value when it syncs.

        // Register the in-game config screen (accessible from the Mods menu).
        // Registered here (CLIENT/MOD bus) so the client-only Screen never
        // classloads on a dedicated server.
        ModList.get().getModContainerById(ArsNSpells.MODID).ifPresent(container ->
            container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (mc, modListScreen) ->
                    ConfigScreenFactory.createConfigScreen(modListScreen)));

        LOGGER.info("Client-side initialization complete");
    }
}
