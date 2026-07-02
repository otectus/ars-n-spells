package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ConfigScreenFactory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side initialization for Ars 'n' Spells.
 * Handles config screen registration and client-only features.
 */
@Mod.EventBusSubscriber(modid = ArsNSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ArsNSpellsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArsNSpellsClient.class);
    
    /**
     * Client setup event
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("Initializing Ars 'n' Spells client-side features");
        
        // Register config screen
        registerConfigScreen();

        // 3.0.0: bind the Spell Loom menu to its screen.
        event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
            com.otectus.arsnspells.registry.ModMenus.SPELL_LOOM.get(),
            com.otectus.arsnspells.client.screen.SpellLoomScreen::new));

        // Enable overlay diagnostics if debug mode is on
        if (AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("Debug mode enabled - activating overlay diagnostics");
            OverlayDiagnostics.enable();
        }

        // N-4: surface silent aura-bar HUD-mixin breakage on Covenant version drift.
        probeCovenantHudCompat();
        
        LOGGER.info("Client-side initialization complete");
    }
    
    /**
     * Register the in-game config screen
     */
    private static void registerConfigScreen() {
        try {
            ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                    (minecraft, screen) -> ConfigScreenFactory.createConfigScreen(screen)
                )
            );
            
            LOGGER.info("Config screen registered successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to register config screen", e);
        }
    }

    /** Covenant version the aura-bar HUD mixin was bytecode-verified against (see MixinResourceBarOverlay). */
    private static final String TESTED_COVENANT_VERSION = "2.2.6";

    /**
     * N-4: warn if Covenant of the Seven is present at a version the aura-bar HUD mixin
     * was not verified against. {@link com.otectus.arsnspells.mixin.covenant.MixinResourceBarOverlay}
     * uses {@code require = 0} (so a mismatch can't crash the client) and is pinned to
     * Covenant {@value #TESTED_COVENANT_VERSION} bytecode offsets. If upstream moves the
     * {@code 2_000_000} divisor or renames {@code ResourceBarOverlay}, the Virtue-Ring aura
     * bar silently falls back to Covenant's 2M divisor — this log makes that diagnosable.
     */
    private static void probeCovenantHudCompat() {
        net.minecraftforge.fml.ModList.get().getModContainerById("covenant_of_the_seven").ifPresent(c -> {
            String version = c.getModInfo().getVersion().toString();
            if (version.contains(TESTED_COVENANT_VERSION)) {
                LOGGER.info("Covenant of the Seven {} detected - aura-bar HUD peak-tracking active.", version);
            } else {
                LOGGER.warn("Covenant of the Seven {} detected, but the aura-bar HUD mixin "
                    + "(MixinResourceBarOverlay) was bytecode-verified against {}. If the Virtue-Ring "
                    + "aura bar fills against Covenant's 2,000,000 cap instead of your personal peak, "
                    + "the mixin did not bind to this version - please report it.",
                    version, TESTED_COVENANT_VERSION);
            }
        });
    }
}
