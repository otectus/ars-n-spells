package com.otectus.arsnspells.network;

import com.otectus.arsnspells.config.AnsSettings;
import com.otectus.arsnspells.config.AnsSettingsService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The settings screen's request to change world settings (3.4.1 review CONFIG-01). Client to server
 * only; bounded; rate-limited per player; authorized and applied on the server thread by
 * {@link AnsSettingsService}, which answers with a {@link SettingsSyncPacket} carrying the outcome.
 */
public record SettingsApplyPacket(UUID request, Map<String, String> values) {
    public SettingsApplyPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), AnsSettings.readRequest(buffer));
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUUID(request);
        AnsSettings.writeRequest(buffer, values);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context network = supplier.get();
        if (network == null) return;
        network.enqueueWork(() -> {
            ServerPlayer sender = network.getSender();
            if (sender == null) return;
            if (NetworkRequestGuard.admit(sender, request) != com.otectus.arsnspells.contract.RequestAdmission.Result.ACCEPTED) return;
            AnsSettingsService.Result outcome = AnsSettingsService.apply(sender, values);
            PacketHandler.sendToClient(new SettingsSyncPacket(AnsSettingsService.snapshotFor(sender, outcome)), sender);
        });
        network.setPacketHandled(true);
    }
}
