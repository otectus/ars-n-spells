package com.otectus.arsnspells.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A Glyph Press button press: which action, bound to one menu session and the inventory it saw. */
public record GlyphPressActionPayload(int action, LoomRequestContext context) implements CustomPacketPayload {
    public static final int ACTION_INSCRIBE = 0, ACTION_CANCEL = 1;
    public static final Type<GlyphPressActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ars_n_spells", "glyph_press_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GlyphPressActionPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, value) -> { buffer.writeVarInt(value.action); value.context.write(buffer); },
        buffer -> new GlyphPressActionPayload(buffer.readVarInt(), LoomRequestContext.read(buffer)));

    public GlyphPressActionPayload {
        if (action != ACTION_CANCEL) action = ACTION_INSCRIBE;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(GlyphPressActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;
            String outcome = GlyphPressRequestHandler.execute(sender, payload.context, payload.action);
            if (outcome == null) return;
            if (GlyphPressRequestHandler.STALE.equals(outcome)) sender.containerMenu.broadcastChanges();
            PacketHandler.sendToClient(new GlyphPressResultPayload(outcome, payload.context), sender);
        });
    }
}
