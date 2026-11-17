package com.otectus.arsnspells.network;

import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.behavior.GlyphLiveSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The server's live Iron's facts for every catalogued spell (enabled state, effective maximum,
 * school, learning requirement, rarity per registered level, readiness), so a remote client's
 * editor, tooltips, augment limits and recipe viewer decide exactly as the server does. Sent after
 * the server's own facts are rebuilt on each datapack sync. Decoding is bounded and refuses a copy
 * written for a different catalog.
 */
public record GlyphLiveSyncPacket(long publication, @org.jetbrains.annotations.Nullable GlyphLiveSnapshot snapshot, String rejection) {
    /** The whole payload must fit this, whatever its counts claim. */
    static final int MAX_PAYLOAD_BYTES = 1 << 20;

    public GlyphLiveSyncPacket(long publication, GlyphLiveSnapshot snapshot) {
        this(publication, snapshot, "");
    }

    /**
     * 3.4.1 review SYNC-01: decoding never throws into the network layer, which would disconnect the
     * client. A malformed or incomplete copy arrives as a rejection the client logs; nothing of it is
     * published.
     */
    public GlyphLiveSyncPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    private GlyphLiveSyncPacket(GlyphLiveSyncPacket decoded) {
        this(decoded.publication, decoded.snapshot, decoded.rejection);
    }

    private static GlyphLiveSyncPacket decode(FriendlyByteBuf buffer) {
        long publication = -1;
        try {
            if (buffer.readableBytes() > MAX_PAYLOAD_BYTES)
                return reject(buffer, publication, "larger than " + MAX_PAYLOAD_BYTES + " bytes");
            publication = buffer.readVarLong();
            GlyphLiveSnapshot snapshot = GlyphLiveSnapshot.read(buffer, ImportedGlyphCatalog.get());
            if (buffer.readableBytes() != 0) return reject(buffer, publication, buffer.readableBytes() + " unread bytes");
            return new GlyphLiveSyncPacket(publication, snapshot, "");
        } catch (RuntimeException malformed) {
            return reject(buffer, publication, malformed.toString());
        }
    }

    private static GlyphLiveSyncPacket reject(FriendlyByteBuf buffer, long publication, String why) {
        if (buffer.readableBytes() > 0) buffer.skipBytes(buffer.readableBytes());
        return new GlyphLiveSyncPacket(publication, null, why);
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarLong(publication);
        if (snapshot == null) throw new IllegalStateException("a refused live view is never sent");
        snapshot.write(buffer);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var network = supplier.get();
        if (network == null) return;
        network.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.otectus.arsnspells.client.GlyphClientSync.acceptLive(this)));
        network.setPacketHandled(true);
    }
}
