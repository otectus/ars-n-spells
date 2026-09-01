package com.otectus.arsnspells.client;

import net.minecraft.resources.ResourceLocation;
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
 * per-frame subscriber is registered on the NeoForge game event bus only while
 * diagnostics are enabled (see {@link #enable()}), so there is zero dispatch
 * cost when off (the default). Driven by DEBUG_MODE at client setup.
 *
 * <p>Ported from the Forge 1.20.1 {@code RenderGuiOverlayEvent.Pre} tap to
 * NeoForge 1.21.1's {@link RenderGuiLayerEvent.Pre}, which fires once per
 * registered layer and identifies it via {@link RenderGuiLayerEvent#getName()}.
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

    /** Enable diagnostics: register the per-frame subscriber. */
    public static void enable() {
        if (diagnosticsEnabled) {
            return;
        }
        diagnosticsEnabled = true;
        loggedOverlays.clear();
        NeoForge.EVENT_BUS.register(OverlayDiagnostics.class);
        LOGGER.info("========================================");
        LOGGER.info("Overlay Diagnostics ENABLED");
        LOGGER.info("Will log all GUI layer IDs...");
        LOGGER.info("========================================");
    }

    /** Disable diagnostics: unregister the subscriber. */
    public static void disable() {
        if (!diagnosticsEnabled) {
            return;
        }
        diagnosticsEnabled = false;
        NeoForge.EVENT_BUS.unregister(OverlayDiagnostics.class);
        LOGGER.info("========================================");
        LOGGER.info("Overlay Diagnostics DISABLED");
        LOGGER.info("Logged {} unique layers", loggedOverlays.size());
        LOGGER.info("========================================");
    }

    /** Log each GUI layer ID exactly once. */
    @SubscribeEvent
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
            loggedOverlays.forEach(overlay -> LOGGER.info("  - {}", overlay));
        }
        LOGGER.info("========================================");
    }
}
