package com.otectus.arsnspells.network;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.cooldown.CooldownCategory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → client sync of one cooldown category's end-tick timestamp. */
public record CooldownSyncPayload(String categoryName, long timestamp) implements CustomPacketPayload {

    public static final Type<CooldownSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "cooldown_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CooldownSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CooldownSyncPayload::categoryName,
            ByteBufCodecs.VAR_LONG,    CooldownSyncPayload::timestamp,
            CooldownSyncPayload::new
        );

    public CooldownSyncPayload(CooldownCategory category, long timestamp) {
        this(category.name(), timestamp);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnClient(CooldownSyncPayload p, IPayloadContext ctx) {
        ClientHandler.apply(p);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        static void apply(CooldownSyncPayload p) {
            try {
                CooldownCategory cat = CooldownCategory.valueOf(p.categoryName());
                com.otectus.arsnspells.cooldown.UnifiedCooldownManager.setClientCooldownEnd(cat, p.timestamp());
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
