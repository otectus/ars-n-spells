package com.otectus.arsnspells.glyph;

import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.catalog.RetainedCatalogs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 3.4.1 review SAVE-01 on NeoForge: an accepted package's identities survive its removal (retained,
 * dormant) and return unchanged when it is reinstalled; a world records what it used and where it
 * came from, keeps data a later build wrote, and reports exactly what is missing. The Forge build's
 * test of the same name covers the same rules.
 */
class IdentityPersistenceTest {
    private static final ResourceLocation BOLT = ResourceLocation.fromNamespaceAndPath("addon", "bolt");
    private static final String BASE = """
        {"schema_version": 2, "catalog_version": "base-1", "spells": [
          {"spell": "irons_spellbooks:firebolt", "name": "Firebolt", "school": "irons_spellbooks:fire", "cast_type": "INSTANT",
           "levels": {"min": 1, "default_max": 2, "registered_max": 3}, "rarity": ["COMMON", "RARE"], "tier": [1, 2],
           "adapter": "ars_n_spells:projectile", "target": "any"}]}""";
    private static final String ADDON_V1 = """
        {"schema_version": 2, "catalog": "addon", "catalog_version": "1.0.0", "spells": [
          {"spell": "addon:bolt", "name": "Bolt", "school": "addon:storm", "cast_type": "INSTANT",
           "levels": {"min": 1, "default_max": 3, "registered_max": 12}, "rarity": ["COMMON", "RARE", "EPIC"], "tier": [1, 2, 3],
           "adapter": "ars_n_spells:projectile", "target": "any"}]}""";
    private static final String ADDON_V2 = ADDON_V1.replace("\"1.0.0\"", "\"2.0.0\"").replace("\"registered_max\": 12", "\"registered_max\": 14");

    @TempDir
    Path archive;

    private static ImportedGlyphCatalog.Manifest base() {
        return new ImportedGlyphCatalog.Manifest("ars_n_spells.jar/irons_spellbooks.json", BASE, true);
    }

    private static ImportedGlyphCatalog.Manifest live(String json) {
        return new ImportedGlyphCatalog.Manifest("config/addon.json", json, false);
    }

    private ImportedGlyphCatalog start(ImportedGlyphCatalog.Manifest... live) {
        List<ImportedGlyphCatalog.Manifest> liveSources = new ArrayList<>(List.of(live));
        List<ImportedGlyphCatalog.Manifest> manifests = new ArrayList<>();
        manifests.add(base());
        manifests.addAll(liveSources);
        manifests.addAll(RetainedCatalogs.sources(archive, liveSources));
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.assemble(manifests, Set.of());
        RetainedCatalogs.retain(archive, liveSources, catalog);
        return catalog;
    }

    private List<String> archived() throws IOException {
        try (Stream<Path> files = Files.list(archive)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void anAcceptedPackageIsRetainedOnceAndSurvivesItsRemovalDormant() throws IOException {
        ImportedGlyphCatalog installed = start(live(ADDON_V1));
        assertEquals(1, archived().size(), "the accepted package is retained");
        start(live(ADDON_V1));
        assertEquals(1, archived().size(), "retaining is idempotent");
        assertEquals(ADDON_V1, Files.readString(archive.resolve(archived().get(0))), "byte for byte");

        ImportedGlyphCatalog removed = start();
        assertEquals(installed.keys(), removed.keys(), "every identity stays registered with the package gone");
        CatalogEntry dormant = removed.entry(BOLT);
        assertTrue(dormant.retired() && !dormant.executable(), "a retained identity is dormant: " + dormant);
        assertTrue(dormant.sourceCatalog().startsWith(RetainedCatalogs.SOURCE_PREFIX), dormant.sourceCatalog());

        ImportedGlyphCatalog reinstalled = start(live(ADDON_V1));
        assertEquals(installed.hash(), reinstalled.hash(), "a later correct start has an identical catalog");
        assertTrue(reinstalled.entry(BOLT).executable(), "and the package's contract again");
    }

    @Test
    void aRetainedCopyNeverReplacesALiveContractAndOnlyAddsIdentities() throws IOException {
        start(live(ADDON_V2));
        ImportedGlyphCatalog downgraded = start(live(ADDON_V1));
        assertEquals(14, downgraded.entry(BOLT).registeredMax(), "the newer package's extra levels stay registered");
        assertTrue(downgraded.entry(BOLT).executable(), "the live package's contract is the one in force");
        assertTrue(downgraded.errors().isEmpty(), downgraded.errors().toString());
        assertEquals(2, archived().size(), "both versions are retained: " + archived());
    }

    @Test
    void aCorruptRetainedCopyIsNotReadAndIsReported() throws IOException {
        start(live(ADDON_V1));
        Files.writeString(archive.resolve(archived().get(0)), ADDON_V1.replace("Bolt", "Bolt!"), StandardCharsets.UTF_8);
        ImportedGlyphCatalog removed = start();
        assertNull(removed.entry(BOLT), "an edited copy is corrupt and registers nothing");
        assertTrue(RetainedCatalogs.lastProblems().stream().anyMatch(p -> p.contains("does not match its name")), RetainedCatalogs.lastProblems().toString());
    }

    @Test
    void theWorldRecordKeepsRangesProvenanceAndDataALaterBuildWrote() {
        ImportedGlyphCatalog catalog = start(live(ADDON_V1));
        CompoundTag legacy = new CompoundTag();
        CompoundTag spells = new CompoundTag();
        spells.putInt("addon:bolt", 5);
        CompoundTag future = new CompoundTag();
        future.putInt("min", 1);
        future.putInt("max", 4);
        future.putString("future_field", "kept");
        spells.put("addon:other", future);
        legacy.put("spells", spells);
        legacy.putInt("schema", 7);
        legacy.putString("written_by", "a later build");
        GlyphIdentityRecord record = GlyphIdentityRecord.load(legacy);
        assertArrayEquals(new int[]{1, 5}, record.ranges().get("addon:bolt"), "schema 1 reads as levels 1..max");
        assertTrue(record.absorb(catalog));
        assertArrayEquals(new int[]{1, 12}, record.ranges().get("addon:bolt"));
        CompoundTag saved = record.save(new CompoundTag());
        assertEquals(7, saved.getInt("schema"), "a newer schema number is never lowered");
        assertEquals("a later build", saved.getString("written_by"));
        assertEquals("kept", saved.getCompound("spells").getCompound("addon:other").getString("future_field"));
        CompoundTag bolt = saved.getCompound("spells").getCompound("addon:bolt");
        assertEquals("addon", bolt.getString("catalog"));
        assertEquals("1.0.0", bolt.getString("version"));
        assertEquals("config/addon.json", bolt.getList("sources", 8).getString(0));

        ImportedGlyphCatalog without = ImportedGlyphCatalog.assemble(List.of(base()), Set.of());
        List<GlyphIdentityRecord.Missing> missing = GlyphIdentityRecord.load(saved).missingFrom(without);
        GlyphIdentityRecord.Missing lost = missing.stream().filter(m -> m.spell().equals(BOLT)).findFirst().orElseThrow();
        assertEquals(1, lost.fromLevel());
        assertEquals(12, lost.toLevel());
        String report = GlyphIdentityRecord.report(missing);
        assertTrue(report.contains("addon:bolt levels 1-12 from catalog 'addon' version 1.0.0") && report.contains("config/addon.json")
            && report.contains(GlyphIdentityRecord.ACCEPT_MISSING_PROPERTY), report);
        GlyphIdentityRecord reloaded = GlyphIdentityRecord.load(saved);
        reloaded.abandon(missing);
        assertTrue(GlyphIdentityRecord.load(reloaded.save(new CompoundTag())).missingFrom(without).isEmpty(), "an accepted loss stops refusing");
    }
}
