package com.otectus.arsnspells.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bounded intent bound to one menu session and all three native inventory slots. */
public record SpellLoomExportPayload(String name, String nature, String iconSymbol, int action, LoomRequestContext context)
    implements CustomPacketPayload {
    public static final int ACTION_PREVIEW = 0, ACTION_INSCRIBE = 1, ACTION_CONVERT = 2;
    public SpellLoomExportPayload {
        name = name == null ? "" : name; nature = nature == null ? "" : nature; iconSymbol = iconSymbol == null ? "" : iconSymbol;
        if (action != ACTION_INSCRIBE && action != ACTION_CONVERT) action = ACTION_PREVIEW;
    }
    public static final Type<SpellLoomExportPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ars_n_spells", "spell_loom_export"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpellLoomExportPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, value) -> {
            buffer.writeUtf(value.name, 40); buffer.writeUtf(value.nature, 64); buffer.writeUtf(value.iconSymbol, 64);
            buffer.writeVarInt(value.action); value.context.write(buffer);
        }, buffer -> new SpellLoomExportPayload(buffer.readUtf(40), buffer.readUtf(64), buffer.readUtf(64), buffer.readVarInt(), LoomRequestContext.read(buffer)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handleOnServer(SpellLoomExportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;
            String reason = LoomRequestHandler.execute(sender, payload.context, payload.action, payload.name, payload.nature, payload.iconSymbol);
            if (reason == null) return;
            if (LoomRequestHandler.STALE.equals(reason)) sender.containerMenu.broadcastChanges();
            PacketHandler.sendToClient(new SpellLoomResultPayload(reason, payload.action == ACTION_PREVIEW, payload.context), sender);
        });
    }
}
