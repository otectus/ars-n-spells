package com.otectus.arsnspells.client;

import com.otectus.arsnspells.ArsNSpells;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * STUB — NeoForge 1.21.1 replaced Forge's {@code RenderGuiOverlayEvent.Pre}
 * cancellation pattern with the {@code RegisterGuiLayersEvent} layer system.
 * The 1.20.1 implementation cancelled the AN / Iron's mana bar overlay by ID.
 * The 1.21.1 replacement must register a wrapping layer at higher priority
 * than AN / Iron's and conditionally short-circuit it.
 *
 * Deferred to Phase 11 (API drift / GUI). For now, the mana bars from AN and
 * Iron's render unconditionally; the {@code HIDE_MANA_BAR_WITH_RING} config
 * is read but has no visible effect until the layer hook is wired.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ManaBarController {
    private ManaBarController() {}
    // TODO(api-drift / GUI): port to RegisterGuiLayersEvent + LayeredDraw.Layer
    //   wrapping for AN's "ars_nouveau:mana_bar" and Iron's
    //   "irons_spellbooks:mana_bar" gui layers. Honor HIDE_MANA_BAR_WITH_RING,
    //   ARS_PRIMARY/ISS_PRIMARY mode hiding, and HYBRID_MANA_BAR selection.
}
