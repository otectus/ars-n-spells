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
        // Iron's 1.20.1-3.16.3 registers 111 spells (777 default levels); 3.15.0's 106 are a subset.
        assertEquals(111, catalog.spells().size());
        // 3.4.1: every spell reserves base levels up to 10, so 1,110 ids are registered.
        assertEquals(1110, catalog.size());
        assertEquals(777, catalog.definitions().stream().filter(d -> !d.reserved()).count());
        assertTrue(catalog.ironsBuilds().contains("1.20.1-3.15.0") && catalog.ironsBuilds().contains("1.20.1-3.16.3"));
        for (ResourceLocation spell : catalog.spells()) {
            var levels = catalog.levelsOf(spell);
            for (int i = 0; i < levels.size(); i++) assertEquals(i + 1, levels.get(i).level(), spell + " levels must be contiguous from 1");
            assertTrue(levels.size() >= 10, spell + " registers at least the level-10 envelope");
            assertEquals(levels.size(), catalog.registeredMax(spell));
        }
    }

    /** Every identity 3.4.0 published, re-derived from its committed catalog, is still registered. */
    @Test
    void everyPublishedIdentitySurvives() throws Exception {
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.parse(bundled());
        Set<ResourceLocation> ledger = ImportedGlyphCatalog.ledger();
        assertEquals(catalog.size(), ledger.size(), "the ledger lists exactly the registered ids");
        for (ResourceLocation id : ledger) assertTrue(catalog.byGlyphId(id).isPresent(), "published id lost: " + id);
        try (InputStream in = ImportedGlyphCatalogTest.class.getResourceAsStream("/released_identities_3.4.0.txt")) {
            assertNotNull(in, "the 3.4.0 identity fixture is missing");
            int count = 0;
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank()) continue;
                count++;
                assertTrue(ledger.contains(new ResourceLocation(line.strip())), "3.4.0 id dropped from the ledger: " + line);
            }
            assertEquals(777, count);
        }
        // A catalog that lost a published id must never boot.
        ResourceLocation extra = new ResourceLocation("ars_n_spells", "iron/irons_spellbooks/firebolt/level_11");
        Set<ResourceLocation> grown = new java.util.LinkedHashSet<>(ledger);
        grown.add(extra);
        var failure = assertThrows(ImportedGlyphCatalog.CatalogException.class,
            () -> ImportedGlyphCatalog.assemble(java.util.List.of(new ImportedGlyphCatalog.Source("base", bundled(), true)), grown));
        assertTrue(failure.getMessage().contains("level_11"), failure.getMessage());
    }

    @Test
    void reservedLevelsAreRegisteredDormantDefaults() throws Exception {
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.parse(bundled());
        var shroud = catalog.levelsOf(new ResourceLocation("irons_spellbooks:abyssal_shroud"));
        assertEquals(3, shroud.get(0).maxLevel());
        assertFalse(shroud.get(2).reserved());
        assertTrue(shroud.get(3).reserved(), "level 4 of a 3-level spell is reserved");
        assertEquals("LEGENDARY", shroud.get(9).defaultRarity());
        assertEquals(3, shroud.get(9).defaultTier());
    }

    @Test
    void optionalSourcesExtendButNeverReassignOrShrink() {
        String base = catalogOf(spell("a:b"), spell("a:c"));
        String extend = "{\"schema_version\":2,\"catalog\":\"pack\",\"spells\":[" + v2("a:b", "ars_n_spells:aimed", 2, 6, "supported") + "]}";
        String reassign = "{\"schema_version\":2,\"catalog\":\"bad\",\"spells\":[" + v2("a:c", "ars_n_spells:caster", 2, 2, "supported") + "]}";
        String addon = "{\"schema_version\":2,\"catalog\":\"addon\",\"spells\":[" + v2("other:b", "ars_n_spells:aimed", 2, 2, "retired") + "]}";
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.assemble(java.util.List.of(
            new ImportedGlyphCatalog.Source("base", base, true),
            new ImportedGlyphCatalog.Source("pack", extend, false),
            new ImportedGlyphCatalog.Source("bad", reassign, false),
            new ImportedGlyphCatalog.Source("addon", addon, false)), Set.of());
        assertEquals(6, catalog.registeredMax(new ResourceLocation("a:b")), "a later source may extend the envelope");
        assertEquals(2, catalog.registeredMax(new ResourceLocation("a:c")), "a reassigning source is rejected whole");
        assertEquals(GlyphFamily.AIMED, catalog.levelsOf(new ResourceLocation("a:c")).get(0).family());
        assertFalse(catalog.sources().get(2).errors().isEmpty(), "the rejection is reported");
        // Same path, different namespace: two spells, distinct ids, no collision.
        assertEquals(2, catalog.levelsOf(new ResourceLocation("other:b")).size());
        assertNotEquals(catalog.levelsOf(new ResourceLocation("a:b")).get(0).glyphId(), catalog.levelsOf(new ResourceLocation("other:b")).get(0).glyphId());
        assertTrue(catalog.levelsOf(new ResourceLocation("other:b")).get(0).retired(), "a retired identity is registered, dormant");
    }

    @Test
    void capOverflowIsRefusedNotTruncated() {
        StringBuilder huge = new StringBuilder("{\"schema_version\":2,\"catalog\":\"huge\",\"spells\":[");
        for (int i = 0; i < 50; i++) huge.append(i == 0 ? "" : ",").append(v2("huge:s" + i, "ars_n_spells:aimed", 2, 99, "supported"));
        huge.append("]}");
        ImportedGlyphCatalog withOptional = ImportedGlyphCatalog.assemble(java.util.List.of(
            new ImportedGlyphCatalog.Source("base", catalogOf(spell("a:b")), true),
            new ImportedGlyphCatalog.Source("huge", huge.toString(), false)), Set.of());
        assertEquals(2, withOptional.size(), "an over-cap optional source is rejected whole, the base stays complete");
        assertTrue(withOptional.sources().get(1).errors().get(0).contains("at most"), withOptional.sources().get(1).errors().toString());
        assertThrows(ImportedGlyphCatalog.CatalogException.class, () -> ImportedGlyphCatalog.assemble(java.util.List.of(
            new ImportedGlyphCatalog.Source("huge", huge.toString(), true)), Set.of()), "a required source over the cap stops startup");
    }

    static String catalogOf(String... spells) {
        return "{\"schema_version\":1,\"catalog_version\":\"t\",\"id_encoding\":\"ars_n_spells:iron/<spell namespace>/<spell path>/level_<n>\","
            + "\"irons_builds\":[],\"spells\":[" + String.join(",", spells) + "]}";
    }

    static String v2(String id, String adapter, int defaultMax, int registeredMax, String status) {
        StringBuilder rarity = new StringBuilder(), tier = new StringBuilder();
        for (int i = 0; i < defaultMax; i++) {
            rarity.append(i == 0 ? "" : ",").append("\"COMMON\"");
            tier.append(i == 0 ? "" : ",").append("1");
        }
        return "{\"spell\":\"" + id + "\",\"status\":\"" + status + "\",\"name\":\"B\",\"school\":\"irons_spellbooks:fire\","
            + "\"cast_type\":\"INSTANT\",\"levels\":{\"min\":1,\"default_max\":" + defaultMax + ",\"registered_max\":" + registeredMax + "},"
            + "\"rarity\":[" + rarity + "],\"tier\":[" + tier + "],\"requires_learning\":false,\"enabled_by_default\":true,"
            + "\"adapter\":\"" + adapter + "\",\"adapter_version\":1}";
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
        assertThrows(Exception.class, () -> ImportedGlyphCatalog.parse("{\"schema_version\":3,\"spells\":[]}"));
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
