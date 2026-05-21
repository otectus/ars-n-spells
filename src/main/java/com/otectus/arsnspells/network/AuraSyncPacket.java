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
    /** ANS-HIGH-005: matches AnsConfig.AURA_MAX_DEFAULT ceiling (100000) so payload bounds
     *  cap the HUD's worst-case render at the same value the server config accepts. */
    private static final int MAX_REASONABLE_AURA = 100_000;

    private final int aura;
    private final int maxAura;

    public AuraSyncPacket(int aura, int maxAura) {
        this.aura = aura;
        this.maxAura = maxAura;
    }

    public AuraSyncPacket(FriendlyByteBuf buf) {
        // ANS-HIGH-005: clamp both ints at decode so a hostile or version-skewed server
        // cannot feed Integer.MAX_VALUE into the HUD overlay. maxAura is bounded to a
        // reasonable ceiling; aura is then bounded to [0, maxAura] so the render path
        // never sees aura > maxAura (the render math would otherwise compute width > BAR_WIDTH).
        int rawAura = buf.readInt();
        int rawMax = buf.readInt();
        this.maxAura = Math.max(1, Math.min(MAX_REASONABLE_AURA, rawMax));
        this.aura = Math.max(0, Math.min(this.maxAura, rawAura));
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
