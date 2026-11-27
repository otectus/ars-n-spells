package com.otectus.arsnspells.glyph.catalog;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 3.4.1 identity guarantees: the published ledger, the 3.4.0 ids, extension-only merging and refused cap overflow. */
class IdentityLedgerTest {

    static String shipped() throws Exception {
        return Files.readString(com.otectus.arsnspells.TestPaths.of("src/main/resources/ans_glyph_catalog/irons_spellbooks.json"));
    }

    @Test
    void everyPublishedIdentitySurvives() throws Exception {
        Set<ResourceLocation> ledger = ImportedGlyphCatalog.ledger();
        assertEquals(1110, ledger.size());
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.assemble(List.of(new ImportedGlyphCatalog.Manifest("ans", shipped(), true)), ledger);
        for (ResourceLocation id : ledger) assertNotNull(catalog.keyForPart(id), "published id lost: " + id);
        try (InputStream in = IdentityLedgerTest.class.getResourceAsStream("/released_identities_3.4.0.txt")) {
            assertNotNull(in);
            int count = 0;
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank()) continue;
                count++;
                assertTrue(ledger.contains(ResourceLocation.parse(line.strip())), "3.4.0 id dropped: " + line);
            }
            assertEquals(777, count);
        }
        Set<ResourceLocation> grown = new LinkedHashSet<>(ledger);
        grown.add(ResourceLocation.parse("ars_n_spells:iron/irons_spellbooks/firebolt/level_11"));
        assertThrows(ImportedGlyphCatalog.CatalogException.class,
            () -> ImportedGlyphCatalog.assemble(List.of(new ImportedGlyphCatalog.Manifest("ans", shipped(), true)), grown),
            "a catalog that lost a published id never boots");
    }

    @Test
    void aBrokenRequiredCatalogStopsStartupButAnOptionalOneIsRejectedWhole() throws Exception {
        assertThrows(ImportedGlyphCatalog.CatalogException.class, () -> ImportedGlyphCatalog.assemble(
            List.of(new ImportedGlyphCatalog.Manifest("ans", "{\"schema_version\": 2, \"spells\": [{\"spell\": \"x\"}]}", true)), Set.of()));
        ImportedGlyphCatalog withBadAddon = ImportedGlyphCatalog.assemble(List.of(
            new ImportedGlyphCatalog.Manifest("ans", shipped(), true),
            new ImportedGlyphCatalog.Manifest("addon", "{\"schema_version\": 2, \"spells\": [{\"spell\": \"irons_spellbooks:firebolt\", \"name\": \"F\","
                + " \"school\": \"irons_spellbooks:fire\", \"cast_type\": \"INSTANT\", \"levels\": {\"min\": 1, \"default_max\": 1, \"registered_max\": 12},"
                + " \"rarity\": [\"COMMON\"], \"tier\": [1], \"adapter\": \"ars_n_spells:self\", \"target\": \"self\"}]}", false)), Set.of());
        assertEquals(10, withBadAddon.entry(ResourceLocation.parse("irons_spellbooks:firebolt")).registeredMax(),
            "a manifest that would reassign the adapter changes nothing");
        assertFalse(withBadAddon.errors().isEmpty());
    }

    @Test
    void aLaterCatalogMayExtendTheEnvelopeWithTheSameAdapter() throws Exception {
        ImportedGlyphCatalog base = ImportedGlyphCatalog.assemble(List.of(new ImportedGlyphCatalog.Manifest("ans", shipped(), true)), Set.of());
        var firebolt = base.entry(ResourceLocation.parse("irons_spellbooks:firebolt"));
        String extension = "{\"schema_version\": 2, \"spells\": [{\"spell\": \"irons_spellbooks:firebolt\", \"name\": \"F\","
            + " \"school\": \"irons_spellbooks:fire\", \"cast_type\": \"INSTANT\", \"levels\": {\"min\": 1, \"default_max\": 1, \"registered_max\": 15},"
            + " \"rarity\": [\"COMMON\"], \"tier\": [1], \"adapter\": \"" + firebolt.adapter() + "\", \"target\": \"any\"}]}";
        ImportedGlyphCatalog extended = ImportedGlyphCatalog.assemble(List.of(new ImportedGlyphCatalog.Manifest("ans", shipped(), true),
            new ImportedGlyphCatalog.Manifest("pack", extension, false)), ImportedGlyphCatalog.ledger());
        assertEquals(15, extended.entry(ResourceLocation.parse("irons_spellbooks:firebolt")).registeredMax());
        assertEquals(base.variantCount() + 5, extended.variantCount());
        assertEquals(firebolt.maxLevel(), extended.entry(ResourceLocation.parse("irons_spellbooks:firebolt")).maxLevel(),
            "the extension changes the envelope, not the spell's defaults");
    }

    @Test
    void capOverflowIsRefusedNotTruncated() throws Exception {
        StringBuilder huge = new StringBuilder("{\"schema_version\": 2, \"spells\": [");
        for (int i = 0; i < 90; i++) {
            if (i > 0) huge.append(',');
            huge.append("{\"spell\": \"huge:s").append(i).append("\", \"name\": \"S\", \"school\": \"huge:x\", \"cast_type\": \"INSTANT\",")
                .append(" \"levels\": {\"min\": 1, \"default_max\": 1, \"registered_max\": 99}, \"rarity\": [\"COMMON\"], \"tier\": [1],")
                .append(" \"adapter\": \"ars_n_spells:self\", \"target\": \"self\"}");
        }
        huge.append("]}");
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.assemble(List.of(new ImportedGlyphCatalog.Manifest("ans", shipped(), true),
            new ImportedGlyphCatalog.Manifest("huge", huge.toString(), false)), Set.of());
        assertEquals(1110, catalog.variantCount(), "an over-cap optional manifest is rejected whole; the base stays complete");
        assertTrue(catalog.errors().get(0).contains("at most"), catalog.errors().toString());
        assertThrows(ImportedGlyphCatalog.CatalogException.class, () -> ImportedGlyphCatalog.assemble(
            List.of(new ImportedGlyphCatalog.Manifest("huge", huge.toString(), true)), Set.of()));
    }
}
