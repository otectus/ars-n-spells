package com.otectus.arsnspells.glyph.behavior;

import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.recipe.ViewerRecipeSet;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 3.4.1 review SYNC-01 / VIEW-01: a live snapshot is published whole or not at all, the two halves
 * of a publication are applied together and in order, and a recipe viewer's set stays bounded.
 */
class SnapshotPublicationTest {
    private static final ResourceLocation FIRE = new ResourceLocation("irons_spellbooks", "fire");

    private static byte[] encoded(GlyphLiveSnapshot snapshot) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        snapshot.write(buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    private static GlyphLiveSnapshot decode(byte[] bytes, ImportedGlyphCatalog catalog) {
        return GlyphLiveSnapshot.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)), catalog);
    }

    @Test
    void aCompleteSnapshotRoundTripsWithTheServersFeatureFlag() {
        ImportedGlyphCatalog catalog = GlyphLiveDecisionTest.catalog();
        GlyphLiveSnapshot server = GlyphLiveDecisionTest.live(catalog, spell -> GlyphLiveDecisionTest.facts(FIRE, 5)).withGlyphsEnabled(false);
        GlyphLiveSnapshot client = decode(encoded(server), catalog);
        assertEquals(server.digest(), client.digest());
        assertFalse(client.glyphsEnabled(), "the server's feature switch travels with its facts");
        assertTrue(client.received() && !server.received());
        assertNotEquals(server.withGlyphsEnabled(true).digest(), server.digest(), "the switch is part of what clients must agree on");
    }

    @Test
    void contentThatDoesNotReproduceItsDigestIsRefused() {
        ImportedGlyphCatalog catalog = GlyphLiveDecisionTest.catalog();
        byte[] bytes = encoded(GlyphLiveDecisionTest.live(catalog, spell -> GlyphLiveDecisionTest.facts(FIRE, 5)));
        // The last byte is the last rarity index of the last spell: change what it says, not how it is framed.
        bytes[bytes.length - 1] = (byte) (bytes[bytes.length - 1] == 0 ? 1 : 0);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> decode(bytes, catalog));
        assertTrue(refused.getMessage().contains("digest") || refused.getMessage().contains("rarity"), refused.getMessage());
    }

    @Test
    void anIncompleteSnapshotIsRefusedNotFilledWithDefaults() {
        ImportedGlyphCatalog catalog = GlyphLiveDecisionTest.catalog();
        GlyphLiveSnapshot full = GlyphLiveDecisionTest.live(catalog, spell -> GlyphLiveDecisionTest.facts(FIRE, 5));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(full.catalogDigest(), 64);
        buffer.writeUtf(full.digest(), 64);
        buffer.writeByte(GlyphLiveSnapshot.Readiness.READY.ordinal());
        buffer.writeBoolean(true);
        buffer.writeVarInt(1);
        buffer.writeUtf(FIRE.toString(), 256);
        buffer.writeVarInt(1);
        buffer.writeUtf("COMMON", 256);
        buffer.writeVarInt(catalog.spells().size() - 1);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> GlyphLiveSnapshot.read(buffer, catalog));
        assertTrue(refused.getMessage().contains("the catalog has"), refused.getMessage());
    }

    @Test
    void halvesOfAPublicationApplyTogetherAndInOrder() {
        PublicationStage<String, String> stage = new PublicationStage<>();
        assertNull(stage.mapping(2, "map2"), "half a publication applies nothing");
        assertNull(stage.live(1, "live1"), "a part older than the publication being assembled is stale");
        var two = stage.live(2, "live2");
        assertNotNull(two, "the stale part did not disturb publication 2");
        assertEquals("map2", two.mapping());
        assertEquals("live2", two.live());
        assertNull(stage.mapping(3, "map3"));
        assertNull(stage.mapping(4, "map4"), "a newer publication discards the staged older parts");
        assertNull(stage.live(3, "live3"), "publication 3 can no longer complete");
        var four = stage.live(4, "live4");
        assertNotNull(four);
        assertEquals("map4", four.mapping());
        assertNull(stage.live(2, "late"), "a part older than what was applied is ignored");
        assertNull(stage.mapping(5, "map5"));
        stage.refused(5);
        assertNull(stage.live(5, "live5"), "a refused publication never completes");
        stage.reset();
        assertNull(stage.mapping(1, "next-server-map"));
        assertNotNull(stage.live(1, "next-server-live"), "a new connection starts from nothing");
    }

    @Test
    void aViewerSetChangesNothingOnUnchangedRefreshesAndStaysBounded() {
        ViewerRecipeSet<String[]> set = new ViewerRecipeSet<>(recipe -> recipe[0]);
        assertEquals(10, set.next(recipes("a")).add().size());
        for (int i = 0; i < 100; i++) assertFalse(set.next(recipes("a")).changes(), "an unchanged refresh changes nothing");
        assertEquals(10, set.retained());
        for (int i = 0; i < 50; i++) {
            var plan = set.next(recipes(i % 2 == 0 ? "b" : "a"));
            assertTrue(plan.changes());
            if (i >= 2) assertTrue(plan.add().isEmpty(), "a recurring state is unhidden, never added again");
        }
        assertEquals(20, set.retained(), "two alternating states retain two sets of objects, no more");
        set.clear();
        assertEquals(10, set.next(recipes("a")).add().size(), "a new runtime is given every recipe again");
    }

    private static List<String[]> recipes(String terms) {
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < 10; i++) out.add(new String[]{"glyph" + i + "|" + terms});
        return out;
    }
}
