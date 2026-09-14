package com.otectus.arsnspells.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Replies echo the exact request so stale results cannot affect a reopened menu. */
public record SpellLoomResultPayload(String reasonCode, boolean preview, LoomRequestContext context) implements CustomPacketPayload {
    public static final Type<SpellLoomResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ars_n_spells", "spell_loom_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpellLoomResultPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, value) -> { buffer.writeUtf(value.reasonCode, 48); buffer.writeBoolean(value.preview); value.context.write(buffer); },
        buffer -> new SpellLoomResultPayload(buffer.readUtf(48), buffer.readBoolean(), LoomRequestContext.read(buffer)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handleOnClient(SpellLoomResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.otectus.arsnspells.client.screen.SpellLoomScreen.acceptResult(payload.reasonCode, payload.preview, payload.context));
    }
}
