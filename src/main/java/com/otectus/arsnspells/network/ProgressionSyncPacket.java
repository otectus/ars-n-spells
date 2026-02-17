package com.otectus.arsnspells.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ProgressionSyncPacket {
    private final int level;

    public ProgressionSyncPacket(int level) {
        this.level = level;
    }

    public ProgressionSyncPacket(FriendlyByteBuf buf) {
        this.level = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(level);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) {
            return;
        }
        context.enqueueWork(() -> {
            // Sync progression to client
        });
        context.setPacketHandled(true);
    }
}
