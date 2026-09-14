package com.otectus.arsnspells.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Bounded cosmetic intent bound to one open menu, inventory revision and replay-protected request. */
public record SpellLoomExportPacket(String name, String nature, String iconSymbol, int action, LoomRequestContext context) {
    public static final int ACTION_PREVIEW = 0, ACTION_INSCRIBE = 1, ACTION_CONVERT = 2;
    public SpellLoomExportPacket {
        name = name == null ? "" : name; nature = nature == null ? "" : nature; iconSymbol = iconSymbol == null ? "" : iconSymbol;
        if (action != ACTION_INSCRIBE && action != ACTION_CONVERT) action = ACTION_PREVIEW;
    }
    public SpellLoomExportPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(40), buffer.readUtf(64), buffer.readUtf(64), buffer.readVarInt(), LoomRequestContext.read(buffer));
    }
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(name, 40); buffer.writeUtf(nature, 64); buffer.writeUtf(iconSymbol, 64); buffer.writeVarInt(action); context.write(buffer);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context network = supplier.get();
        if (network == null) return;
        network.enqueueWork(() -> {
            ServerPlayer sender = network.getSender();
            String reason = LoomRequestHandler.execute(sender, context, action, name, nature, iconSymbol);
            if (reason == null) return;
            if (LoomRequestHandler.STALE.equals(reason)) sender.containerMenu.broadcastChanges();
            PacketHandler.sendToClient(new SpellLoomResultPacket(reason, action == ACTION_PREVIEW, context), sender);
        });
        network.setPacketHandled(true);
    }
}
