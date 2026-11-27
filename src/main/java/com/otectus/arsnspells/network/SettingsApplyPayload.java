package com.otectus.arsnspells.network;

import com.otectus.arsnspells.config.AnsSettings;
import com.otectus.arsnspells.config.AnsSettingsService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;

/**
 * The settings screen's request to change world settings (3.4.1 review CONFIG-01). Client to server
 * only; bounded; rate-limited per player; authorized and applied on the server thread by
 * {@link AnsSettingsService}, which answers with a {@link SettingsSyncPayload} carrying the outcome.
 */
public record SettingsApplyPayload(UUID request, Map<String, String> values) implements CustomPacketPayload {
    public static final Type<SettingsApplyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ars_n_spells", "settings_apply"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SettingsApplyPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, value) -> { buffer.writeUUID(value.request); AnsSettings.writeRequest(buffer, value.values); },
        buffer -> new SettingsApplyPayload(buffer.readUUID(), AnsSettings.readRequest(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SettingsApplyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;
            if (NetworkRequestGuard.admit(sender, payload.request) != com.otectus.arsnspells.contract.RequestAdmission.Result.ACCEPTED) return;
            AnsSettingsService.Result outcome = AnsSettingsService.apply(sender, payload.values);
            PacketHandler.sendToClient(new SettingsSyncPayload(AnsSettingsService.snapshotFor(sender, outcome)), sender);
        });
    }
}
