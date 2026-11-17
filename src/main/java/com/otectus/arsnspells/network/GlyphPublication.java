package com.otectus.arsnspells.network;

import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphLiveSnapshot;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The server's one way to tell clients the imported-glyph behaviour in force (3.4.1 review SYNC-01):
 * the mapping view and the live facts of the same accepted generation, under one publication id, which
 * a remote client applies together ({@link com.otectus.arsnspells.glyph.behavior.PublicationStage}).
 *
 * <p>Both halves are encoded and measured before anything is sent: a generation that would not fit a
 * payload is not sent at all (clients keep their last accepted one, or stay "not synced") and the
 * server says why, instead of every joining client being disconnected by an undecodable packet.
 */
public final class GlyphPublication {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphPublication.class);
    private static final AtomicLong PUBLICATIONS = new AtomicLong();
    private static volatile long oversizeReported = -1;

    private GlyphPublication() {}

    public static void send(ServerPlayer player) {
        send(List.of(player));
    }

    public static void send(Collection<ServerPlayer> players) {
        if (players.isEmpty()) return;
        long id = PUBLICATIONS.incrementAndGet();
        GlyphMappingSyncPacket mapping = new GlyphMappingSyncPacket(id, GlyphMappingSnapshot.current().clientView());
        GlyphLiveSnapshot facts = GlyphBehavior.live();
        GlyphLiveSyncPacket live = new GlyphLiveSyncPacket(id, facts);
        int mappingBytes = size(mapping::toBytes), liveBytes = size(live::toBytes);
        if (mappingBytes > GlyphMappingSyncPacket.MAX_PAYLOAD_BYTES || liveBytes > GlyphLiveSyncPacket.MAX_PAYLOAD_BYTES) {
            if (oversizeReported != facts.digest().hashCode()) {
                oversizeReported = facts.digest().hashCode();
                LOGGER.error("[Glyph] not sending the imported-glyph behaviour: it encodes to {} + {} bytes, above the {}-byte payload "
                    + "limit; clients keep their last view. Reduce the installed glyph catalogs or mapping files.", mappingBytes, liveBytes,
                    GlyphLiveSyncPacket.MAX_PAYLOAD_BYTES);
            }
            return;
        }
        for (ServerPlayer player : players) {
            PacketHandler.sendToClient(mapping, player);
            PacketHandler.sendToClient(live, player);
        }
    }

    private static int size(java.util.function.Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buffer);
            return buffer.readableBytes();
        } catch (RuntimeException unwritable) {
            LOGGER.error("[Glyph] the imported-glyph behaviour cannot be encoded: {}", unwritable.toString());
            return Integer.MAX_VALUE;
        } finally {
            buffer.release();
        }
    }
}
