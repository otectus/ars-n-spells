package com.otectus.arsnspells.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.TreeSet;

/**
 * Diagnostic tool to log all overlay IDs being rendered. Opt-in: the per-frame
 * render subscriber is registered on the Forge event bus only while diagnostics
 * are enabled (see {@link #enable()}), so there is zero dispatch cost when off
 * (the default). Toggled by {@code /ans debug} and by DEBUG_MODE at client setup.
 */
public class OverlayDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayDiagnostics.class);
    private static final Set<String> loggedOverlays = new TreeSet<>();
    private static boolean diagnosticsEnabled = false;
    
    /**
     * Enable diagnostics mode
     */
    public static void enable() {
        if (diagnosticsEnabled) {
            return;
        }
        diagnosticsEnabled = true;
        loggedOverlays.clear();
        // MED-019: register the per-frame subscriber only while diagnostics are on.
        MinecraftForge.EVENT_BUS.register(OverlayDiagnostics.class);
        LOGGER.info("========================================");
        LOGGER.info("Overlay Diagnostics ENABLED");
        LOGGER.info("Will log all overlay IDs...");
        LOGGER.info("========================================");
    }
    
    /**
     * Disable diagnostics mode
     */
    public static void disable() {
        if (!diagnosticsEnabled) {
            return;
        }
        diagnosticsEnabled = false;
        MinecraftForge.EVENT_BUS.unregister(OverlayDiagnostics.class);
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
        // TreeSet already iterates in sorted order (OPT-010).
        loggedOverlays.forEach(overlay -> LOGGER.info("  - {}", overlay));
        LOGGER.info("========================================");
    }
}
