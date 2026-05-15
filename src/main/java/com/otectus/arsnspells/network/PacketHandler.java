package com.otectus.arsnspells.network;

import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.ModList;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ArsNSpells.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        if (ModList.get().isLoaded("irons_spellbooks")) {
            INSTANCE.registerMessage(id++, ResonanceSyncPacket.class, ResonanceSyncPacket::toBytes, ResonanceSyncPacket::new, ResonanceSyncPacket::handle);
        }
        INSTANCE.registerMessage(id++, AffinitySyncPacket.class, AffinitySyncPacket::toBytes, AffinitySyncPacket::new, AffinitySyncPacket::handle);
        INSTANCE.registerMessage(id++, CooldownSyncPacket.class, CooldownSyncPacket::toBytes, CooldownSyncPacket::new, CooldownSyncPacket::handle);
        // Aura sync is appended at the end so the IDs of pre-existing packets don't shift
        // for clients running mixed mod versions.
        INSTANCE.registerMessage(id++, AuraSyncPacket.class, AuraSyncPacket::toBytes, AuraSyncPacket::new, AuraSyncPacket::handle);
        // CrossCastRequestPacket (C2S) — appended at the tail to preserve
        // pre-existing packet IDs across the 1.10.x → 2.0.0 bump.
        INSTANCE.registerMessage(id++, CrossCastRequestPacket.class,
            CrossCastRequestPacket::toBytes, CrossCastRequestPacket::new, CrossCastRequestPacket::handle,
            java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendToClient(Object msg, ServerPlayer player) {
        INSTANCE.sendTo(msg, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }
}
