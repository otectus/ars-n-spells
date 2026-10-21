package com.otectus.arsnspells.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client intent for one open Glyph Press menu: start or cancel, bound to the inventory revision the client saw. */
public record GlyphPressActionPacket(int action, LoomRequestContext context) {
    public GlyphPressActionPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), LoomRequestContext.read(buffer));
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(action);
        context.write(buffer);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context network = supplier.get();
        if (network == null) return;
        network.enqueueWork(() -> {
            ServerPlayer sender = network.getSender();
            String reason = GlyphPressRequestHandler.execute(sender, context, action);
            if (reason == null) return;
            if (GlyphPressRequestHandler.STALE.equals(reason)) sender.containerMenu.broadcastChanges();
            PacketHandler.sendToClient(new GlyphPressResultPacket(reason, context), sender);
        });
        network.setPacketHandled(true);
    }
}
