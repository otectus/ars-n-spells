package com.otectus.arsnspells.glyph;

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
 * 3.4.1 review SAVE-01: an accepted package's identities survive its removal (retained, dormant) and
 * return unchanged when it is reinstalled; a world records what it used and where it came from, keeps
 * data a later build wrote, and reports exactly what is missing.
 */
class IdentityPersistenceTest {
    private static final ResourceLocation BOLT = new ResourceLocation("addon", "bolt");
    private static final String BASE = """
        {"schema_version": 2, "catalog_version": "base-1", "spells": [
          {"spell": "irons_spellbooks:firebolt", "name": "Firebolt", "school": "irons_spellbooks:fire", "cast_type": "INSTANT",
           "levels": {"min": 1, "default_max": 2, "registered_max": 3}, "rarity": ["COMMON", "RARE"], "tier": [1, 2],
           "adapter": "ars_n_spells:projectile"}]}""";
    private static final String ADDON_V1 = """
        {"schema_version": 2, "catalog": "addon", "catalog_version": "1.0.0", "spells": [
          {"spell": "addon:bolt", "name": "Bolt", "school": "addon:storm", "cast_type": "INSTANT",
           "levels": {"min": 1, "default_max": 3, "registered_max": 12}, "rarity": ["COMMON", "RARE", "EPIC"], "tier": [1, 2, 3],
           "adapter": "ars_n_spells:projectile"}]}""";
    private static final String ADDON_V2 = ADDON_V1.replace("\"1.0.0\"", "\"2.0.0\"").replace("\"registered_max\": 12", "\"registered_max\": 14");

    @TempDir
    Path archive;

    private static ImportedGlyphCatalog.Source base() {
        return new ImportedGlyphCatalog.Source("ars_n_spells/iron_glyph_catalog.json", BASE, true);
    }

    private static ImportedGlyphCatalog.Source live(String json) {
        return new ImportedGlyphCatalog.Source("config/ars_n_spells/glyph_catalogs/addon.json", json, false);
    }

    /** One startup: required catalog, live sources, then the retained copies; then retain what was accepted. */
    private ImportedGlyphCatalog start(ImportedGlyphCatalog.Source... live) {
        List<ImportedGlyphCatalog.Source> liveSources = List.of(live);
        List<ImportedGlyphCatalog.Source> sources = new ArrayList<>();
        sources.add(base());
        sources.addAll(liveSources);
        sources.addAll(RetainedCatalogs.sources(archive, liveSources));
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.assemble(sources, Set.of());
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
        assertTrue(archived().get(0).startsWith("addon@") && archived().get(0).endsWith(".json"), archived().toString());
        start(live(ADDON_V1));
        assertEquals(1, archived().size(), "retaining is idempotent");
        assertEquals(ADDON_V1, Files.readString(archive.resolve(archived().get(0))), "byte for byte");

        ImportedGlyphCatalog removed = start();
        assertEquals(installed.identityDigest(), removed.identityDigest(), "every identity stays registered with the package gone");
        ImportedGlyphDefinition dormant = removed.levelsOf(BOLT).get(0);
        assertTrue(dormant.retired() && !dormant.executable(), "a retained identity is dormant: " + dormant);
        assertTrue(dormant.source().startsWith(RetainedCatalogs.SOURCE_PREFIX), dormant.source());
        assertEquals(12, removed.registeredMax(BOLT));

        ImportedGlyphCatalog reinstalled = start(live(ADDON_V1));
        assertEquals(installed.identityDigest(), reinstalled.identityDigest(), "a later correct start has identical identities");
        assertTrue(reinstalled.levelsOf(BOLT).get(0).executable(), "and the package's contract again");
        assertEquals(installed.levelsOf(BOLT), reinstalled.levelsOf(BOLT), "identical definitions once the package is back");
    }

    @Test
    void aRetainedCopyNeverReplacesALiveContractAndOnlyAddsIdentities() throws IOException {
        start(live(ADDON_V2));
        ImportedGlyphCatalog downgraded = start(live(ADDON_V1));
        assertEquals(14, downgraded.registeredMax(BOLT), "the newer package's extra levels stay registered");
        ImportedGlyphDefinition first = downgraded.levelsOf(BOLT).get(0);
        assertTrue(first.executable(), "the live package's contract is the one in force");
        assertFalse(first.source().startsWith(RetainedCatalogs.SOURCE_PREFIX), first.source());
        assertEquals(2, archived().size(), "both versions are retained: " + archived());
    }

    @Test
    void aCorruptRetainedCopyIsNotReadAndIsReported() throws IOException {
        start(live(ADDON_V1));
        Path copy = archive.resolve(archived().get(0));
        Files.writeString(copy, ADDON_V1.replace("Bolt", "Bolt!"), StandardCharsets.UTF_8);
        ImportedGlyphCatalog removed = start();
        assertTrue(removed.levelsOf(BOLT).isEmpty(), "an edited copy is corrupt and registers nothing");
        assertTrue(RetainedCatalogs.lastProblems().stream().anyMatch(p -> p.contains("does not match its name")), RetainedCatalogs.lastProblems().toString());
    }

    @Test
    void aSchemaOneManifestIsRetainedAsIdentities() {
        String legacy = """
            {"schema_version": 1, "spells": [{"spell": "addon:old", "name": "Old", "school": "addon:storm", "cast_type": "INSTANT",
              "min_level": 1, "max_level": 2, "rarity_by_level": ["COMMON", "RARE"], "tier_by_level": [1, 2], "adapter": "ars_n_spells:caster"}]}""";
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.assemble(List.of(base(),
            new ImportedGlyphCatalog.Source("retained/old@0000000000000000.json", RetainedCatalogs.identityOnly(legacy), false)), Set.of());
        assertTrue(catalog.sources().stream().allMatch(r -> r.errors().isEmpty()), catalog.sources().toString());
        assertEquals(2, catalog.registeredMax(new ResourceLocation("addon", "old")));
        assertTrue(catalog.levelsOf(new ResourceLocation("addon", "old")).get(0).retired());
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
        assertEquals("a later build", saved.getString("written_by"), "unknown root data survives");
        assertEquals("kept", saved.getCompound("spells").getCompound("addon:other").getString("future_field"), "unknown entry data survives");
        CompoundTag bolt = saved.getCompound("spells").getCompound("addon:bolt");
        assertEquals("addon", bolt.getString("catalog"));
        assertEquals("1.0.0", bolt.getString("version"));
        assertEquals("config/ars_n_spells/glyph_catalogs/addon.json", bolt.getList("sources", 8).getString(0));

        GlyphIdentityRecord reloaded = GlyphIdentityRecord.load(saved);
        assertTrue(reloaded.missingFrom(catalog).stream().noneMatch(m -> m.spell().equals(BOLT)));
        List<GlyphIdentityRecord.Missing> missing = reloaded.missingFrom(ImportedGlyphCatalog.assemble(List.of(base()), Set.of()));
        GlyphIdentityRecord.Missing lost = missing.stream().filter(m -> m.spell().equals(BOLT)).findFirst().orElseThrow();
        assertEquals(1, lost.fromLevel());
        assertEquals(12, lost.toLevel());
        String report = GlyphIdentityRecord.report(missing);
        assertTrue(report.contains("addon:bolt levels 1-12 from catalog 'addon' version 1.0.0")
            && report.contains("config/ars_n_spells/glyph_catalogs/addon.json") && report.contains(GlyphIdentityRecord.ACCEPT_MISSING_PROPERTY)
            && report.contains("nothing in it changed"), report);
        reloaded.abandon(missing);
        assertTrue(reloaded.missingFrom(ImportedGlyphCatalog.assemble(List.of(base()), Set.of())).isEmpty(), "an accepted loss stops refusing");
        assertTrue(GlyphIdentityRecord.load(reloaded.save(new CompoundTag())).missingFrom(ImportedGlyphCatalog.assemble(List.of(base()), Set.of()))
            .isEmpty(), "and is saved");
    }

    @Test
    void aRaisedFirstLevelInTheRecordIsMissingOnlyBelowIt() {
        CompoundTag tag = new CompoundTag();
        CompoundTag spells = new CompoundTag();
        CompoundTag bolt = new CompoundTag();
        bolt.putInt("min", 1);
        bolt.putInt("max", 12);
        spells.put("addon:bolt", bolt);
        tag.put("spells", spells);
        String fromTwo = ADDON_V1.replace("\"min\": 1, \"default_max\": 3", "\"min\": 2, \"default_max\": 3")
            .replace("[\"COMMON\", \"RARE\", \"EPIC\"], \"tier\": [1, 2, 3]", "[\"RARE\", \"EPIC\"], \"tier\": [2, 3]");
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.assemble(List.of(base(), live(fromTwo)), Set.of());
        assertTrue(catalog.sources().stream().allMatch(r -> r.errors().isEmpty()), catalog.sources().toString());
        List<GlyphIdentityRecord.Missing> missing = GlyphIdentityRecord.load(tag).missingFrom(catalog);
        assertEquals(1, missing.size(), missing.toString());
        assertEquals(1, missing.get(0).fromLevel());
        assertEquals(1, missing.get(0).toLevel());
    }
}
