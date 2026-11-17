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
     * 3.4.1 review SYNC-01: the server's mapping view and live facts arrive as two packets of one
     * publication; a remote client applies them together, once both are here, and never a refused or
     * stale part. A single-player or LAN host shares the server's own state in this JVM.
     */
    private static final com.otectus.arsnspells.glyph.behavior.PublicationStage<GlyphMappingSnapshot.ClientView,
        com.otectus.arsnspells.glyph.behavior.GlyphLiveSnapshot> STAGE = new com.otectus.arsnspells.glyph.behavior.PublicationStage<>();

    public static void acceptMapping(com.otectus.arsnspells.network.GlyphMappingSyncPacket packet) {
        if (Minecraft.getInstance().hasSingleplayerServer()) return;
        if (packet.view() == null) {
            refuse(packet.publication(), "mapping view", packet.rejection());
            return;
        }
        apply(STAGE.mapping(packet.publication(), packet.view()));
    }

    public static void acceptLive(com.otectus.arsnspells.network.GlyphLiveSyncPacket packet) {
        if (Minecraft.getInstance().hasSingleplayerServer()) return;
        if (packet.snapshot() == null) {
            refuse(packet.publication(), "live facts", packet.rejection());
            return;
        }
        apply(STAGE.live(packet.publication(), packet.snapshot()));
    }

    private static void apply(@org.jetbrains.annotations.Nullable com.otectus.arsnspells.glyph.behavior.PublicationStage.Complete<
            GlyphMappingSnapshot.ClientView, com.otectus.arsnspells.glyph.behavior.GlyphLiveSnapshot> complete) {
        if (complete == null) return;
        com.otectus.arsnspells.glyph.behavior.GlyphBehavior.acceptClientGeneration(GlyphMappingSnapshot.fromClientView(complete.mapping()),
            complete.live());
    }

    private static void refuse(long publication, String part, String why) {
        STAGE.refused(publication);
        LOGGER.error("Refused the server's imported-glyph {} (publication {}): {}; keeping the last accepted view", part, publication, why);
    }

    /** Joining a remote server: its facts are unknown until it sends them, which is not the same as unavailable. */
    public static void onLoggingIn() {
        STAGE.reset();
        if (!Minecraft.getInstance().hasSingleplayerServer()) com.otectus.arsnspells.glyph.behavior.GlyphBehavior.resetClient(true);
    }

    /** Leaving a remote server drops its view, so nothing of it outlives the connection. */
    public static void onLoggingOut() {
        STAGE.reset();
        if (!Minecraft.getInstance().hasSingleplayerServer()) {
            GlyphMappingSnapshot.publish(GlyphMappingSnapshot.defaults());
            com.otectus.arsnspells.glyph.behavior.GlyphBehavior.resetClient(false);
        }
    }

    /**
     * 3.4.1 review SYNC-01 / VIEW-01: a catalyst tag's members can change without the behaviour
     * changing (it names the tag, not its items), so the viewer re-reads them when tags arrive.
     */
    public static void onTagsUpdated() {
        com.otectus.arsnspells.glyph.behavior.GlyphBehavior.viewerInputsChanged();
    }
}
