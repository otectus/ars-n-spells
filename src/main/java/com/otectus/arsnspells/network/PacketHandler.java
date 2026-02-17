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
        INSTANCE.registerMessage(id++, ProgressionSyncPacket.class, ProgressionSyncPacket::toBytes, ProgressionSyncPacket::new, ProgressionSyncPacket::handle);
    }

    public static void sendToClient(Object msg, ServerPlayer player) {
        INSTANCE.sendTo(msg, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
