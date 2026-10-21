package com.otectus.arsnspells.client;

import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import com.otectus.arsnspells.network.GlyphCatalogHandshakePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-only receivers for the imported-glyph catalog handshake and the behaviour view. */
public final class GlyphClientSync {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlyphClientSync.class);

    private GlyphClientSync() {}

    /** Disconnect with a readable error when the server's catalog identity is not this client's. */
    public static void verifyCatalog(GlyphCatalogHandshakePacket server) {
        ImportedGlyphCatalog local = ImportedGlyphCatalog.get();
        if (local.identityDigest().equals(server.identityDigest())) return;
        LOGGER.error("Imported-glyph catalog mismatch: server {} ({} glyphs, {}), client {} ({} glyphs, {})",
            server.catalogVersion(), server.glyphCount(), server.identityDigest(),
            local.catalogVersion(), local.size(), local.identityDigest());
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.getConnection().disconnect(Component.translatable("ars_n_spells.glyph.catalog_mismatch",
                server.catalogVersion(), server.glyphCount(), local.catalogVersion(), local.size()));
        }
    }

    /**
     * Adopt the server's behaviour view. A single-player or LAN host shares the server's own
     * snapshot in this JVM, which is already authoritative, so only a remote client replaces it.
     */
    public static void acceptMapping(GlyphMappingSnapshot.ClientView view) {
        if (Minecraft.getInstance().hasSingleplayerServer()) return;
        GlyphMappingSnapshot.publish(GlyphMappingSnapshot.fromClientView(view));
    }

    /** Leaving a remote server drops its view, so nothing of it outlives the connection. */
    public static void onLoggingOut() {
        if (!Minecraft.getInstance().hasSingleplayerServer()) GlyphMappingSnapshot.publish(GlyphMappingSnapshot.defaults());
    }
}
