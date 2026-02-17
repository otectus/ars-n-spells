package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controls mana bar visibility using Forge's overlay event system.
 * This is a more reliable approach than mixins for hiding overlays.
 */
@Mod.EventBusSubscriber(modid = ArsNSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ManaBarController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManaBarController.class);
    private static boolean loggedOnce = false;
    
    /**
     * Handle overlay rendering - cancel specific overlays based on mana mode
     * Using HIGHEST priority to run before the overlays render
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        try {
            // Only process if mana unification is enabled
            if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
                return;
            }
            
            // Get current mana mode
            ManaUnificationMode mode = BridgeManager.getCurrentMode();
            
            // Get the overlay being rendered
            String overlayId = event.getOverlay().id().toString();
            
            // Log once for debugging
            if (!loggedOnce && AnsConfig.DEBUG_MODE.get()) {
                LOGGER.info("[ManaBarController] Mana mode: {}, Overlay: {}", mode, overlayId);
                loggedOnce = true;
            }
            
            // Determine which bar to show in hybrid mode
            boolean hybridShowIrons = mode == ManaUnificationMode.HYBRID
                && "irons".equalsIgnoreCase(AnsConfig.HYBRID_MANA_BAR.get());

            // Handle Iron's Spellbooks mana bar
            if (overlayId.contains("irons_spellbooks") && overlayId.contains("mana")) {
                if (mode == ManaUnificationMode.ARS_PRIMARY ||
                    (mode == ManaUnificationMode.HYBRID && !hybridShowIrons)) {
                    event.setCanceled(true);
                    if (AnsConfig.DEBUG_MODE.get()) {
                        LOGGER.debug("[ManaBarController] Cancelled ISS mana bar (mode: {})", mode);
                    }
                }
            }

            // Handle Ars Nouveau mana bar
            if (overlayId.contains("ars_nouveau") && overlayId.contains("mana")) {
                if (mode == ManaUnificationMode.ISS_PRIMARY ||
                    (mode == ManaUnificationMode.HYBRID && hybridShowIrons)) {
                    event.setCanceled(true);
                    if (AnsConfig.DEBUG_MODE.get()) {
                        LOGGER.debug("[ManaBarController] Cancelled Ars mana bar (mode: {})", mode);
                    }
                }
            }
        } catch (Exception e) {
            // Fail silently to prevent crashes
            if (AnsConfig.DEBUG_MODE.get()) {
                LOGGER.error("[ManaBarController] Error in overlay handler", e);
            }
        }
    }
}
