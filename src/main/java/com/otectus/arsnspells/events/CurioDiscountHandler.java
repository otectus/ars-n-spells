package com.otectus.arsnspells.events;

/**
 * STUB — Ring of Virtue / Blasphemy mana cost discount via AN's
 * {@code SpellCostCalcEvent}. The Sanctified Legacy path is gone in the
 * NeoForge 1.21.1 port (no upstream 1.21.1 build); Phase 6 will replace
 * it with a Curios-direct integration on top of AN 5.x's event surface.
 *
 * Phase 6: restore {@code @EventBusSubscriber(modid = ArsNSpells.MODID)}
 * once @SubscribeEvent methods land. NeoForge 1.21.1 rejects EventBus
 * registration when a class has no @SubscribeEvent methods.
 */
public class CurioDiscountHandler {
    private CurioDiscountHandler() {}
    // TODO(Phase 6): SpellCostCalcEvent + Curios slot scan
}
