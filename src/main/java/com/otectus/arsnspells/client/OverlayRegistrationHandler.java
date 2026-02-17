package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles mana bar overlay visibility using Forge's overlay event system.
 * Cancels rendering of specific overlays based on mana unification mode.
 */
@Mod.EventBusSubscriber(modid = ArsNSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class OverlayRegistrationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayRegistrationHandler.class);
    
    /**
     * Cancel overlay rendering based on mana mode.
     * Using HIGHEST priority to intercept before rendering.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        try {
            // Only process if mana unification is enabled
            if (!AnsConfig.ENABLE_MANA_UNIFICATION.get()) {
                return;
            }
            
            // Get overlay ID
            ResourceLocation overlayId = event.getOverlay().id();
            String overlayName = overlayId.toString();
            
            // Get current mana mode
            ManaUnificationMode mode = BridgeManager.getCurrentMode();
            
            // Cancel Iron's Spellbooks mana bar if needed
            if (overlayName.equals("irons_spellbooks:mana_bar")) {
                if (mode == ManaUnificationMode.ARS_PRIMARY || mode == ManaUnificationMode.HYBRID) {
                    event.setCanceled(true);
                    if (AnsConfig.DEBUG_MODE.get()) {
                        LOGGER.debug("Cancelled Iron's Spellbooks mana bar overlay (mode: {})", mode);
                    }
                }
            }
            
            // Cancel Ars Nouveau mana bar if needed
            if (overlayName.equals("ars_nouveau:mana_bar") || overlayName.contains("ars_nouveau") && overlayName.contains("mana")) {
                if (mode == ManaUnificationMode.ISS_PRIMARY) {
                    event.setCanceled(true);
                    if (AnsConfig.DEBUG_MODE.get()) {
                        LOGGER.debug("Cancelled Ars Nouveau mana bar overlay (mode: {})", mode);
                    }
                }
            }
        } catch (Exception e) {
            // Fail silently
            if (AnsConfig.DEBUG_MODE.get()) {
                LOGGER.error("Error in overlay pre-render handler", e);
            }
        }
    }
}
