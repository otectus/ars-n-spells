package com.otectus.arsnspells.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit F8 — spell-school classification must consult an explicit
 * registry-name map before the substring heuristic, so known Ars glyphs
 * classify deterministically and heuristic false positives are pinned down.
 *
 * <p>Source-text assertions because SpellAnalysis imports Ars Nouveau API
 * classes whose class-init needs the game runtime.
 */
class SpellAnalysisSchoolMapTest {

    private static String source() throws IOException {
        return Files.readString(Paths.get(
            "src/main/java/com/otectus/arsnspells/util/SpellAnalysis.java"));
    }

    @Test
    void deriveSchool_consultsExplicitMapBeforeHeuristic() throws IOException {
        String src = source();
        int methodIdx = src.indexOf("public static String deriveSchool");
        assertTrue(methodIdx > 0, "deriveSchool must exist");
        int mapLookupIdx = src.indexOf("KNOWN_GLYPH_SCHOOLS.get(", methodIdx);
        int heuristicIdx = src.indexOf("path.contains(\"fire\")", methodIdx);
        assertTrue(mapLookupIdx > methodIdx, "deriveSchool must look up KNOWN_GLYPH_SCHOOLS");
        assertTrue(heuristicIdx > mapLookupIdx,
            "the explicit map must be consulted BEFORE the substring heuristic "
                + "(the heuristic is only a fallback for unknown glyphs)");
    }

    @Test
    void fireworkFalsePositive_isPinnedToGeneric() throws IOException {
        String src = source();
        assertTrue(src.contains("Map.entry(\"ars_nouveau:glyph_firework\", \"generic\")"),
            "glyph_firework must be explicitly generic — the substring heuristic "
                + "matched \"fire\" inside \"firework\" and classified a decorative "
                + "glyph as fire school (audit F8)");
    }

    @Test
    void mapKeys_useFullRegistryNames() throws IOException {
        String src = source();
        assertTrue(src.contains("Map.entry(\"ars_nouveau:glyph_ignite\", \"fire\")"),
            "map keys must be full registry names (namespace:path) so another "
                + "mod's identically-named glyph is never misclassified by the map");
    }
}
