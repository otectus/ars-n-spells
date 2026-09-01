package com.otectus.arsnspells.network;

import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → client sync of resonance state (Iron's-dependent). The
 * client-side body is isolated in a {@link Dist#CLIENT} nested class so the
 * dedicated server's classloader never touches Iron's UI types or
 * {@code ResonanceManager.setClientResonance}.
 */
public record ResonanceSyncPayload(float resonance) implements CustomPacketPayload {

    /** Wire ceiling, mirroring ResonanceManager.MAX_RESONANCE. */
    private static final float MAX_RESONANCE = 100.0f;

    /**
     * Sanitize at construction, so a value that arrives off the wire is bounded before it can
     * reach anything. This is the first of two layers: {@code ResonanceManager} clamps again on
     * receipt (ANS-HIGH-006). NaN is the case that matters most - it is not merely out of
     * range, it defeats every {@code Math.min}-style cap downstream, including the
     * {@code spell_power_cap} that {@code SpellScalingUtil} applies.
     */
    public ResonanceSyncPayload {
        resonance = (Float.isFinite(resonance) && resonance >= 0.0f)
            ? Math.min(MAX_RESONANCE, resonance)
            : 1.0f;
    }

    public static final Type<ResonanceSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "resonance_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResonanceSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.FLOAT, ResonanceSyncPayload::resonance,
            ResonanceSyncPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnClient(ResonanceSyncPayload p, IPayloadContext ctx) {
        ClientHandler.apply(p);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        static void apply(ResonanceSyncPayload p) {
            com.otectus.arsnspells.augmentation.ResonanceManager.setClientResonance(p.resonance());
        }
    }
}
