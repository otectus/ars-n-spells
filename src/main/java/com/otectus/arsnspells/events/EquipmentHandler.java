package com.otectus.arsnspells.events;

import com.otectus.arsnspells.ArsNSpells;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * STUB — recomputed cross-mod equipment bonuses on
 * {@code LivingEquipmentChangeEvent} via {@link com.otectus.arsnspells.equipment.EquipmentIntegration}.
 * The downstream EquipmentIntegration is also stubbed pending Phase 11.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public class EquipmentHandler {
    private EquipmentHandler() {}
    // TODO(Phase 11): subscribe to LivingEquipmentChangeEvent + recompute scaling.
}
