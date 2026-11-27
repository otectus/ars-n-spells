package com.otectus.arsnspells.glyph.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.behavior.SharedMappingFixturesTest;
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
 * Runs the catalog-assembly fixtures shared with the Forge build ({@code ans_shared_fixtures/catalogs}):
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
            if (fixture.has("loader") && !"neoforge".equals(fixture.get("loader").getAsString())) continue;
            tests.add(DynamicTest.dynamicTest(name.getAsString(), () -> check(fixture)));
        }
        assertTrue(tests.size() >= 10, "the shared catalog fixture set shrank");
        return tests;
    }

    private static void check(JsonObject fixture) {
        List<ImportedGlyphCatalog.Manifest> manifests = new ArrayList<>();
        for (JsonElement element : fixture.getAsJsonArray("sources")) {
            JsonObject source = element.getAsJsonObject();
            JsonElement json = source.get("json");
            manifests.add(new ImportedGlyphCatalog.Manifest(source.get("name").getAsString(),
                json.isJsonPrimitive() ? json.getAsString() : json.toString(), source.get("required").getAsBoolean()));
        }
        Set<ResourceLocation> ledger = new LinkedHashSet<>();
        if (fixture.has("ledger")) fixture.getAsJsonArray("ledger").forEach(id -> ledger.add(ResourceLocation.parse(id.getAsString())));
        JsonObject expect = fixture.getAsJsonObject("expect");
        ImportedGlyphCatalog catalog = null;
        String failure = null;
        try {
            catalog = ImportedGlyphCatalog.assemble(manifests, ledger);
        } catch (ImportedGlyphCatalog.CatalogException stopped) {
            failure = stopped.getMessage();
        }
        assertEquals(expect.get("fails_startup").getAsBoolean(), failure != null, "fails startup: " + failure);
        List<String> messages = new ArrayList<>();
        if (failure != null) messages.add(failure);
        if (catalog != null) messages.addAll(catalog.errors());
        if (expect.has("errors_contain")) for (JsonElement fragment : expect.getAsJsonArray("errors_contain"))
            assertTrue(String.join("\n", messages).contains(fragment.getAsString()), "errors should mention '" + fragment.getAsString() + "': " + messages);
        if (catalog == null) return;
        if (expect.has("identities")) assertEquals(expect.get("identities").getAsInt(), catalog.keys().size(), "identities");
        if (expect.has("registered_max")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("registered_max").entrySet()) {
            CatalogEntry entry = catalog.entry(ResourceLocation.parse(e.getKey()));
            assertNotNull(entry, e.getKey() + " is catalogued");
            assertEquals(e.getValue().getAsInt(), entry.registeredMax(), "registered maximum of " + e.getKey());
        }
        if (expect.has("absent")) for (JsonElement spell : expect.getAsJsonArray("absent"))
            assertNull(catalog.entry(ResourceLocation.parse(spell.getAsString())), spell + " should not be catalogued");
        if (expect.has("rejected")) for (JsonElement name : expect.getAsJsonArray("rejected"))
            assertTrue(rejected(catalog, name.getAsString()), name + " should be rejected: " + catalog.errors());
        if (expect.has("accepted")) for (JsonElement name : expect.getAsJsonArray("accepted"))
            assertFalse(rejected(catalog, name.getAsString()), name + " should be accepted: " + catalog.errors());
        if (expect.has("status")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("status").entrySet()) {
            CatalogEntry entry = catalog.entry(ResourceLocation.parse(e.getKey()));
            assertNotNull(entry, e.getKey() + " is catalogued");
            assertEquals(e.getValue().getAsString(), entry.status(), "status of " + e.getKey());
        }
        // 3.4.1 review CATALOG-02: contracts, minimum levels and per-level rarity.
        if (expect.has("executable")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("executable").entrySet())
            assertEquals(e.getValue().getAsBoolean(), entry(catalog, e.getKey()).executable(), "executable " + e.getKey());
        if (expect.has("adapter")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("adapter").entrySet())
            assertEquals(e.getValue().getAsString(), entry(catalog, e.getKey()).adapter().toString(), "adapter of " + e.getKey());
        if (expect.has("first_level")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("first_level").entrySet())
            assertEquals(e.getValue().getAsInt(), entry(catalog, e.getKey()).minLevel(), "first level of " + e.getKey());
        if (expect.has("rarity_at")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("rarity_at").entrySet()) {
            ImportedGlyphKey key = ImportedGlyphKey.decode(ResourceLocation.parse(e.getKey())).orElseThrow();
            assertTrue(catalog.contains(key), e.getKey() + " is registered");
            assertEquals(e.getValue().getAsString(), entry(catalog, key.ironSpellId().toString()).rarityFor(key.baseLevel()), "rarity of " + e.getKey());
        }
        if (expect.has("glyphs_absent")) for (JsonElement id : expect.getAsJsonArray("glyphs_absent")) {
            var key = ImportedGlyphKey.decode(ResourceLocation.parse(id.getAsString()));
            assertTrue(key.isEmpty() || !catalog.contains(key.get()), id + " should not be registered");
        }
    }

    private static CatalogEntry entry(ImportedGlyphCatalog catalog, String spell) {
        CatalogEntry entry = catalog.entry(ResourceLocation.parse(spell));
        assertNotNull(entry, spell + " is catalogued");
        return entry;
    }

    private static boolean rejected(ImportedGlyphCatalog catalog, String source) {
        return catalog.errors().stream().anyMatch(problem -> problem.startsWith(source + ": rejected"));
    }
}
