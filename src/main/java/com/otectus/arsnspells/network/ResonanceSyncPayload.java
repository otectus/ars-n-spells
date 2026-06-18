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
