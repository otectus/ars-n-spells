package com.otectus.arsnspells.network;

import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The server's imported-glyph catalog identity, sent at login. A client whose catalog identity
 * differs would decode the same saved spell into different glyphs, so it disconnects itself with
 * a readable compatibility error instead of playing on a silent mismatch.
 */
public record GlyphCatalogHandshakePacket(String identityDigest, String catalogVersion, int glyphCount) {
    public GlyphCatalogHandshakePacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(64), buffer.readUtf(64), buffer.readVarInt());
    }

    public static GlyphCatalogHandshakePacket current() {
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        return new GlyphCatalogHandshakePacket(catalog.identityDigest(), catalog.catalogVersion(), catalog.size());
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(identityDigest, 64);
        buffer.writeUtf(catalogVersion, 64);
        buffer.writeVarInt(glyphCount);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var network = supplier.get();
        if (network == null) return;
        network.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.otectus.arsnspells.client.GlyphClientSync.verifyCatalog(this)));
        network.setPacketHandled(true);
    }
}
