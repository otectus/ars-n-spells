package com.otectus.arsnspells.client.glyph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.TestPaths;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 3.4.1 review CATALOG-02: an identity a loose catalog registers gets a model and a name generated at
 * run time, only what no installed file supplies is generated, and nothing generated names a texture
 * or model ANS does not ship.
 */
class ImportedGlyphFallbackContentTest {
    private static final String ADDON = """
        {"schema_version": 2, "catalog": "addon", "spells": [
          {"spell": "addon:bolt", "name": "Bolt", "school": "addon:storm", "cast_type": "INSTANT",
           "levels": {"min": 1, "default_max": 3, "registered_max": 12}, "rarity": ["COMMON", "RARE", "EPIC"], "tier": [1, 2, 3],
           "adapter": "ars_n_spells:projectile", "target": "any", "icon": "addon:textures/spell/bolt.png"},
          {"spell": "addon:ward", "name": "Ward", "school": "addon:storm", "cast_type": "INSTANT",
           "levels": {"min": 2, "default_max": 2, "registered_max": 3}, "rarity": ["RARE"], "tier": [2],
           "adapter": "ars_n_spells:self", "target": "self", "status": "unsupported", "adapter_version": 0}
        ]}""";

    private static ImportedGlyphCatalog catalog(boolean withBundled) throws IOException {
        List<ImportedGlyphCatalog.Manifest> manifests = new ArrayList<>();
        if (withBundled) manifests.add(new ImportedGlyphCatalog.Manifest("ars_n_spells.jar/irons_spellbooks.json",
            Files.readString(TestPaths.of("src/main/resources/ans_glyph_catalog/irons_spellbooks.json")), true));
        manifests.add(new ImportedGlyphCatalog.Manifest("config/addon.json", ADDON, false));
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.assemble(manifests, Set.of());
        assertTrue(catalog.errors().isEmpty(), catalog.errors().toString());
        return catalog;
    }

    /** What ANS's own jar ships, as an "installed file" probe over src/main/resources. */
    private static boolean shipped(String namespace, String path) {
        return Files.exists(TestPaths.of("src/main/resources/assets", namespace, path));
    }

    @Test
    void aLooseCatalogGetsEveryModelAndNameButShippedIdentitiesGetNothing() throws IOException {
        Map<ResourceLocation, String> files = ImportedGlyphFallbackContent.build(catalog(true), ImportedGlyphFallbackContentTest::shipped);
        long models = files.keySet().stream().filter(id -> id.getPath().startsWith("models/")).count();
        assertEquals(12 + 2, models, "every addon identity (Bolt 1-12, Ward 2-3) and nothing the jar ships: " + files.keySet());
        assertFalse(files.containsKey(ResourceLocation.fromNamespaceAndPath("ars_n_spells",
            "models/item/glyph_iron/irons_spellbooks/firebolt/level_1.json")), "a shipped model is never shadowed");
        JsonObject ten = json(files.get(ResourceLocation.fromNamespaceAndPath("ars_n_spells", "models/item/glyph_iron/addon/bolt/level_10.json")));
        assertEquals("ars_n_spells:item/iron_glyph/level_10", ten.getAsJsonObject("textures").get("layer2").getAsString());
        JsonObject eleven = json(files.get(ResourceLocation.fromNamespaceAndPath("ars_n_spells", "models/item/glyph_iron/addon/bolt/level_11.json")));
        assertFalse(eleven.getAsJsonObject("textures").has("layer2"), "no level marker exists above X, so none is referenced");
        assertEquals("ars_n_spells:item/glyph_iron_dormant/unmarked",
            eleven.getAsJsonArray("overrides").get(0).getAsJsonObject().get("model").getAsString(), "dormant above X is still shown dormant");
        assertFalse(files.containsKey(ResourceLocation.fromNamespaceAndPath("ars_n_spells", "models/item/glyph_iron/addon/ward/level_1.json")),
            "a spell starting at level 2 has no level 1");
        JsonObject lang = json(files.get(ImportedGlyphFallbackContent.LANG));
        assertEquals("Bolt X", lang.get("ars_n_spells.glyph_name.iron/addon/bolt/level_10").getAsString());
        assertEquals("Bolt 12", lang.get("ars_n_spells.glyph_name.iron/addon/bolt/level_12").getAsString());
        assertEquals(14, lang.size());
        assertNoAbsentReference(files);
    }

    @Test
    void aCatalogOnlyJarsModelsAreNeverShadowed() throws IOException {
        Map<ResourceLocation, String> files = ImportedGlyphFallbackContent.build(catalog(false),
            (namespace, path) -> path.startsWith("models/item/glyph_iron/addon/bolt/"));
        assertTrue(files.keySet().stream().noneMatch(id -> id.getPath().startsWith("models/item/glyph_iron/addon/bolt/")), files.keySet().toString());
        assertTrue(files.containsKey(ResourceLocation.fromNamespaceAndPath("ars_n_spells", "models/item/glyph_iron/addon/ward/level_2.json")));
    }

    @Test
    void theGeneratorReproducesEveryShippedGlyphModel() throws IOException {
        int compared = 0;
        for (CatalogEntry entry : catalog(true).entries()) {
            if (!entry.spellId().getNamespace().equals("irons_spellbooks")) continue;
            for (ImportedGlyphKey key : entry.keys()) {
                ResourceLocation file = ImportedGlyphFallbackContent.modelFile(key);
                Path shipped = TestPaths.of("src/main/resources/assets", file.getNamespace(), file.getPath());
                assertTrue(Files.exists(shipped), "ANS ships " + shipped);
                assertEquals(json(Files.readString(shipped)), ImportedGlyphFallbackContent.model(key), "the runtime model of " + key + " is the shipped one");
                compared++;
            }
        }
        assertEquals(1110, compared, "every shipped identity was compared");
    }

    /** Every texture and model a generated model names is shipped by ANS, or is an atlas sprite ANS always stitches. */
    private static void assertNoAbsentReference(Map<ResourceLocation, String> files) {
        for (Map.Entry<ResourceLocation, String> file : files.entrySet()) {
            if (!file.getKey().getPath().startsWith("models/")) continue;
            JsonObject model = json(file.getValue());
            for (Map.Entry<String, JsonElement> texture : model.getAsJsonObject("textures").entrySet()) {
                ResourceLocation id = ResourceLocation.parse(texture.getValue().getAsString());
                if (id.getPath().startsWith("item/iron_glyph_icon/")) continue; // IronGlyphIconSource stitches one for every catalogued spell
                assertTrue(shipped(id.getNamespace(), "textures/" + id.getPath() + ".png"), file.getKey() + " names absent texture " + id);
            }
            for (JsonElement override : model.getAsJsonArray("overrides")) {
                ResourceLocation id = ResourceLocation.parse(override.getAsJsonObject().get("model").getAsString());
                assertTrue(shipped(id.getNamespace(), "models/" + id.getPath() + ".json"), file.getKey() + " names absent model " + id);
            }
        }
    }

    private static JsonObject json(String text) {
        assertNotNull(text);
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
