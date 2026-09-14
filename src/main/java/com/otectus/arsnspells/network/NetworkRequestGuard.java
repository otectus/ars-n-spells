package com.otectus.arsnspells.network;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.RequestAdmission;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns per-connected-player admission state exclusively on the server thread. */
@Mod.EventBusSubscriber(modid = ArsNSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NetworkRequestGuard {
    private static final Map<UUID, RequestAdmission> PLAYERS = new HashMap<>();

    private NetworkRequestGuard() {}

    public static RequestAdmission.Result admit(ServerPlayer player, UUID request) {
        return PLAYERS.computeIfAbsent(player.getUUID(), ignored -> new RequestAdmission())
            .admit(request, System.nanoTime(), AnsConfig.NETWORK_REQUEST_RATE_PER_SECOND.get(),
                AnsConfig.NETWORK_REQUEST_BURST.get());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide()) PLAYERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) { PLAYERS.clear(); }
}
