package com.otectus.arsnspells.network;

import com.otectus.arsnspells.ArsNSpells;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server to client sync of a player's <em>entire</em> affinity map in one packet.
 *
 * <p>Replaces the one-packet-per-school burst the full resyncs used to send. That pattern was
 * inherited from the 1.20.1 line, where affinity was a fixed 16-value enum and the burst was
 * bounded at 16. This port deliberately re-keyed affinity to full school ids so every Iron's
 * addon school is tracked - a real improvement, but it turned a bounded burst into an
 * unbounded one, and the resync runs on login, on respawn <em>and</em> on every dimension
 * change. A modpack with several school-adding addons pays that per player per nether portal.
 *
 * <p>{@link AffinitySyncPayload} stays for single-school deltas, which is what the cast and
 * decay handlers actually produce.
 */
public record AffinityBulkSyncPayload(Map<String, Integer> levels) implements CustomPacketPayload {

    /**
     * Wire cap for the school id. A full id is {@code namespace:path} and comfortably under
     * this; an unbounded string read defaults to {@code Short.MAX_VALUE}, i.e. 32 KB allocated
     * per string on demand.
     */
    private static final int MAX_ID_LENGTH = 128;

    /** Affinity is defined on [0,100]; anything else is malformed or hostile. */
    private static final int MAX_LEVEL = 100;

    /**
     * Wire cap on the number of tracked schools. Iron's ships nine and this mod adds three;
     * the slack is for addon schools, which is the whole reason the map is keyed by id. It is
     * still a bound, which an unbounded map codec is not.
     */
    private static final int MAX_SCHOOLS = 256;

    /** Sanitize at construction so nothing downstream has to trust the wire (ANS-MED-016). */
    public AffinityBulkSyncPayload {
        Map<String, Integer> clean = new LinkedHashMap<>();
        if (levels != null) {
            levels.forEach((key, level) -> {
                if (key == null || key.isEmpty() || level == null || clean.size() >= MAX_SCHOOLS) {
                    return;
                }
                clean.put(key, Math.max(0, Math.min(MAX_LEVEL, level)));
            });
        }
        levels = Map.copyOf(clean);
    }

    public static final Type<AffinityBulkSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "affinity_bulk_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AffinityBulkSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.map(
                size -> new LinkedHashMap<>(Math.min(size, MAX_SCHOOLS)),
                ByteBufCodecs.stringUtf8(MAX_ID_LENGTH),
                ByteBufCodecs.VAR_INT,
                MAX_SCHOOLS),                       AffinityBulkSyncPayload::levels,
            AffinityBulkSyncPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnClient(AffinityBulkSyncPayload p, IPayloadContext ctx) {
        ClientHandler.apply(p, ctx);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        static void apply(AffinityBulkSyncPayload p, IPayloadContext ctx) {
            net.minecraft.world.entity.player.Player player = ctx.player();
            if (player == null) {
                return;
            }
            AffinityData data = player.getData(AttachmentTypes.AFFINITY.get());
            data.replaceLevels(p.levels());
        }
    }
}
