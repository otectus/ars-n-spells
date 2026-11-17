package com.otectus.arsnspells.glyph.behavior;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import com.otectus.arsnspells.glyph.recipe.SharedMappingFixturesTest;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the effective-level fixtures shared with the NeoForge build ({@code ans_shared_fixtures/levels}):
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
        GlyphLiveSnapshot live = GlyphLiveSnapshot.of(catalog, GlyphLiveSnapshot.Readiness.READY, spell -> facts(catalog, spell, liveState));
        GlyphMappingSnapshot mappings = GlyphMappingSnapshot.build(SharedMappingFixturesTest.sources(fixture), catalog, 1);
        assertTrue(mappings.errors().isEmpty(), "fixture mapping files are valid: " + mappings.errors());
        JsonObject expect = fixture.getAsJsonObject("expect");
        if (expect.has("live_max")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("live_max").entrySet())
            assertEquals(e.getValue().getAsInt(), live.facts(new ResourceLocation(e.getKey())).maxLevel(), "live maximum of " + e.getKey());
        if (expect.has("status")) for (Map.Entry<String, JsonElement> e : expect.getAsJsonObject("status").entrySet()) {
            String[] parts = e.getKey().split("@");
            ImportedGlyphDefinition definition = catalog.byKey(new ImportedGlyphKey(new ResourceLocation(parts[0]), Integer.parseInt(parts[1])))
                .orElseThrow(() -> new AssertionError(e.getKey() + " is not a registered glyph"));
            GlyphAvailability.Status status = GlyphAvailability.decide(definition, true, true, live, mappings);
            assertEquals(e.getValue().getAsString(), canonical(status.reason()), "status of " + e.getKey());
            assertFalse(status.provisional(), "a READY snapshot is never provisional");
        }
    }

    static LiveSpellFacts facts(ImportedGlyphCatalog catalog, ResourceLocation spell, JsonObject liveState) {
        List<ImportedGlyphDefinition> levels = catalog.levelsOf(spell);
        ImportedGlyphDefinition first = levels.get(0);
        if (!liveState.has(spell.toString())) return LiveSpellFacts.missing(first.school());
        JsonObject state = liveState.getAsJsonObject(spell.toString());
        int max = state.get("max_level").getAsInt();
        List<String> rarities = new ArrayList<>();
        for (ImportedGlyphDefinition level : levels) rarities.add(level.level() >= max ? "LEGENDARY" : level.defaultRarity());
        ResourceLocation school = state.has("school") ? new ResourceLocation(state.get("school").getAsString()) : first.school();
        boolean enabled = !state.has("enabled") || state.get("enabled").getAsBoolean();
        return new LiveSpellFacts(true, enabled, 1, max, school, false, rarities);
    }

    /** The loader-neutral status names the fixtures use. */
    static String canonical(GlyphAvailability.Reason reason) {
        return switch (reason) {
            case AVAILABLE -> "available";
            case LEVEL_UNSUPPORTED -> "level_above_live_max";
            case DISABLED_UPSTREAM -> "disabled_upstream";
            case SPELL_MISSING -> "spell_missing";
            case DISABLED_BY_DATAPACK -> "disabled_by_pack";
            case RETIRED -> "retired";
            case UNSUPPORTED -> "unsupported";
            default -> reason.name().toLowerCase(java.util.Locale.ROOT);
        };
    }
}
