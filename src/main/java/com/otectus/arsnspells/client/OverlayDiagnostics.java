package com.otectus.arsnspells.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * STUB — depended on Forge's {@code RenderGuiOverlayEvent} to log overlay
 * IDs as a debugging aid. NeoForge 1.21.1's {@code RegisterGuiLayersEvent}
 * gives the layer ResourceLocations directly at registration time, removing
 * the need for runtime tap. Reimplement during Phase 3 GUI work if it
 * still proves useful; for now, debug overlay tracing is disabled.
 *
 * Phase 3: restore {@code @EventBusSubscriber} once @SubscribeEvent methods
 * land. NeoForge 1.21.1 rejects EventBus registration when a class has no
 * @SubscribeEvent methods.
 */
public class OverlayDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayDiagnostics.class);

    public static void enable() {
        LOGGER.debug("OverlayDiagnostics: NeoForge 1.21.1 stub — see RegisterGuiLayersEvent in Phase 11.");
    }

    public static void disable() {}
    public static void printSummary() {}
}
