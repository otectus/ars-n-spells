package com.otectus.arsnspells.network;

import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge 1.21.1 payload handler — replaces the Forge SimpleChannel rig
 * from 1.20.1. Registration runs on the mod bus
 * ({@link RegisterPayloadHandlersEvent}); broadcast helpers wrap
 * {@link PacketDistributor}.
 *
 * <p>All payloads register unconditionally: registering a payload on only one
 * side (e.g. Iron's-gated) makes channel negotiation fail whenever server and
 * client disagree on Iron's presence. The handlers themselves no-op safely
 * when the relevant integration is absent.
 */
public final class PacketHandler {
    /** Bumped on protocol-breaking changes. "2" for the 3.0.x line (loom payload added). */
    public static final String PROTOCOL_VERSION = "2";

    private PacketHandler() {}

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar(PROTOCOL_VERSION);

        reg.playToClient(AffinitySyncPayload.TYPE, AffinitySyncPayload.STREAM_CODEC,
            AffinitySyncPayload::handleOnClient);
        reg.playToClient(CooldownSyncPayload.TYPE, CooldownSyncPayload.STREAM_CODEC,
            CooldownSyncPayload::handleOnClient);
        reg.playToClient(ResonanceSyncPayload.TYPE, ResonanceSyncPayload.STREAM_CODEC,
            ResonanceSyncPayload::handleOnClient);
        reg.playToServer(SpellLoomExportPayload.TYPE, SpellLoomExportPayload.STREAM_CODEC,
            SpellLoomExportPayload::handleOnServer);

        ArsNSpells.LOGGER.info("Registered payload handlers (protocol={})", PROTOCOL_VERSION);
    }

    public static <T extends CustomPacketPayload> void sendToClient(T payload, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static <T extends CustomPacketPayload> void sendToServer(T payload) {
        PacketDistributor.sendToServer(payload);
    }
}
