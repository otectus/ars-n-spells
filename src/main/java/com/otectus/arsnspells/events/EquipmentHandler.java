package com.otectus.arsnspells.events;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-mod equipment-change notification. Fires whenever a player swaps
 * any armor / off-hand / main-hand slot; downstream handlers (Curios
 * discount cache, equipment-bonus pipeline) can recompute against the new
 * loadout.
 *
 * <p>Currently a thin marker — emits debug logging so the integration is
 * visible during testing. The Forge 1.20.1 version called into
 * {@link com.otectus.arsnspells.equipment.EquipmentIntegration} to refresh
 * Ars / Iron's gear-bonus modifiers; that downstream class is itself a
 * stub on the NeoForge 1.21.1 port (Phase 3 work — re-derive against
 * Ars 5.x / Iron's 3.15.6 enchantment + attribute APIs). When it lands,
 * extend this handler to invoke the recompute.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class EquipmentHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EquipmentHandler.class);

    private EquipmentHandler() {}

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[Equipment] {} swapped slot {}: {} -> {}",
                player.getName().getString(),
                event.getSlot(),
                event.getFrom(),
                event.getTo());
        }
        // TODO(Phase 3): when EquipmentIntegration is re-derived against Ars 5.x +
        // Iron's 3.15.6 enchantment / attribute APIs, call its recompute() here.
    }
}
