package com.otectus.arsnspells.network;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.AttachmentTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → client sync of one affinity school's level. */
public record AffinitySyncPayload(String typeName, int level) implements CustomPacketPayload {

    public static final Type<AffinitySyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "affinity_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AffinitySyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AffinitySyncPayload::typeName,
            ByteBufCodecs.INT,         AffinitySyncPayload::level,
            AffinitySyncPayload::new
        );

    public AffinitySyncPayload(AffinityType type, int level) {
        this(type.name(), level);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnClient(AffinitySyncPayload p, IPayloadContext ctx) {
        ClientHandler.apply(p, ctx);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        static void apply(AffinitySyncPayload p, IPayloadContext ctx) {
            net.minecraft.world.entity.player.Player player = ctx.player();
            if (player == null) return;
            AffinityData data = player.getData(AttachmentTypes.AFFINITY.get());
            try {
                data.setLevel(AffinityType.valueOf(p.typeName()), p.level());
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
