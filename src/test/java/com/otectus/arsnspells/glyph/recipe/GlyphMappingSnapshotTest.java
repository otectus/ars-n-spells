package com.otectus.arsnspells.glyph.recipe;

import com.google.gson.JsonParser;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlyphMappingSnapshotTest {
    static final String CATALOG = "{\"schema_version\":1,\"catalog_version\":\"t\",\"id_encoding\":\"ars_n_spells:iron/<spell namespace>/<spell path>/level_<n>\","
        + "\"irons_builds\":[\"x\"],\"spells\":[" + com.otectus.arsnspells.glyph.ImportedGlyphCatalogTest.spell("irons_spellbooks:firebolt") + ","
        + com.otectus.arsnspells.glyph.ImportedGlyphCatalogTest.spell("irons_spellbooks:heal").replace("irons_spellbooks:fire\"", "irons_spellbooks:holy\"") + "]}";

    static ImportedGlyphCatalog catalog() { return ImportedGlyphCatalog.parse(CATALOG); }

    static GlyphMappingSnapshot.Source source(String path, String pack, int rank, String json) {
        return new GlyphMappingSnapshot.Source(new ResourceLocation("test", path), pack, rank, JsonParser.parseString(json));
    }

    static ImportedGlyphDefinition def(ImportedGlyphCatalog catalog, String spell, int level) {
        return catalog.levelsOf(new ResourceLocation(spell)).get(level - 1);
    }

    @Test
    void defaultsMatchTheShippedTierTerms() {
        GlyphMappingSnapshot snapshot = GlyphMappingSnapshot.build(List.of(), catalog(), 1);
        assertTrue(snapshot.errors().isEmpty());
        assertEquals(500, snapshot.tiers().get(1).sourceCost());
        assertEquals(2, snapshot.tiers().get(2).catalystCount());
        assertEquals(200, snapshot.tiers().get(3).durationTicks());
        assertEquals(3000, snapshot.maxSourceCost());
    }

    @Test
    void perLevelOverrideAppliesToThatLevelOnly() {
        ImportedGlyphCatalog catalog = catalog();
        GlyphMappingSnapshot snapshot = GlyphMappingSnapshot.build(List.of(source("a", "pack", 1,
            "{\"schema_version\":1,\"spell\":\"irons_spellbooks:firebolt\",\"levels\":{\"2\":{\"ars_tier\":2,\"source_cost\":1234}}}")), catalog, 1);
        assertTrue(snapshot.errors().isEmpty(), snapshot.errors().toString());
        assertEquals(2, snapshot.tierOverride(def(catalog, "irons_spellbooks:firebolt", 2)));
        assertNull(snapshot.tierOverride(def(catalog, "irons_spellbooks:firebolt", 1)));
        assertEquals(1234, snapshot.pressTerms(def(catalog, "irons_spellbooks:firebolt", 2), 2).sourceCost());
        // The unspecified duration and catalyst fall back to the overridden tier's terms.
        assertEquals(GlyphMappingSnapshot.DEFAULT_TIERS.get(2).durationTicks(), snapshot.pressTerms(def(catalog, "irons_spellbooks:firebolt", 2), 2).durationTicks());
    }

    @Test
    void explicitDenyWinsOverAllow() {
        ImportedGlyphCatalog catalog = catalog();
        GlyphMappingSnapshot snapshot = GlyphMappingSnapshot.build(List.of(source("p", "pack", 1,
            "{\"schema_version\":1,\"type\":\"ars_n_spells:policy\",\"allow_spells\":[\"irons_spellbooks:firebolt\"],"
                + "\"deny_schools\":[\"irons_spellbooks:fire\"]}")), catalog, 1);
        assertFalse(snapshot.allows(def(catalog, "irons_spellbooks:firebolt", 1)), "deny school beats allow spell");
        assertFalse(snapshot.allows(def(catalog, "irons_spellbooks:heal", 1)), "an allow list excludes everything it does not name");
    }

    @Test
    void laterPackReplacesEarlierAndSamePackDuplicateIsAnError() {
        ImportedGlyphCatalog catalog = catalog();
        String off = "{\"schema_version\":1,\"spell\":\"irons_spellbooks:firebolt\",\"enabled\":false}";
        String on = "{\"schema_version\":1,\"spell\":\"irons_spellbooks:firebolt\",\"enabled\":true}";
        GlyphMappingSnapshot later = GlyphMappingSnapshot.build(List.of(source("a", "low", 1, off), source("b", "high", 2, on)), catalog, 1);
        assertTrue(later.errors().isEmpty());
        assertTrue(later.allows(def(catalog, "irons_spellbooks:firebolt", 1)));
        assertTrue(later.winners().stream().anyMatch(w -> w.contains("[high]")), later.winners().toString());
        GlyphMappingSnapshot clash = GlyphMappingSnapshot.build(List.of(source("a", "same", 1, off), source("b", "same", 1, on)), catalog, 1);
        assertFalse(clash.errors().isEmpty());
        assertFalse(clash.allows(def(catalog, "irons_spellbooks:firebolt", 1)), "a conflicting spell fails closed");
    }

    @Test
    void cannotManufactureIdsOrRemapLevels() {
        ImportedGlyphCatalog catalog = catalog();
        GlyphMappingSnapshot unknown = GlyphMappingSnapshot.build(List.of(source("a", "p", 1,
            "{\"schema_version\":1,\"spell\":\"irons_spellbooks:not_a_spell\",\"enabled\":true}")), catalog, 1);
        assertFalse(unknown.errors().isEmpty());
        GlyphMappingSnapshot beyond = GlyphMappingSnapshot.build(List.of(source("a", "p", 1,
            "{\"schema_version\":1,\"spell\":\"irons_spellbooks:firebolt\",\"levels\":{\"3\":{\"ars_tier\":1}}}")), catalog, 1);
        assertFalse(beyond.errors().isEmpty(), "level 3 is not a registered glyph for a 2-level spell");
        assertFalse(beyond.allows(def(catalog, "irons_spellbooks:firebolt", 1)), "the affected spell fails closed");
        assertTrue(beyond.allows(def(catalog, "irons_spellbooks:heal", 1)), "unaffected spells stay available");
    }

    @Test
    void rejectsUnboundedNumbers() {
        ImportedGlyphCatalog catalog = catalog();
        for (String bad : new String[] {"{\"source_cost\":-1}", "{\"source_cost\":100001}", "{\"duration_ticks\":0}",
                "{\"catalyst\":{\"count\":65}}", "{\"ars_tier\":4}"}) {
            GlyphMappingSnapshot snapshot = GlyphMappingSnapshot.build(List.of(source("a", "p", 1,
                "{\"schema_version\":1,\"spell\":\"irons_spellbooks:firebolt\",\"levels\":{\"1\":" + bad + "}}")), catalog, 1);
            assertFalse(snapshot.errors().isEmpty(), bad);
        }
    }

    @Test
    void digestTracksBehaviour() {
        ImportedGlyphCatalog catalog = catalog();
        String base = GlyphMappingSnapshot.build(List.of(), catalog, 1).digest();
        assertEquals(base, GlyphMappingSnapshot.build(List.of(), catalog, 2).digest(), "generation is not behaviour");
        String changed = GlyphMappingSnapshot.build(List.of(source("a", "p", 1,
            "{\"schema_version\":1,\"spell\":\"irons_spellbooks:firebolt\",\"enabled\":false}")), catalog, 1).digest();
        assertNotEquals(base, changed);
    }

    @Test
    void adapterMustBeKnownAndMatchTheCatalog() {
        ImportedGlyphCatalog catalog = catalog();
        GlyphMappingSnapshot same = GlyphMappingSnapshot.build(List.of(source("a", "p", 1,
            "{\"schema_version\":1,\"spell\":\"irons_spellbooks:firebolt\",\"adapter\":\"ars_n_spells:aimed\"}")), catalog, 1);
        assertTrue(same.errors().isEmpty(), "naming the catalogued adapter is allowed: " + same.errors());
        GlyphMappingSnapshot other = GlyphMappingSnapshot.build(List.of(source("a", "p", 1,
            "{\"schema_version\":1,\"spell\":\"irons_spellbooks:firebolt\",\"adapter\":\"ars_n_spells:caster\"}")), catalog, 1);
        assertFalse(other.errors().isEmpty(), "a datapack cannot change an adapter");
        assertTrue(other.failedClosed().contains(new ResourceLocation("irons_spellbooks:firebolt")), "the spell fails closed");
        GlyphMappingSnapshot unknown = GlyphMappingSnapshot.build(List.of(source("a", "p", 1,
            "{\"schema_version\":1,\"spell\":\"irons_spellbooks:firebolt\",\"adapter\":\"ars_n_spells:teleporter\"}")), catalog, 1);
        assertTrue(unknown.errors().get(0).contains("unknown adapter"), unknown.errors().toString());
    }

    @Test
    void clientViewRebuildsTheSameBehaviourAndKeepsTheServerDigest() {
        ImportedGlyphCatalog catalog = catalog();
        GlyphMappingSnapshot server = GlyphMappingSnapshot.build(List.of(
            source("tiers", "p", 1, "{\"schema_version\":1,\"type\":\"ars_n_spells:press_tiers\",\"tiers\":{\"2\":"
                + "{\"source_cost\":900,\"duration_ticks\":40,\"catalyst\":{\"tag\":\"ars_n_spells:glyph_press_catalysts\",\"count\":3}}}}"),
            source("bolt", "p", 1, "{\"schema_version\":1,\"spell\":\"irons_spellbooks:firebolt\",\"levels\":{\"2\":{\"ars_tier\":2,\"source_cost\":700}}}"),
            source("policy", "p", 1, "{\"schema_version\":1,\"type\":\"ars_n_spells:policy\",\"deny_spells\":[\"irons_spellbooks:heal\"]}")),
            catalog, 7);
        assertTrue(server.errors().isEmpty(), server.errors().toString());
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        new com.otectus.arsnspells.network.GlyphMappingSyncPacket(server.clientView()).toBytes(buffer);
        GlyphMappingSnapshot client = GlyphMappingSnapshot.fromClientView(new com.otectus.arsnspells.network.GlyphMappingSyncPacket(buffer).view());
        assertEquals(server.digest(), client.digest(), "the client keeps the server's identity");
        assertEquals(7, client.generation());
        for (int level = 1; level <= 2; level++) {
            ImportedGlyphDefinition bolt = def(catalog, "irons_spellbooks:firebolt", level);
            int tier = level == 2 ? 2 : bolt.defaultTier();
            assertEquals(server.pressTerms(bolt, tier), client.pressTerms(bolt, tier), "terms for level " + level);
            assertEquals(server.tierOverride(bolt), client.tierOverride(bolt));
            assertEquals(server.allows(bolt), client.allows(bolt));
        }
        assertFalse(client.allows(def(catalog, "irons_spellbooks:heal", 1)), "the deny reaches the client");
        assertEquals(server.tiers(), client.tiers());
    }

    @Test
    void syncPacketRejectsAnOversizedView() {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buffer.writeVarInt(0);
        buffer.writeVarInt(100_000);
        assertThrows(IllegalArgumentException.class, () -> new com.otectus.arsnspells.network.GlyphMappingSyncPacket(buffer));
    }
}
