package com.otectus.arsnspells.glyph.behavior;

import com.otectus.arsnspells.glyph.GlyphTestFixtures;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.recipe.ViewerRecipeSet;
import com.otectus.arsnspells.network.ImportedGlyphSyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 3.4.1 review SYNC-01 / VIEW-01 on NeoForge: a behaviour snapshot is published whole or not at all,
 * and a recipe viewer's set stays bounded across refreshes and runtimes.
 */
class SnapshotPublicationTest {
    @BeforeEach
    void install() {
        ImportedGlyphCatalog.install(GlyphTestFixtures.catalog());
    }

    @AfterEach
    void uninstall() {
        ImportedGlyphCatalog.install(ImportedGlyphCatalog.empty());
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test
    void aCompleteSnapshotRoundTripsThroughThePayload() {
        GlyphBehaviorSnapshot server = GlyphTestFixtures.snapshot();
        RegistryFriendlyByteBuf buf = buffer();
        ImportedGlyphSyncPayload.STREAM_CODEC.encode(buf, new ImportedGlyphSyncPayload(server));
        ImportedGlyphSyncPayload received = ImportedGlyphSyncPayload.STREAM_CODEC.decode(buf);
        assertNotNull(received.snapshot(), received.rejection());
        for (ImportedGlyphKey key : GlyphTestFixtures.catalog().keys())
            assertEquals(server.variant(key), received.snapshot().variant(key), key.toString());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void aBodyThatDoesNotMatchItsDigestIsRefusedNotPublished() {
        RegistryFriendlyByteBuf buf = buffer();
        ImportedGlyphSyncPayload.STREAM_CODEC.encode(buf, new ImportedGlyphSyncPayload(GlyphTestFixtures.snapshot()));
        int last = buf.writerIndex() - 1;
        buf.setByte(last, buf.getByte(last) ^ 0x5A);
        ImportedGlyphSyncPayload received = ImportedGlyphSyncPayload.STREAM_CODEC.decode(buf);
        assertNull(received.snapshot(), "a tampered body publishes nothing");
        assertTrue(received.rejection().contains("digest"), received.rejection());
        assertEquals(0, buf.readableBytes(), "the rest of a refused frame is consumed, not left for the next packet");
    }

    @Test
    void aTruncatedOrOversizedFrameIsRefusedWithoutThrowing() {
        RegistryFriendlyByteBuf whole = buffer();
        ImportedGlyphSyncPayload.STREAM_CODEC.encode(whole, new ImportedGlyphSyncPayload(GlyphTestFixtures.snapshot()));
        RegistryFriendlyByteBuf cut = buffer();
        cut.writeBytes(whole, whole.readableBytes() / 2);
        assertNull(ImportedGlyphSyncPayload.STREAM_CODEC.decode(cut).snapshot(), "half a frame publishes nothing");
        RegistryFriendlyByteBuf huge = buffer();
        huge.writeVarInt(ImportedGlyphSyncPayload.MAX_BODY_BYTES + 1);
        assertTrue(ImportedGlyphSyncPayload.STREAM_CODEC.decode(huge).rejection().contains("frame length"));
    }

    @Test
    void anIncompleteVariantTableIsRefused() {
        ImportedGlyphCatalog catalog = GlyphTestFixtures.catalog();
        FriendlyByteBuf body = new FriendlyByteBuf(Unpooled.buffer());
        body.writeVarInt(1);
        body.writeUtf(catalog.hash(), 64);
        body.writeUtf("0".repeat(64), 64);
        body.writeBoolean(true);
        body.writeBoolean(true);
        body.writeUtf("per_spell", 32);
        for (int i = 0; i < 4; i++) body.writeVarInt(1);
        body.writeVarInt(1);
        body.writeUtf("ars_n_spells:glyph_press_catalysts", 256);
        body.writeVarInt(catalog.keys().size() - 1);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> GlyphBehaviorSnapshot.read(body, catalog));
        assertTrue(refused.getMessage().contains("variant table lists"), refused.getMessage());
    }

    @Test
    void aViewerSetChangesNothingOnUnchangedRefreshesAndStaysBounded() {
        ViewerRecipeSet<String[]> set = new ViewerRecipeSet<>(recipe -> recipe[0]);
        List<String[]> a = recipes("a"), b = recipes("b");
        ViewerRecipeSet.Plan<String[]> first = set.next(a);
        assertEquals(10, first.add().size());
        for (int i = 0; i < 100; i++) assertFalse(set.next(recipes("a")).changes(), "an unchanged refresh changes nothing");
        assertEquals(10, set.retained());
        for (int i = 0; i < 50; i++) {
            ViewerRecipeSet.Plan<String[]> toB = set.next(i % 2 == 0 ? b : a);
            assertTrue(toB.changes());
            if (i >= 2) assertTrue(toB.add().isEmpty(), "a recurring state is unhidden, never added again");
        }
        assertEquals(20, set.retained(), "two alternating states retain two sets of objects, no more");
        set.clear();
        assertEquals(10, set.next(a).add().size(), "a new runtime is given every recipe again, none assumed registered");
    }

    private static List<String[]> recipes(String terms) {
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < 10; i++) out.add(new String[]{"glyph" + i + "|" + terms});
        return out;
    }
}
