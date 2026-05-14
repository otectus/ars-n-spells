package com.otectus.arsnspells.network;

import com.otectus.arsnspells.client.ClientAuraState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client sync of the local player's aura value. Sent by the server-side aura
 * capability provider when the value changes; consumed by {@link ClientAuraState} so the
 * HUD overlay can render without polling.
 */
public class AuraSyncPacket {
    private final int aura;
    private final int maxAura;

    public AuraSyncPacket(int aura, int maxAura) {
        this.aura = aura;
        this.maxAura = maxAura;
    }

    public AuraSyncPacket(FriendlyByteBuf buf) {
        this.aura = buf.readInt();
        this.maxAura = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(aura);
        buf.writeInt(maxAura);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) {
            return;
        }
        context.enqueueWork(() -> ClientAuraState.update(aura, maxAura));
        context.setPacketHandled(true);
    }
}
