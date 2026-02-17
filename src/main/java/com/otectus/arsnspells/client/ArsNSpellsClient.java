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
        
        // Initialize overlay manager
        OverlayManager.initialize(event);
        
        // Enable overlay diagnostics if debug mode is on
        if (AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("Debug mode enabled - activating overlay diagnostics");
            OverlayDiagnostics.enable();
        }
        
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
}
