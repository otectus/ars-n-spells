package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Manages overlay visibility by directly manipulating the overlay registry.
 * This is a more aggressive approach than event cancellation.
 */
public class OverlayManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayManager.class);
    
    private static IGuiOverlay originalIronsOverlay = null;
    private static IGuiOverlay originalArsOverlay = null;
    private static boolean overlaysDisabled = false;
    
    /**
     * Initialize overlay management
     */
    public static void initialize(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                LOGGER.info("Initializing Overlay Manager");
                
                // Get current mode
                ManaUnificationMode mode = BridgeManager.getCurrentMode();
                
                if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
                    LOGGER.info("Mana unification disabled, skipping overlay management");
                    return;
                }
                
                LOGGER.info("Mana mode: {}", mode);
                LOGGER.info("Overlay management initialized");
                
            } catch (Exception e) {
                LOGGER.error("Failed to initialize overlay manager", e);
            }
        });
    }
    
    /**
     * Create a no-op overlay that renders nothing
     */
    private static IGuiOverlay createNoOpOverlay() {
        return (gui, graphics, partialTick, width, height) -> {
            // Render nothing
        };
    }
    
    /**
     * Log overlay status
     */
    public static void logStatus() {
        LOGGER.info("========================================");
        LOGGER.info("Overlay Manager Status");
        LOGGER.info("========================================");
        LOGGER.info("Mana Mode: {}", BridgeManager.getCurrentMode());
        LOGGER.info("Overlays Disabled: {}", overlaysDisabled);
        LOGGER.info("========================================");
    }
}
