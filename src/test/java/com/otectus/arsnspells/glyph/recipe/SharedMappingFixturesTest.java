package com.otectus.arsnspells.glyph.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the mapping-schema fixtures shared with the NeoForge build ({@code ans_shared_fixtures/mappings}),
 * so both loaders demonstrably read the canonical schema the same way: the same documents are
 * valid or invalid, allow the same glyphs and price the same terms.
 */
public class SharedMappingFixturesTest {
    /** The catalog every shared fixture family runs against ({@code ans_shared_fixtures/catalog.json}). */
    public static ImportedGlyphCatalog sharedCatalog() throws Exception {
        return ImportedGlyphCatalog.parse(text("/ans_shared_fixtures/catalog.json"));
    }

    public static String text(String path) throws Exception {
        try (InputStream in = SharedMappingFixturesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing fixture " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static JsonObject resource(String path) throws Exception {
        return JsonParser.parseString(text("/ans_shared_fixtures/mappings/" + path)).getAsJsonObject();
    }

    @TestFactory
    List<DynamicTest> sharedFixtures() throws Exception {
        ImportedGlyphCatalog catalog = sharedCatalog();
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement name : resource("index.json").getAsJsonArray("cases")) {
            JsonObject fixture = resource(name.getAsString() + ".json");
            tests.add(DynamicTest.dynamicTest(name.getAsString(), () -> check(catalog, fixture)));
        }
        assertTrue(tests.size() >= 16, "the shared fixture set shrank");
        return tests;
    }

    /** The fixture's mapping files as loader sources, in the order listed. */
    public static List<GlyphMappingSnapshot.Source> sources(JsonObject fixture) {
        List<GlyphMappingSnapshot.Source> sources = new ArrayList<>();
        if (fixture.has("files")) for (JsonElement file : fixture.getAsJsonArray("files")) {
            JsonObject f = file.getAsJsonObject();
            sources.add(new GlyphMappingSnapshot.Source(new ResourceLocation(f.get("id").getAsString()), f.get("pack").getAsString(),
                f.get("rank").getAsInt(), f.get("json")));
        }
        return sources;
    }

    private static void check(ImportedGlyphCatalog catalog, JsonObject fixture) {
        GlyphMappingSnapshot snapshot = GlyphMappingSnapshot.build(sources(fixture), catalog, 1);
        JsonObject expect = fixture.getAsJsonObject("expect");
        assertEquals(expect.get("valid").getAsBoolean(), snapshot.errors().isEmpty(), "validity; errors " + snapshot.errors());
        if (expect.has("errors_contain")) {
            String all = String.join("\n", snapshot.errors());
            for (JsonElement fragment : expect.getAsJsonArray("errors_contain"))
                assertTrue(all.contains(fragment.getAsString()), "errors should mention '" + fragment.getAsString() + "': " + all);
        }
        if (expect.has("failed_closed")) {
            TreeSet<String> closed = new TreeSet<>();
            snapshot.failedClosed().forEach(id -> closed.add(id.toString()));
            for (JsonElement spell : expect.getAsJsonArray("failed_closed")) assertTrue(closed.contains(spell.getAsString()), "fails closed: " + spell);
        }
        if (expect.has("allows")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("allows").entrySet())
            assertEquals(e.getValue().getAsBoolean(), snapshot.allows(def(catalog, e.getKey())), "allows " + e.getKey());
        if (expect.has("tier")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("tier").entrySet())
            assertEquals(e.getValue().getAsInt(), snapshot.tierOverride(def(catalog, e.getKey())), "tier " + e.getKey());
        if (expect.has("terms")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("terms").entrySet()) {
            JsonObject want = e.getValue().getAsJsonObject();
            GlyphMappingSnapshot.PressTerms terms = snapshot.pressTerms(def(catalog, e.getKey()), want.get("tier").getAsInt());
            assertEquals(want.get("source_cost").getAsInt(), terms.sourceCost(), "source " + e.getKey());
            assertEquals(want.get("duration_ticks").getAsInt(), terms.durationTicks(), "duration " + e.getKey());
            assertEquals(want.get("catalyst_count").getAsInt(), terms.catalystCount(), "catalysts " + e.getKey());
            if (want.has("catalyst_tag")) assertEquals(want.get("catalyst_tag").getAsString(), terms.catalystTag().toString());
        }
        if (expect.has("self_only")) for (JsonElement spell : expect.getAsJsonArray("self_only"))
            assertTrue(snapshot.selfOnly(new ResourceLocation(spell.getAsString())), "self only " + spell);
        if (expect.has("catalyst_tags")) {
            TreeSet<String> tags = new TreeSet<>();
            snapshot.catalystTags().forEach(tag -> tags.add(tag.toString()));
            TreeSet<String> want = new TreeSet<>();
            expect.getAsJsonArray("catalyst_tags").forEach(tag -> want.add(tag.getAsString()));
            assertEquals(want, tags);
        }
    }

    static ImportedGlyphDefinition def(ImportedGlyphCatalog catalog, String spellAtLevel) {
        String[] parts = spellAtLevel.split("@");
        return catalog.levelsOf(new ResourceLocation(parts[0])).get(Integer.parseInt(parts[1]) - 1);
    }
}
