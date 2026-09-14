package com.otectus.arsnspells.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Diagnostic tool to log every GUI layer ID being rendered. Opt-in: the
 * per-frame subscribers are registered on the NeoForge game event bus only while
 * diagnostics are enabled (see {@link #enable()}), so there is zero dispatch
 * cost when off (the default). Driven by DEBUG_MODE at client setup.
 *
 * <p>Ported from the Forge 1.20.1 {@code RenderGuiOverlayEvent.Pre} tap to
 * NeoForge 1.21.1's {@link RenderGuiLayerEvent.Pre}, which fires once per
 * registered layer and identifies it via {@link RenderGuiLayerEvent#getName()}.
 *
 * <p>Both taps run at {@link EventPriority#LOWEST} and the Pre tap receives cancelled
 * events, so the log shows the <em>final</em> state of each layer: whether some listener
 * (this mod's {@link ManaBarController} or any other mod) cancelled its Pre, and whether
 * its Post fired at all. {@code GuiLayerManager} neither renders a layer nor posts its Post
 * when Pre is cancelled, and third-party HUD mods commonly hook the vanilla hotbar or
 * effects layer, so "Pre CANCELLED" on {@code minecraft:hotbar} is the line that explains
 * a vanished HUD.
 */
public class OverlayDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayDiagnostics.class);
    /**
     * Synchronized: {@code add} runs on the render thread (once per layer per frame) while
     * {@code clear} runs on whichever thread flipped {@code debug_mode} - the server thread
     * for {@code /ans debug}, the config-watcher thread for a hand-edited TOML. A bare
     * {@code TreeSet} rebalancing under a concurrent traversal corrupts the tree, and the
     * catch below would swallow the resulting exception frame by frame.
     */
    private static final Set<String> loggedOverlays =
        Collections.synchronizedSet(new TreeSet<>());
    private static final Set<String> loggedCancelled =
        Collections.synchronizedSet(new TreeSet<>());
    private static final Set<String> loggedPost =
        Collections.synchronizedSet(new TreeSet<>());

    /** volatile: written off-thread by enable/disable, read on the render thread per layer. */
    private static volatile boolean diagnosticsEnabled = false;

    private OverlayDiagnostics() {}

    /**
     * Turn diagnostics on or off to match {@code debug_mode}.
     *
     * <p>This exists because the tool was previously unreachable. It was enabled only from
     * {@code ArsNSpellsClient} at {@code FMLClientSetupEvent}, gated on
     * {@code AnsConfig.DEBUG_MODE} — but {@code debug_mode} lives on the SERVER config, which
     * is not loaded at client setup. The read there always threw, was swallowed, and defaulted
     * to false, so {@link #enable()} had no reachable caller and the one tool that dumps real
     * GUI layer ids could never be switched on. That is a large part of why the mana-bar layer
     * ids went wrong unnoticed.
     *
     * <p>Called from the mod's config load/reload listeners, so it follows {@code /ans debug},
     * a hand-edited TOML, and the config screen, and it picks up the server's value when a
     * dedicated server syncs its config to the client.
     */
    public static void syncWithConfig() {
        boolean wanted;
        try {
            wanted = com.otectus.arsnspells.config.AnsConfig.debugEnabled();
        } catch (Exception configNotReady) {
            return;
        }
        if (wanted) {
            enable();
        } else {
            disable();
        }
    }

    /** Enable diagnostics: register the per-frame subscribers. */
    public static void enable() {
        if (diagnosticsEnabled) {
            return;
        }
        diagnosticsEnabled = true;
        loggedOverlays.clear();
        loggedCancelled.clear();
        loggedPost.clear();
        NeoForge.EVENT_BUS.register(OverlayDiagnostics.class);
        LOGGER.info("========================================");
        LOGGER.info("Overlay Diagnostics ENABLED");
        LOGGER.info("Will log all GUI layer IDs, cancelled Pre events, and Post events...");
        LOGGER.info("========================================");
    }

    /** Disable diagnostics: unregister the subscribers. */
    public static void disable() {
        if (!diagnosticsEnabled) {
            return;
        }
        diagnosticsEnabled = false;
        NeoForge.EVENT_BUS.unregister(OverlayDiagnostics.class);
        LOGGER.info("========================================");
        LOGGER.info("Overlay Diagnostics DISABLED");
        LOGGER.info("Logged {} unique layers ({} cancelled)", loggedOverlays.size(), loggedCancelled.size());
        LOGGER.info("========================================");
    }

    /**
     * Log each GUI layer ID exactly once, and once per layer whose Pre arrives already
     * cancelled. LOWEST + receiveCanceled so a layer cancelled at a higher priority by any
     * mod is still seen here instead of silently skipped.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderLayerPre(RenderGuiLayerEvent.Pre event) {
        if (!diagnosticsEnabled) {
            return;
        }

        try {
            ResourceLocation layerId = event.getName();
            String layerName = layerId.toString();

            if (loggedOverlays.add(layerName)) {
                LOGGER.info("[OVERLAY] {}", layerName);
                if (layerName.contains("mana") || layerName.contains("Mana")) {
                    LOGGER.info("[OVERLAY] ^^^ MANA-RELATED LAYER DETECTED ^^^");
                }
            }

            // Log the first time each layer is seen cancelled. It will not render and its
            // Post will not fire, so mixins into its render path and Post listeners never run.
            if (event.isCanceled() && loggedCancelled.add(layerName)) {
                LOGGER.info("[OVERLAY] {} Pre CANCELLED - layer not rendered, Post will not fire",
                    layerName);
            }
        } catch (Exception e) {
            LOGGER.error("Error in diagnostics", e);
        }
    }

    /**
     * Log once per layer that its Post fired: proof the layer rendered and the render loop
     * reached the point third-party HUD mods draw from, so a missing HUD is that mod's own doing.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLayerPost(RenderGuiLayerEvent.Post event) {
        if (!diagnosticsEnabled) {
            return;
        }

        try {
            String layerName = event.getName().toString();
            if (loggedPost.add(layerName)) {
                LOGGER.info("[OVERLAY] {} Post fired", layerName);
            }
        } catch (Exception e) {
            LOGGER.error("Error in diagnostics", e);
        }
    }

    /** Print a summary of every layer logged so far. */
    public static void printSummary() {
        LOGGER.info("========================================");
        LOGGER.info("Overlay Diagnostics Summary");
        LOGGER.info("========================================");
        LOGGER.info("Total Unique Layers: {}", loggedOverlays.size());
        LOGGER.info("");
        LOGGER.info("All Layers:");
        // Iteration over a synchronizedSet must hold its monitor; forEach alone does not.
        synchronized (loggedOverlays) {
            loggedOverlays.forEach(overlay -> LOGGER.info("  - {}{}", overlay,
                loggedCancelled.contains(overlay) ? "  (Pre cancelled)"
                    : loggedPost.contains(overlay) ? "" : "  (Post never fired)"));
        }
        LOGGER.info("========================================");
    }
}
