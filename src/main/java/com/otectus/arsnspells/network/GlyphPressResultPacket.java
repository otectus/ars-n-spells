package com.otectus.arsnspells.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The server's answer to one press request; it echoes the request, so a late answer cannot update another menu. */
public record GlyphPressResultPacket(String reasonCode, LoomRequestContext context) {
    public GlyphPressResultPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(48), LoomRequestContext.read(buffer));
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(reasonCode, 48);
        context.write(buffer);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var network = supplier.get();
        if (network == null) return;
        network.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.otectus.arsnspells.client.screen.GlyphPressScreen.acceptResult(reasonCode, context)));
        network.setPacketHandled(true);
    }
}
