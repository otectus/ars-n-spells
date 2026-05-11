package com.otectus.arsnspells.client;

/**
 * STUB — NeoForge 1.21.1 replaced Forge's {@code RenderGuiOverlayEvent.Pre}
 * cancellation pattern with the {@code RegisterGuiLayersEvent} layer system.
 * The 1.20.1 implementation cancelled the AN / Iron's mana bar overlay by ID.
 * The 1.21.1 replacement must register a wrapping layer at higher priority
 * than AN / Iron's and conditionally short-circuit it.
 *
 * Deferred to Phase 3 (GUI). For now, the mana bars from AN and Iron's render
 * unconditionally; mode-based hiding has no visible effect until the layer
 * hook is wired.
 *
 * Phase 3: restore {@code @EventBusSubscriber(modid = ArsNSpells.MODID,
 * bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)} (note: MOD bus,
 * not GAME bus — {@code RegisterGuiLayersEvent} is a mod-bus event)
 * once @SubscribeEvent methods land. NeoForge 1.21.1 rejects EventBus
 * registration when a class has no @SubscribeEvent methods.
 */
public class ManaBarController {
    private ManaBarController() {}
    // TODO(Phase 3): port to RegisterGuiLayersEvent + LayeredDraw.Layer
    //   wrapping for AN's "ars_nouveau:mana_bar" and Iron's
    //   "irons_spellbooks:mana_bar" gui layers. Honor mode-based hiding
    //   (ARS_PRIMARY/ISS_PRIMARY) and HYBRID_MANA_BAR selection.
}
