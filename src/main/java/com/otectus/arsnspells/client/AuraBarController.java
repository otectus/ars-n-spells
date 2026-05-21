package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders the aura bar over the hotbar when the Virtue Ring is equipped.
 *
 * <p>The mana bar (Iron's or Ars) is hidden in this state by {@link ManaBarController},
 * so there'd otherwise be no resource UI for the player. This overlay fills that gap.
 *
 * <p>Bar uses opaque colors so it reads clearly against any background. Fill is aqua
 * to match the chat colour used in aura messages. Position is just above the hotbar,
 * roughly where Iron's / Ars mana bars sit.
 */
@Mod.EventBusSubscriber(modid = ArsNSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AuraBarController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuraBarController.class);

    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 7;
    private static final int BACKGROUND_COLOR = 0xFF1A1A1A;     // opaque dark grey
    private static final int BAR_FILL_COLOR    = 0xFF55FFFF;    // aqua
    private static final int BAR_BORDER_COLOR  = 0xFFFFFFFF;    // white
    private static final int TEXT_COLOR        = 0xFF55FFFF;    // aqua

    private static long lastDiagLogMs = 0;

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        // Anchor to the hotbar overlay so we only draw once per frame and in a sensible Z-order.
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            return;
        }

        try {
            if (!AnsConfig.ENABLE_AURA_SYSTEM.get()) {
                return;
            }
            if (!AnsConfig.SHOW_AURA_HUD.get()) {
                return;
            }
            if (!SanctifiedLegacyCompat.isAvailable()) {
                return;
            }
        } catch (Exception e) {
            // ANS-LOW-002: swallow the config-read failure but log it (throttled by
            // the diagnostic timer below) so unexpected NPEs during early world load
            // don't disappear silently.
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastDiagLogMs > 5000) {
                lastDiagLogMs = nowMs;
                org.slf4j.LoggerFactory.getLogger(AuraBarController.class)
                    .warn("AuraBarController config read failed: {}", e.getMessage());
            }
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (mc.options.hideGui) {
            return;
        }
        if (!SanctifiedLegacyCompat.isWearingVirtueRing(player)) {
            return;
        }
        if (!ClientAuraState.isInitialized()) {
            return;
        }

        int aura = ClientAuraState.getAura();
        int maxAura = ClientAuraState.getMaxAura();
        if (maxAura <= 0) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int screenW = event.getWindow().getGuiScaledWidth();
        int screenH = event.getWindow().getGuiScaledHeight();

        int barX = (screenW - BAR_WIDTH) / 2;
        // Position above the hotbar (~50 px from the bottom — clear of XP bar at -32 and hotbar at -22).
        int barY = screenH - 50;

        // 1px white border, then opaque dark background, then fill on top.
        graphics.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, BAR_BORDER_COLOR);
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, BACKGROUND_COLOR);

        // Fill: width proportional to aura/maxAura, clamped 0..BAR_WIDTH.
        long filledLong = Math.round((double) aura / (double) maxAura * (double) BAR_WIDTH);
        int filled = (int) Math.max(0, Math.min(BAR_WIDTH, filledLong));
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + BAR_HEIGHT, BAR_FILL_COLOR);
        }

        // Label centred above the bar.
        Font font = mc.font;
        String label = aura + " / " + maxAura;
        int textW = font.width(label);
        int textX = (screenW - textW) / 2;
        int textY = barY - 10;
        graphics.drawString(font, Component.literal(label), textX, textY, TEXT_COLOR, true);

        // Once-per-second diagnostic log so the user can verify what the client thinks
        // the value is, if they enable DEBUG_MODE in config.
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastDiagLogMs > 1000) {
            lastDiagLogMs = nowMs;
            try {
                if (AnsConfig.DEBUG_MODE.get()) {
                    LOGGER.info("[AuraBar] aura={}, maxAura={}, filled={}/{}, barX={}, barY={}, screen={}x{}",
                        aura, maxAura, filled, BAR_WIDTH, barX, barY, screenW, screenH);
                }
            } catch (Exception ignored) {}
        }
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAuraState.reset();
    }
}
