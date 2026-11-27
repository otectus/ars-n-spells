package com.otectus.arsnspells.network;

import com.otectus.arsnspells.config.AnsSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The world settings in force, sent to a client at login, after every change and after a config
 * reload (3.4.1 review CONFIG-01). NeoForge synchronizes SERVER configs only when a player joins, so
 * without this a remote client kept the mode it joined with. Server to client only; bounded.
 */
public record SettingsSyncPayload(AnsSettings.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<SettingsSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ars_n_spells", "settings_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SettingsSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, value) -> value.snapshot.write(buffer),
        buffer -> new SettingsSyncPayload(AnsSettings.Snapshot.read(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(SettingsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.otectus.arsnspells.client.ClientSettings.accept(payload.snapshot));
    }
}
