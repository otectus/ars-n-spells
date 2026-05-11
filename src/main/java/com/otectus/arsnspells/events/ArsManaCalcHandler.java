package com.otectus.arsnspells.events;

/**
 * STUB — listened on AN's {@code ManaRegenCalcEvent} and
 * {@code MaxManaCalcEvent} to feed cross-system bonuses into AN's mana
 * computation. Both event field surfaces drifted with AN 5.x; pending
 * Phase 3.
 *
 * Phase 3: restore {@code @EventBusSubscriber(modid = ArsNSpells.MODID)}
 * once @SubscribeEvent methods land. NeoForge 1.21.1 rejects EventBus
 * registration when a class has no @SubscribeEvent methods.
 */
public class ArsManaCalcHandler {
    private ArsManaCalcHandler() {}
    // TODO(Phase 3): ManaRegenCalcEvent + MaxManaCalcEvent re-port
}
