package com.otectus.arsnspells.glyph;

import com.google.gson.JsonParser;
import com.otectus.arsnspells.TestPaths;
import com.otectus.arsnspells.glyph.behavior.Dormancy;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.behavior.GlyphMappings;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The published examples in {@code examples/} are valid against this build, so the authoring guide is not fiction. */
class ExampleGlyphFilesTest {

    private static ImportedGlyphCatalog shipped() throws Exception {
        return ImportedGlyphCatalog.fromManifests(Map.of("irons_spellbooks",
            Files.readString(TestPaths.of("src/main/resources/ans_glyph_catalog/irons_spellbooks.json"))));
    }

    @Test
    void theExampleDataPackParsesAndAppliesAgainstTheShippedCatalog() throws Exception {
        Path dir = TestPaths.of("examples/iron-glyph-mapping/data/example_pack/ans_iron_glyphs");
        List<GlyphMappings.Source> sources = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.sorted().toList()) {
                sources.add(new GlyphMappings.Source(ResourceLocation.parse("example_pack:ans_iron_glyphs/" + file.getFileName()),
                    "example", JsonParser.parseString(Files.readString(file))));
            }
        }
        ImportedGlyphCatalog catalog = shipped();
        GlyphMappings.Result result = GlyphMappings.parse(sources, catalog);
        assertTrue(result.valid(), result.errors().toString());
        GlyphBehaviorSnapshot snapshot = GlyphBehaviorSnapshot.build(1, catalog, result, GlyphBehaviorSnapshot.Settings.DEFAULTS,
            new GlyphBehaviorSnapshot.LiveIrons() {
                @Override
                public boolean present() {
                    return true;
                }

                @Override
                public GlyphBehaviorSnapshot.LiveSpell spell(ResourceLocation spellId) {
                    var entry = catalog.entry(spellId);
                    return new GlyphBehaviorSnapshot.LiveSpell(true, true, entry.maxLevel(), entry.rarity());
                }
            });
        ResourceLocation firebolt = ResourceLocation.parse("irons_spellbooks:firebolt");
        assertEquals(1500, snapshot.variant(new ImportedGlyphKey(firebolt, 3)).pressSource());
        assertEquals(800, snapshot.variant(new ImportedGlyphKey(firebolt, 1)).pressSource());
        assertEquals(Dormancy.DISABLED_BY_PACK, snapshot.variant(new ImportedGlyphKey(firebolt, 10)).dormancy());
        assertTrue(snapshot.selfOnly(ResourceLocation.parse("irons_spellbooks:teleport")));
        assertEquals(Dormancy.DISABLED_BY_PACK, snapshot.variant(new ImportedGlyphKey(ResourceLocation.parse("irons_spellbooks:black_hole"), 1)).dormancy());
        assertEquals(Dormancy.DISABLED_BY_PACK, snapshot.variant(new ImportedGlyphKey(ResourceLocation.parse("irons_spellbooks:abyssal_shroud"), 1)).dormancy(),
            "the Eldritch school is denied");
    }

    @Test
    void theExampleAddonManifestRegistersItsLevels() throws Exception {
        String manifest = Files.readString(TestPaths.of("examples/iron-glyph-addon-manifest/ans_glyph_catalog/example_addon.json"));
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.fromManifests(Map.of("example_addon.jar/example_addon.json", manifest));
        assertTrue(catalog.errors().isEmpty(), catalog.errors().toString());
        assertNotNull(catalog.entry(ResourceLocation.parse("example_addon:frost_lance")));
        assertEquals(5, catalog.variantCount());
        assertEquals("ars_n_spells:iron/example_addon/frost_lance/level_5",
            new ImportedGlyphKey(ResourceLocation.parse("example_addon:frost_lance"), 5).partId().toString());
        assertEquals("ars_n_spells:glyph_iron/example_addon/frost_lance/level_5",
            new ImportedGlyphKey(ResourceLocation.parse("example_addon:frost_lance"), 5).itemId().toString());
    }
}
