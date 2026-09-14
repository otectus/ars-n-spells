package com.otectus.arsnspells.events;

import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.ResonanceSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class ResonanceEvents {
    // ANS-MED-028: cleanup tracking moved to a server-wide ServerTickEvent handler
    // below. The per-player tick counter previously incremented N times per
    // (40-tick) window with N players, causing cleanup to fire much more often
    // than the "60 seconds" the comment claimed.
    private int serverCleanupTickCounter = 0;

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide()
            
            && event.getEntity() instanceof ServerPlayer player) {
            double before = ResonanceManager.cachedResonance(player);
            ResonanceManager.computeResonance(player);
            double after = ResonanceManager.getResonance(player);
            if (after != before) {
                PacketHandler.sendToClient(new ResonanceSyncPayload((float) after), player);
            }
        }
    }

    /**
     * ANS-MED-028: server-global cleanup, fires once per 1200 server ticks
     * (60 seconds) regardless of player count.
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!IronsCompat.isLoaded()) {
            return;
        }
        serverCleanupTickCounter++;
        if (serverCleanupTickCounter >= 1200) {
            serverCleanupTickCounter = 0;
            ResonanceManager.cleanupOfflinePlayers(event.getServer());
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Logic: Immediate sync on login ensures no 'Zero-State' HUD artifacts
            ResonanceManager.computeResonance(player);
            PacketHandler.sendToClient(new ResonanceSyncPayload((float) ResonanceManager.getResonance(player)), player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ResonanceManager.clear(event.getEntity());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ResonanceManager.clearAll();
    }
}
