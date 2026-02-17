package com.otectus.arsnspells.network;

import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.affinity.AffinityType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

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
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) {
                return;
            }
            mc.player.getCapability(AffinityData.AFFINITY_DATA).ifPresent(data -> {
                try {
                    AffinityType type = AffinityType.valueOf(typeName);
                    // Operational Logic: Sets the client specialty level to the mirrored server value.
                    int boost = level - data.getLevel(type);
                    data.addLevel(type, boost);
                } catch (Exception ignored) {}
            });
        });
        context.setPacketHandled(true);
    }
}
