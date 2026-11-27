package com.otectus.arsnspells.glyph.behavior;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the effective-level fixtures shared with the Forge build ({@code ans_shared_fixtures/levels}):
 * given Iron's live state (maximum, enabled state, school) and optional mapping files, both loaders
 * report the same live maximum and the same dormancy for every glyph, in the same order of checks.
 */
class SharedLevelFixturesTest {
    static JsonObject resource(String name) throws Exception {
        return JsonParser.parseString(SharedMappingFixturesTest.text("/ans_shared_fixtures/levels/" + name)).getAsJsonObject();
    }

    @TestFactory
    List<DynamicTest> sharedFixtures() throws Exception {
        ImportedGlyphCatalog catalog = SharedMappingFixturesTest.sharedCatalog();
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement name : resource("index.json").getAsJsonArray("cases")) {
            JsonObject fixture = resource(name.getAsString() + ".json");
            tests.add(DynamicTest.dynamicTest(name.getAsString(), () -> check(catalog, fixture)));
        }
        assertTrue(tests.size() >= 9, "the shared level fixture set shrank");
        return tests;
    }

    private static void check(ImportedGlyphCatalog catalog, JsonObject fixture) {
        JsonObject liveState = fixture.getAsJsonObject("live");
        GlyphMappings.Result mappings = GlyphMappings.parse(SharedMappingFixturesTest.sources(fixture), catalog);
        assertTrue(mappings.valid(), "fixture mapping files are valid: " + mappings.errors());
        GlyphBehaviorSnapshot snapshot = GlyphBehaviorSnapshot.build(1, catalog, mappings, GlyphBehaviorSnapshot.Settings.DEFAULTS,
            new GlyphBehaviorSnapshot.LiveIrons() {
                @Override public boolean present() { return true; }
                @Override public GlyphBehaviorSnapshot.LiveSpell spell(ResourceLocation spellId, int registeredMax) {
                    return live(catalog.entry(spellId), liveState, registeredMax);
                }
            });
        assertFalse(snapshot.ironsConfigPending(), "a ready snapshot is never pending");
        JsonObject expect = fixture.getAsJsonObject("expect");
        if (expect.has("live_max")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("live_max").entrySet())
            assertEquals(e.getValue().getAsInt(), snapshot.spell(ResourceLocation.parse(e.getKey())).liveMax(), "live maximum of " + e.getKey());
        if (expect.has("status")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("status").entrySet()) {
            String[] parts = e.getKey().split("@");
            ImportedGlyphKey key = new ImportedGlyphKey(ResourceLocation.parse(parts[0]), Integer.parseInt(parts[1]));
            assertTrue(catalog.keys().contains(key), e.getKey() + " is a registered glyph");
            assertEquals(e.getValue().getAsString(), canonical(snapshot.variant(key).dormancy()), "status of " + e.getKey());
        }
    }

    static GlyphBehaviorSnapshot.LiveSpell live(CatalogEntry entry, JsonObject liveState, int registeredMax) {
        if (entry == null || !liveState.has(entry.spellId().toString())) return null;
        JsonObject state = liveState.getAsJsonObject(entry.spellId().toString());
        int max = state.get("max_level").getAsInt();
        List<String> rarities = new ArrayList<>();
        for (int level = 1; level <= registeredMax; level++) rarities.add(level >= max ? "LEGENDARY" : entry.rarityFor(level));
        ResourceLocation school = state.has("school") ? ResourceLocation.parse(state.get("school").getAsString()) : entry.school();
        boolean enabled = !state.has("enabled") || state.get("enabled").getAsBoolean();
        return new GlyphBehaviorSnapshot.LiveSpell(true, enabled, max, rarities, school, false);
    }

    /** The loader-neutral status names the fixtures use. */
    static String canonical(Dormancy dormancy) {
        return switch (dormancy) {
            case AVAILABLE -> "available";
            case LEVEL_ABOVE_SERVER_MAX -> "level_above_live_max";
            case SPELL_DISABLED_BY_IRONS -> "disabled_upstream";
            case SPELL_NOT_REGISTERED -> "spell_missing";
            case DISABLED_BY_PACK -> "disabled_by_pack";
            case RETIRED -> "retired";
            case UNSUPPORTED -> "unsupported";
            default -> dormancy.name().toLowerCase(java.util.Locale.ROOT);
        };
    }
}
