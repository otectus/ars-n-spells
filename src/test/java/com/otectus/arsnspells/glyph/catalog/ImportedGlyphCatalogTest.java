package com.otectus.arsnspells.glyph.catalog;

import com.otectus.arsnspells.glyph.GlyphTestFixtures;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Startup catalog parsing: identities, determinism, and refusal of anything malformed or unimplemented. */
class ImportedGlyphCatalogTest {

    @Test
    void supportedSpellsGetOneIdentityPerLevel() {
        ImportedGlyphCatalog catalog = GlyphTestFixtures.catalog();
        assertTrue(catalog.errors().isEmpty(), catalog.errors().toString());
        assertEquals(4, catalog.entries().size());
        // 3.4.1: an unsupported spell keeps its identities registered (dormant), so a status change
        // can never remove an id that a world may already store.
        int storm = catalog.entry(GlyphTestFixtures.STORM).registeredMax() - catalog.entry(GlyphTestFixtures.STORM).minLevel() + 1;
        assertEquals(3 + 2 + 1 + storm, catalog.variantCount(), "every catalogued level of every status is registered");
        assertEquals(new ImportedGlyphKey(GlyphTestFixtures.FIREBOLT, 2),
            catalog.keyForPart(new ImportedGlyphKey(GlyphTestFixtures.FIREBOLT, 2).partId()));
        assertEquals(new ImportedGlyphKey(GlyphTestFixtures.STORM, 1),
            catalog.keyForPart(new ImportedGlyphKey(GlyphTestFixtures.STORM, 1).partId()), "the unsupported spell is registered, dormant");
    }

    @Test
    void hashIsDeterministicAndContentSensitive() {
        String manifest = GlyphTestFixtures.manifest();
        assertEquals(ImportedGlyphCatalog.fromManifests(Map.of("a", manifest)).hash(),
            ImportedGlyphCatalog.fromManifests(Map.of("b", manifest)).hash(), "the source name does not change the content hash");
        String changed = manifest.replace("\"max_level\": 3, \"rarity\": [\"COMMON\", \"UNCOMMON\", \"RARE\"], \"default_tier\": [1, 2, 2]",
            "\"max_level\": 2, \"rarity\": [\"COMMON\", \"UNCOMMON\"], \"default_tier\": [1, 2]");
        assertNotEquals(ImportedGlyphCatalog.fromManifests(Map.of("a", manifest)).hash(),
            ImportedGlyphCatalog.fromManifests(Map.of("a", changed)).hash(), "a different level range is a different catalog");
    }

    @Test
    void malformedEntriesAndUnknownAdaptersAreRefused() {
        String bad = """
            {"schema_version": 1, "spells": [
              {"spell": "addon:boom", "name": "Boom", "school": "addon:fire", "cast_type": "INSTANT", "min_level": 1,
               "max_level": 1, "rarity": ["COMMON"], "default_tier": [1], "adapter": "addon:custom", "target": "self"},
              {"spell": "addon:gap", "name": "Gap", "school": "addon:fire", "cast_type": "INSTANT", "min_level": 1,
               "max_level": 2, "rarity": ["COMMON"], "default_tier": [1], "adapter": "ars_n_spells:self", "target": "self"},
              {"spell": "addon:tier", "name": "Tier", "school": "addon:fire", "cast_type": "INSTANT", "min_level": 1,
               "max_level": 1, "rarity": ["COMMON"], "default_tier": [4], "adapter": "ars_n_spells:self", "target": "self"}
            ]}""";
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.fromManifests(Map.of("addon", bad));
        assertEquals(0, catalog.entries().size(), "an invalid optional manifest is rejected whole");
        assertEquals(1, catalog.errors().size(), catalog.errors().toString());
        String reasons = catalog.errors().get(0);
        // 3.4.1 review CATALOG-02: strict messages name the field and the value found.
        assertTrue(reasons.contains("addon:custom") && reasons.contains("'rarity'") && reasons.contains("'default_tier' entry")
            && reasons.contains("(found 4)"), reasons);
        ImportedGlyphCatalog future = ImportedGlyphCatalog.fromManifests(Map.of("future", "{\"schema_version\": 3, \"spells\": []}"));
        assertEquals(1, future.errors().size(), "a future schema is reported and registers nothing");
    }

    @Test
    void aDuplicateSpellKeepsTheFirstManifestInSortedOrder() {
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.fromManifests(Map.of("b_second", GlyphTestFixtures.manifest(),
            "a_first", GlyphTestFixtures.manifest()));
        assertEquals("a_first", catalog.entry(GlyphTestFixtures.FIREBOLT).sourceCatalog());
        // 3.4.1: re-declaring a spell with the same adapter is harmless (it may only extend the
        // registered levels); only a reassigning declaration is an error.
        assertTrue(catalog.errors().isEmpty(), "an identical re-declaration is not a conflict: " + catalog.errors());
    }

    @Test
    void theShippedCatalogParsesCleanly() throws Exception {
        String shipped = Files.readString(com.otectus.arsnspells.TestPaths.of("src/main/resources/ans_glyph_catalog/irons_spellbooks.json"));
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.fromManifests(Map.of("irons_spellbooks", shipped));
        assertTrue(catalog.errors().isEmpty(), catalog.errors().toString());
        assertEquals(111, catalog.entries().size());
        // 777 default levels plus reserved levels up to 10 for every spell.
        assertEquals(1110, catalog.variantCount());
        for (CatalogEntry entry : catalog.entries()) assertTrue(entry.registeredMax() >= 10, entry.spellId() + " reserves the level-10 envelope");
    }
}
