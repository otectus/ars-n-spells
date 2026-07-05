package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
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
            // Get the overlay being rendered. Use the ResourceLocation directly instead
            // of .toString() — avoids a per-overlay-per-frame String allocation and lets
            // the matchers do a namespace equals() rather than a substring scan (OPT-009).
            ResourceLocation overlayId = event.getOverlay().id();
            String overlayNamespace = overlayId.getNamespace();
            String overlayPath = overlayId.getPath();

            // Hide mana bars when the Cursed/Virtue Ring is equipped — spells consume
            // LP or Aura in that state, so showing a mana bar is misleading. This runs
            // independently of mana unification so it still applies when unification is off.
            if (isManaOverlay(overlayNamespace, overlayPath)
                && AnsConfig.HIDE_MANA_BAR_WITH_RING.get()
                && SanctifiedLegacyCompat.isAvailable()) {
                LocalPlayer localPlayer = Minecraft.getInstance().player;
                if (localPlayer != null) {
                    boolean cursed = AnsConfig.ENABLE_LP_SYSTEM.get()
                        && SanctifiedLegacyCompat.isWearingCursedRing(localPlayer);
                    boolean virtue = SanctifiedLegacyCompat.isWearingVirtueRing(localPlayer);
                    if (cursed || virtue) {
                        event.setCanceled(true);
                        return;
                    }
                }
            }

            // Only process if mana unification is enabled (master toggle + mode;
            // BridgeManager.isUnificationEnabled() is the precedence source of truth)
            if (!BridgeManager.isUnificationEnabled()) {
                return;
            }

            // Get current mana mode
            ManaUnificationMode mode = BridgeManager.getCurrentMode();
            
            // Log once for debugging
            if (!loggedOnce && AnsConfig.DEBUG_MODE.get()) {
                LOGGER.info("[ManaBarController] Mana mode: {}, Overlay: {}", mode, overlayId);
                loggedOnce = true;
            }
            
            // Determine which bar to show in hybrid mode
            boolean hybridShowIrons = mode == ManaUnificationMode.HYBRID
                && "irons".equalsIgnoreCase(AnsConfig.HYBRID_MANA_BAR.get());

            // Handle Iron's Spellbooks mana bar
            if (isIronsManaOverlay(overlayNamespace, overlayPath)) {
                if (mode == ManaUnificationMode.ARS_PRIMARY ||
                    (mode == ManaUnificationMode.HYBRID && !hybridShowIrons)) {
                    event.setCanceled(true);
                    if (AnsConfig.DEBUG_MODE.get()) {
                        LOGGER.debug("[ManaBarController] Cancelled ISS mana bar (mode: {})", mode);
                    }
                }
            }

            // Handle Ars Nouveau mana bar
            if (isArsManaOverlay(overlayNamespace, overlayPath)) {
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

    // Package-private + pure (namespace/path strings) so they're allocation-free on the
    // render path and unit-testable without a Minecraft bootstrap (OPT-009).
    static boolean isManaOverlay(String namespace, String path) {
        return path.contains("mana")
            && (namespace.equals("irons_spellbooks") || namespace.equals("ars_nouveau"));
    }

    static boolean isIronsManaOverlay(String namespace, String path) {
        return namespace.equals("irons_spellbooks") && path.contains("mana");
    }

    static boolean isArsManaOverlay(String namespace, String path) {
        return namespace.equals("ars_nouveau") && path.contains("mana");
    }
}
