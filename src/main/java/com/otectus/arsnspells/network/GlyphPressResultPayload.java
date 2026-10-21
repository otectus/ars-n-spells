package com.otectus.arsnspells.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The outcome of one Glyph Press action, echoing its request so a reopened menu ignores it. */
public record GlyphPressResultPayload(String outcomeKey, LoomRequestContext context) implements CustomPacketPayload {
    public static final Type<GlyphPressResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ars_n_spells", "glyph_press_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GlyphPressResultPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, value) -> { buffer.writeUtf(value.outcomeKey, 96); value.context.write(buffer); },
        buffer -> new GlyphPressResultPayload(buffer.readUtf(96), LoomRequestContext.read(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(GlyphPressResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.otectus.arsnspells.client.screen.GlyphPressScreen.acceptResult(payload.outcomeKey, payload.context));
    }
}
