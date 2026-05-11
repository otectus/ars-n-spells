package com.otectus.arsnspells.events;

import com.otectus.arsnspells.augmentation.ResonanceManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Ticks the resonance computation per player at 1Hz. The math itself
 * ({@link ResonanceManager#computeResonance}) reads from Iron's
 * {@code MagicData}, which is alive in 1.21.1-3.15.6, so resonance from
 * Iron's mana works today. AN-side resonance still needs Phase 11 work
 * once the Ars mana bridge is restored.
 */
public class ResonanceEvents {

    @SubscribeEvent
    public void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (event.getEntity() == null || event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity().tickCount % 20 != 0) {
            return;
        }
        ResonanceManager.computeResonance(event.getEntity());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ResonanceManager.clearAll();
    }
}
