package com.otectus.arsnspells.network;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Server to client: the server's imported-glyph behaviour snapshot, so the client's spellbook
 * GUI, tooltips, the Glyph Press screen and JEI agree with what the server will allow. One snapshot
 * is one complete accepted generation: settings, every variant and every spell's live facts.
 *
 * <p>3.4.1 review SYNC-01: the snapshot travels as one framed body with its SHA-256, so the client
 * publishes only a body that arrived whole and whose content is exactly what the server encoded, and
 * the decoder ({@link GlyphBehaviorSnapshot#read}) requires a complete, unique, in-range entry set.
 * A body that fails any check is never published: the client keeps its last accepted snapshot (or
 * stays "not synced") and logs why, rather than being disconnected. The server checks the encoded
 * size before sending ({@link #encode}), so a catalog that loads cannot produce a payload no client
 * can take. A client whose startup catalog differs disconnects with a readable message.
 */
public record ImportedGlyphSyncPayload(@Nullable GlyphBehaviorSnapshot snapshot, String catalogHash, String rejection)
    implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportedGlyphSyncPayload.class);
    /** Vanilla's clientbound custom-payload limit, less room for the frame. */
    public static final int MAX_BODY_BYTES = (1 << 20) - 1024;

    public static final Type<ImportedGlyphSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "imported_glyph_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImportedGlyphSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buf, value) -> {
            byte[] body = encode(value.snapshot());
            if (body == null) throw new IllegalStateException("imported-glyph snapshot exceeds " + MAX_BODY_BYTES + " bytes");
            buf.writeVarInt(body.length);
            buf.writeBytes(sha256(body));
            buf.writeBytes(body);
        },
        ImportedGlyphSyncPayload::decode);

    public ImportedGlyphSyncPayload(GlyphBehaviorSnapshot snapshot) {
        this(snapshot, snapshot.catalogHash(), "");
    }

    /** The snapshot's encoded body, or null when it would not fit a payload. */
    @Nullable
    public static byte[] encode(@Nullable GlyphBehaviorSnapshot snapshot) {
        if (snapshot == null) return null;
        FriendlyByteBuf body = new FriendlyByteBuf(Unpooled.buffer());
        try {
            snapshot.write(body, ImportedGlyphCatalog.get());
            if (body.readableBytes() > MAX_BODY_BYTES) return null;
            byte[] bytes = new byte[body.readableBytes()];
            body.readBytes(bytes);
            return bytes;
        } finally {
            body.release();
        }
    }

    /** Read a frame; never throws into the network layer (a bad body becomes a rejection the handler logs). */
    private static ImportedGlyphSyncPayload decode(RegistryFriendlyByteBuf buf) {
        try {
            int length = buf.readVarInt();
            if (length < 0 || length > MAX_BODY_BYTES || length + 32 > buf.readableBytes())
                return rejected(buf, "", "frame length " + length + " does not match the payload");
            byte[] expected = new byte[32];
            buf.readBytes(expected);
            byte[] body = new byte[length];
            buf.readBytes(body);
            if (!Arrays.equals(expected, sha256(body))) return rejected(buf, "", "content does not match its digest");
            FriendlyByteBuf reader = new FriendlyByteBuf(Unpooled.wrappedBuffer(body));
            GlyphBehaviorSnapshot snapshot = GlyphBehaviorSnapshot.read(reader, ImportedGlyphCatalog.get());
            if (reader.readableBytes() != 0) return rejected(buf, snapshot.catalogHash(), reader.readableBytes() + " unread bytes after the snapshot");
            return new ImportedGlyphSyncPayload(snapshot, snapshot.catalogHash(), "");
        } catch (RuntimeException malformed) {
            return rejected(buf, "", malformed.toString());
        }
    }

    private static ImportedGlyphSyncPayload rejected(FriendlyByteBuf buf, String hash, String why) {
        if (buf.readableBytes() > 0) buf.skipBytes(buf.readableBytes());
        return new ImportedGlyphSyncPayload(null, hash, why);
    }

    static byte[] sha256(byte[] body) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(body);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(ImportedGlyphSyncPayload payload, IPayloadContext context) {
        String local = ImportedGlyphCatalog.get().hash();
        if (!payload.catalogHash().isEmpty() && !local.equals(payload.catalogHash())) {
            context.disconnect(net.minecraft.network.chat.Component.translatable("ars_n_spells.glyph.catalog_mismatch",
                payload.catalogHash().substring(0, Math.min(12, payload.catalogHash().length())), local.substring(0, 12)));
            return;
        }
        if (payload.snapshot() == null) {
            // Rejected input never overwrites accepted state.
            LOGGER.error("[ImportedGlyphs] refused the server's glyph behaviour snapshot ({}); keeping the last accepted one",
                payload.rejection());
            return;
        }
        com.otectus.arsnspells.glyph.behavior.GlyphBehavior.receiveClient(payload.snapshot());
        com.otectus.arsnspells.glyph.recipe.GlyphPressRecipes.behaviourChanged();
    }
}
