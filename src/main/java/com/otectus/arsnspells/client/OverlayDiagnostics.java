package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * STUB — depended on Forge's {@code RenderGuiOverlayEvent} to log overlay
 * IDs as a debugging aid. NeoForge 1.21.1's {@code RegisterGuiLayersEvent}
 * gives the layer ResourceLocations directly at registration time, removing
 * the need for runtime tap. Reimplement during Phase 11 GUI work if it
 * still proves useful; for now, debug overlay tracing is disabled.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class OverlayDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayDiagnostics.class);

    public static void enable() {
        LOGGER.debug("OverlayDiagnostics: NeoForge 1.21.1 stub — see RegisterGuiLayersEvent in Phase 11.");
    }

    public static void disable() {}
    public static void printSummary() {}
}
