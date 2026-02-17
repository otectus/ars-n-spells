package com.otectus.arsnspells.network;

import com.otectus.arsnspells.augmentation.ResonanceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class ResonanceSyncPacket {
    private final float resonance;

    public ResonanceSyncPacket(float resonance) {
        this.resonance = resonance;
    }

    public ResonanceSyncPacket(FriendlyByteBuf buf) {
        this.resonance = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeFloat(resonance);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) {
            return;
        }
        context.enqueueWork(() -> {
            // Logic: High-Fidelity client-side sync using DistExecutor to prevent server crashes
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                 ResonanceManager.setClientResonance(resonance);
            });
        });
        context.setPacketHandled(true);
    }
}
