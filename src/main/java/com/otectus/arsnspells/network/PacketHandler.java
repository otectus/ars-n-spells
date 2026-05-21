package com.otectus.arsnspells.network;

import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.ModList;

public class PacketHandler {
    // PROTOCOL_VERSION bumped 1 -> 2 in the aura-subsystem deletion: AuraSyncPacket
    // was removed, which shifts all subsequent packet IDs down by one slot. A client
    // running the old jar would mis-parse our packets — hard-fail at connect instead.
    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ArsNSpells.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        // ANS-HIGH-013: all S2C packets now register with PLAY_TO_CLIENT so the bus
        // rejects mis-directed client→server payloads at the channel layer (no enqueueWork,
        // no static-field writes on the server side). Previously these four packets had
        // no direction guard, giving a hostile client a free DoS amplification path.
        int id = 0;
        if (ModList.get().isLoaded("irons_spellbooks")) {
            INSTANCE.registerMessage(id++, ResonanceSyncPacket.class,
                ResonanceSyncPacket::toBytes, ResonanceSyncPacket::new, ResonanceSyncPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        }
        INSTANCE.registerMessage(id++, AffinitySyncPacket.class,
            AffinitySyncPacket::toBytes, AffinitySyncPacket::new, AffinitySyncPacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++, CooldownSyncPacket.class,
            CooldownSyncPacket::toBytes, CooldownSyncPacket::new, CooldownSyncPacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // AuraSyncPacket removed in the aura-subsystem deletion: Covenant of the Seven
        // owns the aura state and renders its own HUD, so we no longer need to sync.
        // CrossCastRequestPacket (C2S) shifted down one slot; PROTOCOL_VERSION was bumped
        // to force a hard-fail at connect for clients on the old jar.
        INSTANCE.registerMessage(id++, CrossCastRequestPacket.class,
            CrossCastRequestPacket::toBytes, CrossCastRequestPacket::new, CrossCastRequestPacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** ANS-LOW-015: defensive null-checks for early-login / mid-disconnect edge cases. */
    public static void sendToClient(Object msg, ServerPlayer player) {
        if (player == null || player.connection == null) return;
        INSTANCE.sendTo(msg, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }
}
