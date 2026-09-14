package com.otectus.arsnspells.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Replies echo the exact request; delayed results cannot update another menu or revision. */
public record SpellLoomResultPacket(String reasonCode, boolean preview, LoomRequestContext context) {
    public SpellLoomResultPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(48), buffer.readBoolean(), LoomRequestContext.read(buffer));
    }
    public void toBytes(FriendlyByteBuf buffer) { buffer.writeUtf(reasonCode, 48); buffer.writeBoolean(preview); context.write(buffer); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var network = supplier.get(); if (network == null) return;
        network.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.otectus.arsnspells.client.screen.SpellLoomScreen.acceptResult(reasonCode, preview, context)));
        network.setPacketHandled(true);
    }
}
