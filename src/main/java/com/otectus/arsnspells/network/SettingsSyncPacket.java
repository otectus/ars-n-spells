package com.otectus.arsnspells.network;

import com.otectus.arsnspells.config.AnsSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The world settings in force, sent to a client at login, after every change and after a config
 * reload (3.4.1 review CONFIG-01). Forge synchronizes SERVER configs only when a player joins, so
 * without this a remote client kept the mode it joined with: its mana bars, and its settings screen,
 * showed a mode the server no longer used. Server to client only; bounded.
 */
public record SettingsSyncPacket(AnsSettings.Snapshot snapshot) {
    public SettingsSyncPacket(FriendlyByteBuf buffer) {
        this(AnsSettings.Snapshot.read(buffer));
    }

    public void toBytes(FriendlyByteBuf buffer) {
        snapshot.write(buffer);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context network = supplier.get();
        if (network == null) return;
        network.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.otectus.arsnspells.client.ClientSettings.accept(snapshot)));
        network.setPacketHandled(true);
    }
}
