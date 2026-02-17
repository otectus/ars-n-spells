package com.otectus.arsnspells.events;

import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.ResonanceSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

public class ResonanceEvents {
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!AnsConfig.ENABLE_RESONANCE_SYSTEM.get() || !ModList.get().isLoaded("irons_spellbooks")) {
            return;
        }
        if (event.phase == TickEvent.Phase.END
            && !event.player.level().isClientSide()
            && event.player.tickCount % 40 == 0
            && event.player instanceof ServerPlayer player) {
            ResonanceManager.computeResonance(player);
            PacketHandler.sendToClient(new ResonanceSyncPacket((float) ResonanceManager.getResonance(player)), player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!AnsConfig.ENABLE_RESONANCE_SYSTEM.get() || !ModList.get().isLoaded("irons_spellbooks")) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            // Logic: Immediate sync on login ensures no 'Zero-State' HUD artifacts
            ResonanceManager.computeResonance(player);
            PacketHandler.sendToClient(new ResonanceSyncPacket((float) ResonanceManager.getResonance(player)), player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ResonanceManager.clear(event.getEntity());
    }
}
