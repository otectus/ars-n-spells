package com.otectus.arsnspells.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.TreeSet;

/**
 * Diagnostic tool to log all overlay IDs being rendered. Opt-in: the per-frame
 * render subscribers are registered on the Forge event bus only while diagnostics
 * are enabled (see {@link #enable()}), so there is zero dispatch cost when off
 * (the default). Toggled by {@code /ans debug} and by DEBUG_MODE at client setup.
 *
 * <p>Both taps run at {@link EventPriority#LOWEST} and the Pre tap receives cancelled
 * events, so the log shows the <em>final</em> state of each overlay: whether some listener
 * (this mod's {@link ManaBarController} or any other mod) cancelled its Pre, and whether
 * its Post fired at all. Forge does not post Post for an overlay whose Pre was cancelled,
 * and third-party HUD mods commonly draw from the Post of a vanilla overlay, so
 * "Pre CANCELLED" on {@code minecraft:hotbar} is the line that explains a vanished HUD.
 */
public class OverlayDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayDiagnostics.class);
    private static final Set<String> loggedOverlays = new TreeSet<>();
    private static final Set<String> loggedCancelled = new TreeSet<>();
    private static final Set<String> loggedPost = new TreeSet<>();
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
        loggedCancelled.clear();
        loggedPost.clear();
        // MED-019: register the per-frame subscribers only while diagnostics are on.
        MinecraftForge.EVENT_BUS.register(OverlayDiagnostics.class);
        LOGGER.info("========================================");
        LOGGER.info("Overlay Diagnostics ENABLED");
        LOGGER.info("Will log all overlay IDs, cancelled Pre events, and Post events...");
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
        LOGGER.info("Logged {} unique overlays ({} cancelled)", loggedOverlays.size(), loggedCancelled.size());
        LOGGER.info("========================================");
    }
    
    /**
     * Log all overlay IDs (runs once per unique overlay), and once per overlay whose Pre
     * arrives already cancelled. LOWEST + receiveCanceled so an overlay cancelled at a
     * higher priority by any mod is still seen here instead of silently skipped.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!diagnosticsEnabled) {
            return;
        }
        
        try {
            ResourceLocation overlayId = event.getOverlay().id();
            String overlayName = overlayId.toString();
            
            // Log each overlay only once
            if (loggedOverlays.add(overlayName)) {
                LOGGER.info("[OVERLAY] {}", overlayName);
                
                // Highlight mana-related overlays
                if (overlayName.contains("mana") || overlayName.contains("Mana")) {
                    LOGGER.info("[OVERLAY] ^^^ MANA-RELATED OVERLAY DETECTED ^^^");
                }
            }

            // Log the first time each overlay is seen cancelled. Its Post will not fire, so
            // anything drawn from that Post (many third-party HUDs) will not render.
            if (event.isCanceled() && loggedCancelled.add(overlayName)) {
                LOGGER.info("[OVERLAY] {} Pre CANCELLED - Post will not fire; nothing drawn from it renders",
                    overlayName);
            }
        } catch (Exception e) {
            LOGGER.error("Error in diagnostics", e);
        }
    }

    /**
     * Log once per overlay that its Post fired: proof the render loop reached the point
     * third-party HUD mods draw from, so a missing HUD is that mod's own doing.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (!diagnosticsEnabled) {
            return;
        }

        try {
            String overlayName = event.getOverlay().id().toString();
            if (loggedPost.add(overlayName)) {
                LOGGER.info("[OVERLAY] {} Post fired", overlayName);
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
        loggedOverlays.forEach(overlay -> LOGGER.info("  - {}{}", overlay,
            loggedCancelled.contains(overlay) ? "  (Pre cancelled)"
                : loggedPost.contains(overlay) ? "" : "  (Post never fired)"));
        LOGGER.info("========================================");
    }
}
