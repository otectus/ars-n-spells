package com.otectus.arsnspells.events;

import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.ResonanceSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Computes resonance per player at 1 Hz and pushes it to the client so the HUD
 * mirror stays current. {@link ResonanceManager#computeResonance} reads Iron's
 * {@code MagicData}; this handler is registered only when Iron's is loaded.
 *
 * <p>The server value is authoritative for spell scaling
 * ({@code MixinIronsSpellDamage} reads it directly server-side); the
 * {@link ResonanceSyncPayload} sent here only keeps the <em>client</em> copy in
 * sync — on login and on every recompute. Respawn / dimension sync is owned by
 * {@link CapabilityResyncHandler}.
 */
public class ResonanceEvents {

    @SubscribeEvent
    public void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        ResonanceManager.computeResonance(player);
        sync(player);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResonanceManager.computeResonance(player);
            sync(player);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ResonanceManager.clearAll();
    }

    private static void sync(ServerPlayer player) {
        PacketHandler.sendToClient(
            new ResonanceSyncPayload((float) ResonanceManager.getResonance(player)), player);
    }
}
