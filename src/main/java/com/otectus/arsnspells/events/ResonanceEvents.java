package com.otectus.arsnspells.events;

import com.otectus.arsnspells.augmentation.ResonanceManager;
import com.otectus.arsnspells.config.AnsConfig;
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

    /** Recompute interval, in ticks. 40 = twice a second, matching the 1.20.1 line. */
    private static final int RECOMPUTE_INTERVAL_TICKS = 40;

    @SubscribeEvent
    public void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % RECOMPUTE_INTERVAL_TICKS != 0) {
            return;
        }
        // The feature gate belongs here as well as inside computeResonance: without it the
        // handler pays the MagicData lookup and the attribute read for a system the server
        // owner turned off.
        if (!AnsConfig.flag(AnsConfig.ENABLE_RESONANCE_SYSTEM, false)) {
            return;
        }
        // Only sync when the value actually moved. The client copy is a HUD/prediction
        // mirror; resending an unchanged multiplier every interval is a packet per player
        // per interval that changes nothing on the receiving end.
        if (ResonanceManager.computeResonance(player)) {
            sync(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Always sync on login, changed or not: the client starts at the neutral 1.0 and
            // has nothing to mirror until the first packet arrives.
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
