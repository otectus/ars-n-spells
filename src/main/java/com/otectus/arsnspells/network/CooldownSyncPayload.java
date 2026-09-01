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

    /** Wire cap for the category name (ANS-MED-016); the longest real value is 9 characters. */
    private static final int MAX_NAME_LENGTH = 32;

    /**
     * Ceiling on a cooldown end-tick (ANS-MED-017). Without it a hostile server sends
     * {@code Long.MAX_VALUE} and the category is on cooldown effectively forever, with no way
     * for the player to clear it. 1e12 ticks is ~1.6 million real-world years.
     */
    private static final long MAX_TIMESTAMP = 1_000_000_000_000L;

    /** Clamp at construction so nothing downstream has to trust the wire. */
    public CooldownSyncPayload {
        categoryName = categoryName == null ? "" : categoryName;
        timestamp = Math.max(0L, Math.min(MAX_TIMESTAMP, timestamp));
    }

    public static final Type<CooldownSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "cooldown_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CooldownSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), CooldownSyncPayload::categoryName,
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
