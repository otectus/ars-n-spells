package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Hides the redundant Ars Nouveau / Iron's Spellbooks mana-bar GUI layer
 * based on the active {@link ManaUnificationMode}. Replaces the Forge
 * 1.20.1 {@code RenderGuiOverlayEvent.Pre} cancellation pattern — NeoForge
 * 1.21.1 fires {@link RenderGuiLayerEvent.Pre} once per registered layer,
 * which is cancellable and identifies the layer by its
 * {@link ResourceLocation}.
 *
 * <p>Strategy:
 * <ul>
 *   <li>ISS_PRIMARY — hide Ars's mana bar (Iron's is the source of truth).</li>
 *   <li>ARS_PRIMARY — hide Iron's mana bar.</li>
 *   <li>HYBRID — hide one based on {@code hybrid_mana_bar} config; the
 *       other shows the unified value.</li>
 *   <li>SEPARATE / DISABLED — show both (each pool is independent).</li>
 * </ul>
 */
@EventBusSubscriber(modid = ArsNSpells.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ManaBarController {
    private static final ResourceLocation ARS_MANA_BAR =
        ResourceLocation.fromNamespaceAndPath("ars_nouveau", "mana_bar");
    private static final ResourceLocation IRONS_MANA_BAR =
        ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "mana_bar");

    private ManaBarController() {}

    @SubscribeEvent
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        ResourceLocation name = event.getName();
        if (!ARS_MANA_BAR.equals(name) && !IRONS_MANA_BAR.equals(name)) {
            return;
        }
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode == null) {
            return;
        }
        boolean isArs = ARS_MANA_BAR.equals(name);
        if (shouldHide(mode, isArs)) {
            event.setCanceled(true);
        }
    }

    private static boolean shouldHide(ManaUnificationMode mode, boolean isArs) {
        switch (mode) {
            case ISS_PRIMARY:
                return isArs;
            case ARS_PRIMARY:
                return !isArs;
            case HYBRID:
                String preferred = AnsConfig.HYBRID_MANA_BAR.get();
                boolean preferArs = "ars".equalsIgnoreCase(preferred);
                return isArs ? !preferArs : preferArs;
            case SEPARATE:
            case DISABLED:
            default:
                return false;
        }
    }
}
