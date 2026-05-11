package com.otectus.arsnspells.events;

/**
 * STUB — recomputed cross-mod equipment bonuses on
 * {@code LivingEquipmentChangeEvent} via {@link com.otectus.arsnspells.equipment.EquipmentIntegration}.
 * The downstream EquipmentIntegration is also stubbed pending Phase 3.
 *
 * Phase 3: restore {@code @EventBusSubscriber(modid = ArsNSpells.MODID)}
 * once @SubscribeEvent methods land. NeoForge 1.21.1 rejects EventBus
 * registration when a class has no @SubscribeEvent methods.
 */
public class EquipmentHandler {
    private EquipmentHandler() {}
    // TODO(Phase 3): subscribe to LivingEquipmentChangeEvent + recompute scaling.
}
