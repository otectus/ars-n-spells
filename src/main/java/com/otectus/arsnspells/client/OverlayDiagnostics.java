package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Diagnostic tool to log all overlay IDs being rendered.
 * Enable debug mode to see what overlays are active.
 */
@Mod.EventBusSubscriber(modid = ArsNSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class OverlayDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayDiagnostics.class);
    private static final Set<String> loggedOverlays = new HashSet<>();
    private static boolean diagnosticsEnabled = false;
    
    /**
     * Enable diagnostics mode
     */
    public static void enable() {
        diagnosticsEnabled = true;
        loggedOverlays.clear();
        LOGGER.info("========================================");
        LOGGER.info("Overlay Diagnostics ENABLED");
        LOGGER.info("Will log all overlay IDs...");
        LOGGER.info("========================================");
    }
    
    /**
     * Disable diagnostics mode
     */
    public static void disable() {
        diagnosticsEnabled = false;
        LOGGER.info("========================================");
        LOGGER.info("Overlay Diagnostics DISABLED");
        LOGGER.info("Logged {} unique overlays", loggedOverlays.size());
        LOGGER.info("========================================");
    }
    
    /**
     * Log all overlay IDs (runs once per unique overlay)
     */
    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!diagnosticsEnabled) {
            return;
        }
        
        try {
            ResourceLocation overlayId = event.getOverlay().id();
            String overlayName = overlayId.toString();
            
            // Log each overlay only once
            if (!loggedOverlays.contains(overlayName)) {
                loggedOverlays.add(overlayName);
                LOGGER.info("[OVERLAY] {}", overlayName);
                
                // Highlight mana-related overlays
                if (overlayName.contains("mana") || overlayName.contains("Mana")) {
                    LOGGER.info("[OVERLAY] ^^^ MANA-RELATED OVERLAY DETECTED ^^^");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error in diagnostics", e);
        }
    }
    
    /**
     * Print summary of all logged overlays
     */
    public static void printSummary() {
        LOGGER.info("========================================");
        LOGGER.info("Overlay Diagnostics Summary");
        LOGGER.info("========================================");
        LOGGER.info("Total Unique Overlays: {}", loggedOverlays.size());
        LOGGER.info("");
        LOGGER.info("All Overlays:");
        loggedOverlays.stream().sorted().forEach(overlay -> {
            LOGGER.info("  - {}", overlay);
        });
        LOGGER.info("========================================");
    }
}
