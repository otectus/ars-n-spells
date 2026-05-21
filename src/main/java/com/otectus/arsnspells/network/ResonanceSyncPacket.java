package com.otectus.arsnspells.network;

import com.otectus.arsnspells.augmentation.ResonanceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class ResonanceSyncPacket {
    /** ANS-HIGH-006: matches AnsConfig.MAX_DAMAGE_MULTIPLIER ceiling — a hostile or
     *  garbled payload can never multiply spell damage past the configured cap. */
    private static final float MAX_RESONANCE = 100.0f;

    private final float resonance;

    public ResonanceSyncPacket(float resonance) {
        this.resonance = resonance;
    }

    public ResonanceSyncPacket(FriendlyByteBuf buf) {
        // ANS-HIGH-006: reject NaN/+-Infinity at the packet boundary so they cannot
        // propagate through ResonanceManager into spell-damage multipliers. NaN through
        // Math.min(NaN, cap) returns NaN, bypassing the SpellScalingUtil cap entirely.
        float raw = buf.readFloat();
        this.resonance = (Float.isFinite(raw) && raw >= 0.0f) ? Math.min(MAX_RESONANCE, raw) : 1.0f;
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
