package com.otectus.arsnspells.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit F-1/F-2 — ring/blasphemy/source-jar detection must be tag-driven
 * (datapack-extensible) instead of hardcoded registry-name sets, following
 * the F1 {@code irons_spell_books} pattern. All shipped tag entries must be
 * {@code required: false} so the tags load without the optional mods.
 */
class CurioTagDetectionTest {

    private static String read(String path) throws IOException {
        return Files.readString(Paths.get(path));
    }

    @Test
    void scanCurios_usesTagsNotHardcodedSets() throws IOException {
        String src = read("src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java");
        assertTrue(src.contains("ModTags.CURSED_RINGS")
                && src.contains("ModTags.VIRTUE_RINGS")
                && src.contains("ModTags.BLASPHEMY_CURIOS"),
            "curio scanning must check the ars_n_spells item tags");
        assertFalse(src.contains("CURSED_RING_IDS") || src.contains("BLASPHEMY_IDS"),
            "the hardcoded ResourceLocation sets must be gone (audit F-1)");
    }

    @Test
    void sourceJarScan_usesBlockTagNotSubstring() throws IOException {
        String src = read("src/main/java/com/otectus/arsnspells/events/RegenSynergyHandler.java");
        assertTrue(src.contains("ModTags.SOURCE_JARS"),
            "the synergy scan must check the ars_n_spells:source_jars block tag");
        assertFalse(src.contains("contains(\"source_jar\")"),
            "the registry-path substring match must be gone (audit F-2)");
    }

    @Test
    void shippedTagJsons_existWithOptionalEntries() throws IOException {
        String base = "src/main/resources/data/ars_n_spells/tags/";
        for (String tag : new String[]{
                "items/cursed_rings.json", "items/virtue_rings.json",
                "items/blasphemy_curios.json", "blocks/source_jars.json"}) {
            Path p = Paths.get(base + tag);
            assertTrue(Files.exists(p), tag + " must ship with the mod");
            String json = Files.readString(p);
            assertTrue(json.contains("\"required\": false"),
                tag + " entries must be optional (required:false) so the tag "
                    + "loads when the referenced mod is absent");
            assertTrue(json.contains("\"replace\": false"),
                tag + " must merge with (not clobber) datapack additions");
        }
    }

    @Test
    void blasphemySchoolMatch_isNamespaceAgnostic() throws IOException {
        String src = read("src/main/java/com/otectus/arsnspells/compat/SanctifiedLegacyCompat.java");
        int idx = src.indexOf("public static boolean hasMatchingBlasphemy");
        assertTrue(idx > 0, "hasMatchingBlasphemy must exist");
        int end = src.indexOf("\n    }", idx);
        String body = src.substring(idx, end);
        assertTrue(body.contains("getPath().equals(wantedPath)"),
            "school matching must compare item PATH only, so pack-added "
                + "blasphemies in any namespace can school-match (audit F-1)");
        assertFalse(body.contains("new ResourceLocation(MOD_ID"),
            "school matching must not pin the Covenant namespace anymore");
    }
}
