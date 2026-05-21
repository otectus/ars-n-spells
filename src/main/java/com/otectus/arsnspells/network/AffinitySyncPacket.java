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
        // ANS-MED-016: bounded readUtf so a hostile payload cannot allocate a 32k
        // string per packet (default cap is Short.MAX_VALUE). AffinityType names
        // are at most ~16 chars in any sane build.
        this.typeName = buf.readUtf(64);
        int raw = buf.readInt();
        // Clamp level to the AffinityData range so a stray int does not corrupt
        // the client mirror.
        this.level = Math.max(0, Math.min(100, raw));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(typeName, 64);
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
