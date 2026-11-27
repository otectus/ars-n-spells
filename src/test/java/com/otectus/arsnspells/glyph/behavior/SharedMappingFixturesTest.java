package com.otectus.arsnspells.glyph.behavior;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
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
 * Runs the mapping-schema fixtures shared with the Forge build ({@code ans_shared_fixtures/mappings},
 * kept identical by {@code tools/verify_loader_parity.py}), so both loaders demonstrably read the
 * canonical schema the same way. Iron's live maximum is set to each spell's registered maximum here,
 * so only the data-pack layer decides availability, as the fixtures intend.
 */
public class SharedMappingFixturesTest {
    /** The catalog every shared fixture family runs against ({@code ans_shared_fixtures/catalog.json}), as a required manifest. */
    public static ImportedGlyphCatalog sharedCatalog() throws Exception {
        return ImportedGlyphCatalog.assemble(List.of(new ImportedGlyphCatalog.Manifest("fixture", text("/ans_shared_fixtures/catalog.json"), true)),
            java.util.Set.of());
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
        assertTrue(catalog.errors().isEmpty(), catalog.errors().toString());
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement name : resource("index.json").getAsJsonArray("cases")) {
            JsonObject fixture = resource(name.getAsString() + ".json");
            tests.add(DynamicTest.dynamicTest(name.getAsString(), () -> check(catalog, fixture)));
        }
        assertTrue(tests.size() >= 16, "the shared fixture set shrank");
        return tests;
    }

    /** The fixture's mapping files as loader sources, in the order listed. */
    public static List<GlyphMappings.Source> sources(JsonObject fixture) {
        List<GlyphMappings.Source> sources = new ArrayList<>();
        if (fixture.has("files")) for (JsonElement file : fixture.getAsJsonArray("files")) {
            JsonObject f = file.getAsJsonObject();
            sources.add(new GlyphMappings.Source(ResourceLocation.parse(f.get("id").getAsString()), f.get("pack").getAsString(),
                f.get("rank").getAsInt(), f.get("json")));
        }
        return sources;
    }

    private static void check(ImportedGlyphCatalog catalog, JsonObject fixture) {
        GlyphMappings.Result mappings = GlyphMappings.parse(sources(fixture), catalog);
        GlyphBehaviorSnapshot snapshot = GlyphBehaviorSnapshot.build(1, catalog, mappings, GlyphBehaviorSnapshot.Settings.DEFAULTS,
            new GlyphBehaviorSnapshot.LiveIrons() {
                @Override public boolean present() { return true; }
                @Override public GlyphBehaviorSnapshot.LiveSpell spell(ResourceLocation spellId, int registeredMax) {
                    var entry = catalog.entry(spellId);
                    List<String> rarities = new ArrayList<>();
                    for (int level = 1; level <= registeredMax; level++) rarities.add(entry.rarityFor(level));
                    return new GlyphBehaviorSnapshot.LiveSpell(true, true, registeredMax, rarities, entry.school(), false);
                }
            });
        JsonObject expect = fixture.getAsJsonObject("expect");
        assertEquals(expect.get("valid").getAsBoolean(), mappings.valid(), "validity; errors " + mappings.errors());
        if (expect.has("errors_contain")) {
            String all = String.join("\n", mappings.errors());
            for (JsonElement fragment : expect.getAsJsonArray("errors_contain"))
                assertTrue(all.contains(fragment.getAsString()), "errors should mention '" + fragment.getAsString() + "': " + all);
        }
        if (expect.has("failed_closed")) {
            TreeSet<String> closed = new TreeSet<>();
            mappings.failedClosed().forEach(id -> closed.add(id.toString()));
            for (JsonElement spell : expect.getAsJsonArray("failed_closed")) assertTrue(closed.contains(spell.getAsString()), "fails closed: " + spell);
        }
        if (expect.has("allows")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("allows").entrySet())
            assertEquals(e.getValue().getAsBoolean(), snapshot.variant(key(e.getKey())).available(), "allows " + e.getKey()
                + " (" + snapshot.variant(key(e.getKey())).dormancy() + ")");
        if (expect.has("tier")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("tier").entrySet())
            assertEquals(e.getValue().getAsInt(), snapshot.variant(key(e.getKey())).tier(), "tier " + e.getKey());
        if (expect.has("terms")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("terms").entrySet()) {
            JsonObject want = e.getValue().getAsJsonObject();
            VariantBehavior behavior = snapshot.variant(key(e.getKey()));
            assertEquals(want.get("tier").getAsInt(), behavior.tier(), "priced tier " + e.getKey());
            assertEquals(want.get("source_cost").getAsInt(), behavior.pressSource(), "source " + e.getKey());
            assertEquals(want.get("duration_ticks").getAsInt(), behavior.pressTicks(), "duration " + e.getKey());
            assertEquals(want.get("catalyst_count").getAsInt(), behavior.pressGems(), "catalysts " + e.getKey());
            if (want.has("catalyst_tag")) assertEquals(want.get("catalyst_tag").getAsString(), behavior.catalyst());
        }
        if (expect.has("self_only")) for (JsonElement spell : expect.getAsJsonArray("self_only"))
            assertTrue(snapshot.selfOnly(ResourceLocation.parse(spell.getAsString())), "self only " + spell);
        if (expect.has("catalyst_tags")) {
            TreeSet<String> want = new TreeSet<>();
            expect.getAsJsonArray("catalyst_tags").forEach(tag -> want.add(tag.getAsString()));
            assertEquals(want, new TreeSet<>(snapshot.catalystTags()));
        }
    }

    static ImportedGlyphKey key(String spellAtLevel) {
        String[] parts = spellAtLevel.split("@");
        return new ImportedGlyphKey(ResourceLocation.parse(parts[0]), Integer.parseInt(parts[1]));
    }
}
