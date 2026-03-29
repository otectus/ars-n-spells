package com.otectus.arsnspells.network;

import com.otectus.arsnspells.data.ProgressionData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Syncs full progression state (all school cast counts) from server to client.
 */
public class ProgressionSyncPacket {
    private final CompoundTag data;

    public ProgressionSyncPacket(ProgressionData progressionData) {
        this.data = new CompoundTag();
        progressionData.saveToNBT(this.data);
    }

    public ProgressionSyncPacket(FriendlyByteBuf buf) {
        this.data = buf.readNbt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;
            mc.player.getCapability(ProgressionData.PROGRESSION_DATA).ifPresent(clientData -> {
                clientData.loadFromNBT(data);
            });
        });
        context.setPacketHandled(true);
    }
}
