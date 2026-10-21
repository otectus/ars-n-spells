package com.otectus.arsnspells.glyph;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ImportedGlyphCatalogTest {

    static String bundled() throws Exception {
        try (InputStream in = ImportedGlyphCatalog.class.getResourceAsStream(ImportedGlyphCatalog.RESOURCE)) {
            assertNotNull(in, "the catalog must ship in the jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void bundledCatalogCoversEveryRegisteredBaseSpell() throws Exception {
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.parse(bundled());
        // Iron's 1.20.1-3.16.3 registers 111 spells (777 levels); 3.15.0's 106 are a subset.
        assertEquals(111, catalog.spells().size());
        assertEquals(777, catalog.size());
        assertTrue(catalog.ironsBuilds().contains("1.20.1-3.15.0") && catalog.ironsBuilds().contains("1.20.1-3.16.3"));
        for (ResourceLocation spell : catalog.spells()) {
            var levels = catalog.levelsOf(spell);
            for (int i = 0; i < levels.size(); i++) assertEquals(i + 1, levels.get(i).level(), spell + " levels must be contiguous from 1");
        }
    }

    @Test
    void everyIdIsUniqueAndDecodesToItsOwnKey() throws Exception {
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.parse(bundled());
        Set<ResourceLocation> ids = new HashSet<>();
        for (ImportedGlyphDefinition definition : catalog.definitions()) {
            assertTrue(ids.add(definition.glyphId()), "duplicate id " + definition.glyphId());
            assertEquals(definition.key(), ImportedGlyphKey.decode(definition.glyphId()).orElseThrow());
            assertTrue(definition.glyphId().getPath().length() < 128, "id too long for comfort: " + definition.glyphId());
        }
    }

    @Test
    void neverCataloguesTheProxyPoolOrNone() throws Exception {
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.parse(bundled());
        for (ResourceLocation spell : catalog.spells()) {
            assertNotEquals("ars_n_spells", spell.getNamespace(), "ANS proxy spells are never imported: " + spell);
            assertNotEquals("none", spell.getPath());
        }
    }

    @Test
    void identityDigestIsStableAndIgnoresBehaviourFields() throws Exception {
        String json = bundled();
        String digest = ImportedGlyphCatalog.parse(json).identityDigest();
        assertEquals(digest, ImportedGlyphCatalog.parse(json).identityDigest());
        // Tier, adapter and names are behaviour; only the registered ids form the identity.
        String retiered = json.replace("\"adapter\": \"ars_n_spells:caster\"", "\"adapter\": \"ars_n_spells:aimed\"");
        assertNotEquals(json, retiered);
        assertEquals(digest, ImportedGlyphCatalog.parse(retiered).identityDigest());
    }

    @Test
    void tiersFollowIronsRarity() throws Exception {
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.parse(bundled());
        for (ImportedGlyphDefinition definition : catalog.definitions()) {
            assertEquals(GlyphAvailability.tierOfRarity(definition.defaultRarity()), definition.defaultTier(), definition.key().toString());
        }
    }

    @Test
    void rejectsMalformedCatalogs() {
        assertThrows(Exception.class, () -> ImportedGlyphCatalog.parse("{\"schema_version\":2}"));
        String duplicate = "{\"schema_version\":1,\"catalog_version\":\"t\",\"id_encoding\":\"ars_n_spells:iron/<spell namespace>/<spell path>/level_<n>\","
            + "\"irons_builds\":[],\"spells\":[" + spell("a:b") + "," + spell("a:b") + "]}";
        assertThrows(IllegalArgumentException.class, () -> ImportedGlyphCatalog.parse(duplicate));
        String badLevels = "{\"schema_version\":1,\"catalog_version\":\"t\",\"id_encoding\":\"ars_n_spells:iron/<spell namespace>/<spell path>/level_<n>\","
            + "\"irons_builds\":[],\"spells\":[" + spell("a:b").replace("\"max_level\":2", "\"max_level\":3") + "]}";
        assertThrows(IllegalArgumentException.class, () -> ImportedGlyphCatalog.parse(badLevels));
    }

    public static String spell(String id) {
        return "{\"spell\":\"" + id + "\",\"name\":\"B\",\"school\":\"irons_spellbooks:fire\",\"cast_type\":\"INSTANT\",\"max_level\":2,"
            + "\"rarity_by_level\":[\"COMMON\",\"LEGENDARY\"],\"tier_by_level\":[1,3],\"requires_learning\":false,"
            + "\"enabled_by_default\":true,\"adapter\":\"ars_n_spells:aimed\",\"irons_versions\":[\"x\"]}";
    }
}
