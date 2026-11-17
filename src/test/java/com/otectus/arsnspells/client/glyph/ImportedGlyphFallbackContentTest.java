package com.otectus.arsnspells.client.glyph;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 3.4.1 review CATALOG-02: an identity a loose catalog registers gets a model, a name and (for an
 * icon outside Iron's standard directory) an atlas entry generated at run time, and only what no
 * installed file supplies is generated.
 */
class ImportedGlyphFallbackContentTest {
    private static final String ADDON = """
        {"schema_version": 2, "catalog": "addon", "spells": [
          {"spell": "addon:bolt", "name": "Bolt", "school": "addon:storm", "cast_type": "INSTANT",
           "levels": {"min": 1, "default_max": 3, "registered_max": 12}, "rarity": ["COMMON", "RARE", "EPIC"], "tier": [1, 2, 3],
           "adapter": "ars_n_spells:projectile", "icon": "addon:textures/spell/bolt.png"},
          {"spell": "addon:ward", "name": "Ward", "school": "addon:storm", "cast_type": "INSTANT",
           "levels": {"min": 2, "default_max": 2, "registered_max": 3}, "rarity": ["RARE"], "tier": [2],
           "adapter": "ars_n_spells:caster", "status": "unsupported", "adapter_version": 0, "icon": "addon:textures/gui/spell_icons/ward.png"}
        ]}""";

    private static ImportedGlyphCatalog catalog(boolean withBundled) throws IOException {
        List<ImportedGlyphCatalog.Source> sources = new java.util.ArrayList<>();
        if (withBundled) sources.add(new ImportedGlyphCatalog.Source("ars_n_spells/iron_glyph_catalog.json", bundled(), true));
        sources.add(new ImportedGlyphCatalog.Source("config/ars_n_spells/glyph_catalogs/addon.json", ADDON, false));
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.assemble(sources, Set.of());
        for (ImportedGlyphCatalog.SourceReport report : catalog.sources()) assertTrue(report.errors().isEmpty(), report.toString());
        return catalog;
    }

    private static String bundled() throws IOException {
        try (InputStream in = ImportedGlyphCatalog.class.getResourceAsStream(ImportedGlyphCatalog.RESOURCE)) {
            assertNotNull(in, "the bundled catalog is on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** What ANS's own jar ships, as an "installed file" probe over src/main/resources. */
    private static boolean shipped(String namespace, String path) {
        return Files.exists(Path.of("src/main/resources/assets", namespace, path));
    }

    @Test
    void aLooseCatalogGetsEveryModelAndNameButShippedIdentitiesGetNothing() throws IOException {
        Map<ResourceLocation, String> files = ImportedGlyphFallbackContent.build(catalog(true), ImportedGlyphFallbackContentTest::shipped);
        long models = files.keySet().stream().filter(id -> id.getPath().startsWith("models/")).count();
        assertEquals(12 + 2, models, "every addon identity (Bolt 1-12, Ward 2-3) and nothing the jar ships: " + files.keySet());
        assertFalse(files.containsKey(new ResourceLocation("ars_n_spells", "models/item/iron/irons_spellbooks/firebolt/level_1.json")),
            "a shipped model is never shadowed");
        JsonObject eleven = json(files.get(new ResourceLocation("ars_n_spells", "models/item/iron/addon/bolt/level_11.json")));
        assertEquals("forge:item/default", eleven.get("parent").getAsString());
        assertEquals("ars_n_spells:imported_glyph", eleven.get("loader").getAsString());
        assertEquals(11, eleven.get("level").getAsInt(), "the geometry draws no marker above X, so none is referenced");
        assertEquals("addon:spell/bolt", eleven.get("icon").getAsString(), "the catalogued icon, wherever it lives");
        assertFalse(files.containsKey(new ResourceLocation("ars_n_spells", "models/item/iron/addon/ward/level_1.json")),
            "a spell starting at level 2 has no level 1");
        JsonObject lang = json(files.get(ImportedGlyphFallbackContent.LANG));
        assertEquals("Bolt X", lang.get("ars_n_spells.glyph_name.iron/addon/bolt/level_10").getAsString());
        assertEquals("Bolt 12", lang.get("ars_n_spells.glyph_name.iron/addon/bolt/level_12").getAsString());
        assertEquals(14, lang.size());
        assertFalse(files.containsKey(ImportedGlyphFallbackContent.BLOCK_ATLAS),
            "an icon no installed file contains is not put in the atlas (the geometry shows ANS's rune instead)");
    }

    @Test
    void anIconOutsideTheStitchedDirectoryIsAddedToTheAtlasOnlyWhenInstalled() throws IOException {
        Map<ResourceLocation, String> files = ImportedGlyphFallbackContent.build(catalog(false),
            (namespace, path) -> "addon".equals(namespace) && path.startsWith("textures/"));
        JsonObject atlas = json(files.get(ImportedGlyphFallbackContent.BLOCK_ATLAS));
        assertEquals(1, atlas.getAsJsonArray("sources").size(), "only Bolt's icon is outside gui/spell_icons: " + atlas);
        JsonObject single = atlas.getAsJsonArray("sources").get(0).getAsJsonObject();
        assertEquals("single", single.get("type").getAsString());
        assertEquals("addon:spell/bolt", single.get("resource").getAsString());
    }

    @Test
    void aCatalogOnlyJarsModelsAreNeverShadowed() throws IOException {
        // The jar ships Bolt's models (and names) but the atlas entry for its icon is still needed.
        Map<ResourceLocation, String> files = ImportedGlyphFallbackContent.build(catalog(false),
            (namespace, path) -> path.startsWith("models/item/iron/addon/bolt/") || "addon".equals(namespace));
        assertTrue(files.keySet().stream().noneMatch(id -> id.getPath().startsWith("models/item/iron/addon/bolt/")), files.keySet().toString());
        assertTrue(files.containsKey(new ResourceLocation("ars_n_spells", "models/item/iron/addon/ward/level_2.json")));
        assertTrue(files.containsKey(ImportedGlyphFallbackContent.BLOCK_ATLAS));
    }

    @Test
    void theGeneratorReproducesEveryShippedGlyphModel() throws IOException {
        ImportedGlyphCatalog catalog = catalog(true);
        int compared = 0;
        for (ImportedGlyphDefinition definition : catalog.definitions()) {
            if (!definition.spell().getNamespace().equals("irons_spellbooks")) continue;
            ResourceLocation file = ImportedGlyphFallbackContent.modelFile(definition);
            Path shipped = Path.of("src/main/resources/assets", file.getNamespace(), file.getPath());
            assertTrue(Files.exists(shipped), "ANS ships " + shipped);
            assertEquals(json(Files.readString(shipped)),
                ImportedGlyphFallbackContent.model(definition, ImportedGlyphFallbackContent.iconSprite(definition)),
                "the runtime model of " + definition.key() + " is the shipped one");
            compared++;
        }
        assertEquals(1110, compared, "every shipped identity was compared");
    }

    private static JsonObject json(String text) {
        assertNotNull(text);
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
