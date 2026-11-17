package com.otectus.arsnspells.glyph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.glyph.recipe.SharedMappingFixturesTest;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the catalog-assembly fixtures shared with the NeoForge build ({@code ans_shared_fixtures/catalogs}):
 * the same required catalog and optional sources register the same identities on both loaders, a
 * later source may only extend an envelope with the same adapter, a rejected source changes
 * nothing, and a broken required catalog or a lost published identity stops startup.
 */
class SharedCatalogFixturesTest {
    static JsonObject resource(String name) throws Exception {
        return JsonParser.parseString(SharedMappingFixturesTest.text("/ans_shared_fixtures/catalogs/" + name)).getAsJsonObject();
    }

    @TestFactory
    List<DynamicTest> sharedFixtures() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement name : resource("index.json").getAsJsonArray("cases")) {
            JsonObject fixture = resource(name.getAsString() + ".json");
            // A package the pack tool built for one loader (3.4.1 review CATALOG-02) is that loader's to load.
            if (fixture.has("loader") && !"forge".equals(fixture.get("loader").getAsString())) continue;
            tests.add(DynamicTest.dynamicTest(name.getAsString(), () -> check(fixture)));
        }
        assertTrue(tests.size() >= 10, "the shared catalog fixture set shrank");
        return tests;
    }

    private static void check(JsonObject fixture) {
        List<ImportedGlyphCatalog.Source> sources = new ArrayList<>();
        for (JsonElement element : fixture.getAsJsonArray("sources")) {
            JsonObject source = element.getAsJsonObject();
            JsonElement json = source.get("json");
            sources.add(new ImportedGlyphCatalog.Source(source.get("name").getAsString(),
                json.isJsonPrimitive() ? json.getAsString() : json.toString(), source.get("required").getAsBoolean()));
        }
        Set<ResourceLocation> ledger = new LinkedHashSet<>();
        if (fixture.has("ledger")) fixture.getAsJsonArray("ledger").forEach(id -> ledger.add(new ResourceLocation(id.getAsString())));
        JsonObject expect = fixture.getAsJsonObject("expect");
        ImportedGlyphCatalog catalog = null;
        String failure = null;
        try {
            catalog = ImportedGlyphCatalog.assemble(sources, ledger);
        } catch (ImportedGlyphCatalog.CatalogException stopped) {
            failure = stopped.getMessage();
        }
        assertEquals(expect.get("fails_startup").getAsBoolean(), failure != null, "fails startup: " + failure);
        List<String> messages = new ArrayList<>();
        if (failure != null) messages.add(failure);
        if (catalog != null) for (ImportedGlyphCatalog.SourceReport report : catalog.sources()) messages.addAll(report.errors());
        if (expect.has("errors_contain")) for (JsonElement fragment : expect.getAsJsonArray("errors_contain"))
            assertTrue(String.join("\n", messages).contains(fragment.getAsString()), "errors should mention '" + fragment.getAsString() + "': " + messages);
        if (catalog == null) return;
        if (expect.has("identities")) assertEquals(expect.get("identities").getAsInt(), catalog.definitions().size(), "identities");
        if (expect.has("registered_max")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("registered_max").entrySet())
            assertEquals(e.getValue().getAsInt(), catalog.registeredMax(new ResourceLocation(e.getKey())), "registered maximum of " + e.getKey());
        if (expect.has("absent")) for (JsonElement spell : expect.getAsJsonArray("absent"))
            assertTrue(catalog.levelsOf(new ResourceLocation(spell.getAsString())).isEmpty(), spell + " should not be catalogued");
        if (expect.has("rejected")) for (JsonElement name : expect.getAsJsonArray("rejected"))
            assertTrue(rejected(catalog, name.getAsString()), name + " should be rejected: " + catalog.sources());
        if (expect.has("accepted")) for (JsonElement name : expect.getAsJsonArray("accepted"))
            assertFalse(rejected(catalog, name.getAsString()), name + " should be accepted: " + catalog.sources());
        if (expect.has("status")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("status").entrySet()) {
            List<ImportedGlyphDefinition> levels = catalog.levelsOf(new ResourceLocation(e.getKey()));
            assertFalse(levels.isEmpty(), e.getKey() + " is catalogued");
            for (ImportedGlyphDefinition level : levels) assertEquals(e.getValue().getAsString(), level.status().id(), "status of " + level.key());
        }
        // 3.4.1 review CATALOG-02: contracts, minimum levels and per-level rarity.
        if (expect.has("executable")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("executable").entrySet())
            assertEquals(e.getValue().getAsBoolean(), first(catalog, e.getKey()).executable(), "executable " + e.getKey());
        if (expect.has("adapter")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("adapter").entrySet())
            assertEquals(e.getValue().getAsString(), first(catalog, e.getKey()).family().adapterId(), "adapter of " + e.getKey());
        if (expect.has("first_level")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("first_level").entrySet())
            assertEquals(e.getValue().getAsInt(), catalog.minLevel(new ResourceLocation(e.getKey())), "first level of " + e.getKey());
        if (expect.has("rarity_at")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("rarity_at").entrySet()) {
            var glyph = catalog.byGlyphId(new ResourceLocation(e.getKey()));
            assertTrue(glyph.isPresent(), e.getKey() + " is registered");
            assertEquals(e.getValue().getAsString(), glyph.get().defaultRarity(), "rarity of " + e.getKey());
        }
        if (expect.has("glyphs_absent")) for (JsonElement id : expect.getAsJsonArray("glyphs_absent"))
            assertTrue(catalog.byGlyphId(new ResourceLocation(id.getAsString())).isEmpty(), id + " should not be registered");
    }

    private static ImportedGlyphDefinition first(ImportedGlyphCatalog catalog, String spell) {
        List<ImportedGlyphDefinition> levels = catalog.levelsOf(new ResourceLocation(spell));
        assertFalse(levels.isEmpty(), spell + " is catalogued");
        return levels.get(0);
    }

    private static boolean rejected(ImportedGlyphCatalog catalog, String source) {
        return catalog.sources().stream().anyMatch(report -> report.name().equals(source) && !report.errors().isEmpty());
    }
}
