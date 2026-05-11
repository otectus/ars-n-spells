package com.otectus.arsnspells.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import com.otectus.arsnspells.affinity.AffinityType;

public class AffinitySyncPacket {
    private final String typeName;
    private final int level;

    public AffinitySyncPacket(AffinityType type, int level) {
        this.typeName = type.name();
        this.level = level;
    }

    public AffinitySyncPacket(FriendlyByteBuf buf) {
        this.typeName = buf.readUtf();
        this.level = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(typeName);
        buf.writeInt(level);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) {
            return;
        }
        context.enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.otectus.arsnspells.client.ClientAffinityPacketHandler.apply(typeName, level)));
        context.setPacketHandled(true);
    }
}
