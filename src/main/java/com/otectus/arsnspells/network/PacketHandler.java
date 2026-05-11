package com.otectus.arsnspells.network;

import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge 1.21.1 payload handler — replaces the Forge SimpleChannel rig
 * from 1.20.1. Registration runs on the mod bus
 * ({@link RegisterPayloadHandlersEvent}); broadcast helpers wrap
 * {@link PacketDistributor}.
 */
public final class PacketHandler {
    /** Bumped on protocol-breaking changes. "1" for the first NeoForge line. */
    public static final String PROTOCOL_VERSION = "1";

    private PacketHandler() {}

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar(PROTOCOL_VERSION);

        reg.playToClient(AffinitySyncPayload.TYPE, AffinitySyncPayload.STREAM_CODEC,
            AffinitySyncPayload::handleOnClient);
        reg.playToClient(CooldownSyncPayload.TYPE, CooldownSyncPayload.STREAM_CODEC,
            CooldownSyncPayload::handleOnClient);

        if (ModList.get().isLoaded("irons_spellbooks")) {
            reg.playToClient(ResonanceSyncPayload.TYPE, ResonanceSyncPayload.STREAM_CODEC,
                ResonanceSyncPayload::handleOnClient);
        }

        ArsNSpells.LOGGER.info("Registered payload handlers (protocol={})", PROTOCOL_VERSION);
    }

    public static <T extends CustomPacketPayload> void sendToClient(T payload, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
