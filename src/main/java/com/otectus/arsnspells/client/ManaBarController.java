package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hides the redundant Ars Nouveau / Iron's Spellbooks mana-bar GUI layer based on the active
 * {@link ManaUnificationMode}. Replaces the Forge 1.20.1 {@code RenderGuiOverlayEvent.Pre}
 * cancellation pattern — NeoForge 1.21.1 fires {@link RenderGuiLayerEvent.Pre} once per
 * registered layer, which is cancellable and identifies the layer by its
 * {@link ResourceLocation}.
 *
 * <p>Strategy:
 * <ul>
 *   <li>ISS_PRIMARY — hide Ars's mana bar (Iron's is the source of truth).</li>
 *   <li>ARS_PRIMARY — hide Iron's mana bar.</li>
 *   <li>HYBRID — hide one based on {@code hybrid_mana_bar} config; the other shows the
 *       unified value.</li>
 *   <li>SEPARATE / DISABLED — show both (each pool is independent).</li>
 * </ul>
 *
 * <p><b>Why the matching is by namespace + substring, not by exact id.</b> This class briefly
 * compared the layer id against two hardcoded constants, {@code ars_nouveau:mana_bar} and
 * {@code irons_spellbooks:mana_bar}. <em>Neither mod uses either id</em>, so nothing was ever
 * cancelled and both bars rendered in every mode. The ids the mods actually register are
 * {@code ars_nouveau:mana_hud} and {@code irons_spellbooks:mana_overlay} — and Iron's renamed
 * its bar from {@code player_mana_bar} between 1.20.1 and 1.21.1. An exact-id list is a
 * standing bet that neither upstream ever renames a layer; matching the namespace exactly and
 * the path loosely is the bet that already paid off once. The predicates are pure and
 * package-private so they carry unit tests without a Minecraft bootstrap.
 *
 * <p>Hiding the losing bar is a correctness requirement, not a tidiness one:
 * {@code MixinManaCapability} returns early on the client side, so Ars's {@code ManaCap}
 * reports its <em>native</em> value there and cannot show the unified pool. Left visible in
 * ISS_PRIMARY it does not merely duplicate Iron's bar, it contradicts it.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ManaBarController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManaBarController.class);

    private static final String ARS_NAMESPACE = "ars_nouveau";
    private static final String IRONS_NAMESPACE = "irons_spellbooks";

    /** One diagnostic line per session; this runs once per layer per frame. */
    private static boolean loggedOnce = false;

    private ManaBarController() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        try {
            // Read namespace/path off the ResourceLocation directly rather than building a
            // "namespace:path" String: this runs for every layer, every frame, so the
            // matchers must not allocate.
            ResourceLocation layerId = event.getName();
            String namespace = layerId.getNamespace();
            String path = layerId.getPath();

            if (!isManaOverlay(namespace, path)) {
                return;
            }

            // Everything below reads the SERVER config. Reaching it at all is new — while the
            // matchers were broken this method returned above on every layer — which is why
            // the try/catch around this body matters now: the config is not loaded during
            // world transitions, and an unguarded throw here is one per layer per frame.
            if (!BridgeManager.isUnificationEnabled()) {
                return;
            }
            ManaUnificationMode mode = BridgeManager.getCurrentMode();
            if (mode == null) {
                return;
            }

            if (!loggedOnce && AnsConfig.DEBUG_MODE.get()) {
                LOGGER.info("[ManaBarController] Mana mode: {}, mana layer: {}", mode, layerId);
                loggedOnce = true;
            }

            boolean isArs = ARS_NAMESPACE.equals(namespace);
            if (shouldHide(mode, isArs)) {
                event.setCanceled(true);
            }
        } catch (Exception e) {
            // Never let a HUD listener take the render loop down. Logged only under debug so a
            // config-not-loaded window during a world transition cannot spam the log.
            try {
                if (AnsConfig.DEBUG_MODE.get()) {
                    LOGGER.error("[ManaBarController] Error in layer handler", e);
                }
            } catch (Exception ignored) {
                // The config read is itself the most likely thing to have thrown.
            }
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

    // Package-private + pure (namespace/path strings) so they are allocation-free on the
    // render path and unit-testable without a Minecraft bootstrap.

    /** True when the layer is either mod's mana bar. */
    static boolean isManaOverlay(String namespace, String path) {
        return path.contains("mana")
            && (IRONS_NAMESPACE.equals(namespace) || ARS_NAMESPACE.equals(namespace));
    }

    /** True when the layer is Iron's Spellbooks' mana bar. */
    static boolean isIronsManaOverlay(String namespace, String path) {
        return IRONS_NAMESPACE.equals(namespace) && path.contains("mana");
    }

    /** True when the layer is Ars Nouveau's mana bar. */
    static boolean isArsManaOverlay(String namespace, String path) {
        return ARS_NAMESPACE.equals(namespace) && path.contains("mana");
    }
}
