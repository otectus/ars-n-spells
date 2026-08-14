package com.otectus.arsnspells.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Behavioural tests for the school resolution chain.
 *
 * <p>These replace an earlier set that asserted on the <em>source text</em> of
 * {@code SpellAnalysis.java} — grepping for a {@code Map.entry("ars_nouveau:glyph_ignite",
 * "fire")} literal. That style proves a line exists, not that resolution works, and it broke
 * the moment the map moved even though behaviour was unchanged. {@code SchoolResolver} exposes
 * a data-shaped overload precisely so the real decision logic can be exercised here without a
 * Minecraft or Ars Nouveau runtime.
 */
class SchoolResolverTest {

    @AfterEach
    void restoreDefaults() {
        SchoolMappings.reset();
    }

    private static SpellSchoolId resolve(String id, String... schools) {
        return SchoolResolver.resolve(id, List.of(schools));
    }

    // --- source 1: explicit glyph mapping -------------------------------------------------

    @Test
    void explicitMapping_winsOverDeclaredSchools() {
        // Firework declares nothing and its path contains "fire"; the shipped mapping pins it.
        assertEquals(SpellSchoolId.GENERIC, resolve("ars_nouveau:glyph_firework"),
            "glyph_firework must stay generic — the substring heuristic matched \"fire\" inside "
                + "\"firework\" and classified a decorative glyph as fire school");
    }

    @Test
    void explicitMapping_overridesEvenDeclaredMetadata() {
        SchoolMappings.applyOverlay(Map.of("ars_elemental:glyph_spark", SpellSchoolId.LIGHTNING),
            Map.of());
        assertEquals(SpellSchoolId.LIGHTNING, resolve("ars_elemental:glyph_spark", "fire"),
            "a pack author's explicit mapping is a deliberate correction and must beat the "
                + "addon's own declared school");
    }

    @Test
    void mappingKeys_areFullRegistryIds() {
        // Same path, different namespace: the mapping must not leak across mods.
        assertNotEquals(resolve("ars_nouveau:glyph_firework"),
            resolve("some_addon:glyph_firework", "fire"),
            "mappings are keyed by full registry id so another mod's identically-named glyph "
                + "is never misclassified");
    }

    // --- source 2: Ars Nouveau declared schools -------------------------------------------

    @Test
    void declaredArsSchools_areTranslatedToCanonical() {
        assertEquals(SpellSchoolId.FIRE, resolve("ars_nouveau:glyph_ignite", "fire"));
        // Ars models Freeze as "water"; Iron's has no water school, so it lands on ice.
        assertEquals(SpellSchoolId.ICE, resolve("ars_nouveau:glyph_freeze", "water"));
        assertEquals(SpellSchoolId.NATURE, resolve("ars_nouveau:glyph_grow", "earth"));
        assertEquals(SpellSchoolId.EVOCATION, resolve("ars_nouveau:glyph_summon", "conjuration"));
    }

    @Test
    void declaredSchools_beatTheHeuristic() {
        // Path says "fire", metadata says water. Metadata is real data and must win.
        assertEquals(SpellSchoolId.ICE, resolve("some_addon:glyph_firestorm_water", "water"),
            "declared schools must take precedence over a substring match on the path");
    }

    @Test
    void parentElementalSchool_aloneResolvesNothing() {
        assertEquals(SpellSchoolId.GENERIC, resolve("some_addon:glyph_thing", "elemental"),
            "the parent 'elemental' school does not say which element the glyph is, so it must "
                + "not silently pick one");
    }

    @Test
    void multipleSchools_resolveDeterministicallyByDeclarationOrder() {
        // Whatever order the addon declares them in, the answer must be identical.
        SpellSchoolId a = resolve("some_addon:glyph_x", "water", "fire");
        SpellSchoolId b = resolve("some_addon:glyph_x", "fire", "water");
        assertEquals(a, b, "multi-school glyphs must not resolve by collection iteration order");
        assertEquals(SpellSchoolId.FIRE, a,
            "the winner is the school earliest in SpellSchoolId declaration order");
    }

    @Test
    void unknownArsSchool_fallsThroughRatherThanGuessing() {
        assertEquals(SpellSchoolId.GENERIC, resolve("some_addon:glyph_weird", "chronomancy"),
            "an unrecognised Ars school must not resolve to an arbitrary canonical value");
    }

    // --- source 3: heuristic fallback ------------------------------------------------------

    @Test
    void heuristic_appliesOnlyWhenNothingElseAnswers() {
        assertEquals(SpellSchoolId.FIRE, resolve("some_addon:glyph_burning_touch"));
        assertEquals(SpellSchoolId.ELDRITCH, resolve("some_addon:glyph_wither_touch"));
    }

    @Test
    void heuristic_doesNotMisreadLightningAsHoly() {
        assertEquals(SpellSchoolId.LIGHTNING, resolve("some_addon:glyph_lightning_bolt"),
            "\"lightning\" contains \"light\"; lightning must not classify as holy");
    }

    // --- vocabulary invariants -------------------------------------------------------------

    @Test
    void everyNonGenericSchool_hasAnAffinityType() {
        for (SpellSchoolId school : SpellSchoolId.values()) {
            if (school.isGeneric()) {
                continue;
            }
            assertNotNull(
                com.otectus.arsnspells.affinity.AffinityType.valueOf(school.name()),
                "every canonical school must have a matching AffinityType, or spells of that "
                    + "school silently receive no affinity bonus — the exact defect that made "
                    + "'aqua', 'geo' and 'wind' dead values");
        }
    }

    @Test
    void legacyOrphanSchoolIds_parseToGenericRatherThanThrowing() {
        for (String legacy : new String[] {"aqua", "geo", "wind", "nonsense", ""}) {
            assertEquals(SpellSchoolId.GENERIC, SpellSchoolId.fromId(legacy),
                "legacy/unknown school ids must degrade to generic, not throw, so old NBT loads");
        }
        assertEquals(SpellSchoolId.GENERIC, SpellSchoolId.fromId(null));
    }

    @Test
    void roundTrip_idToEnumIsStable() {
        for (SpellSchoolId school : SpellSchoolId.values()) {
            assertEquals(school, SpellSchoolId.fromId(school.id()));
        }
    }

    // --- overlay behaviour ------------------------------------------------------------------

    @Test
    void overlay_doesNotDestroyShippedDefaults() {
        int before = SchoolMappings.get().glyphMappingCount();
        SchoolMappings.applyOverlay(Map.of("addon:glyph_new", SpellSchoolId.BLOOD), Map.of());
        assertEquals(SpellSchoolId.BLOOD, resolve("addon:glyph_new"));
        assertEquals(SpellSchoolId.GENERIC, resolve("ars_nouveau:glyph_firework"),
            "an overlay adds to the shipped mappings; it must not replace them");
        assertEquals(before + 1, SchoolMappings.get().glyphMappingCount());
    }

    @Test
    void overlay_canRemapAnArsSchoolWholesale() {
        SchoolMappings.applyOverlay(Map.of(), Map.of("water", SpellSchoolId.NATURE));
        assertEquals(SpellSchoolId.NATURE, resolve("ars_nouveau:glyph_freeze", "water"),
            "packs must be able to re-aim an entire Ars school without touching Java");
    }

    @Test
    void reset_restoresShippedBehaviour() {
        SchoolMappings.applyOverlay(Map.of("ars_nouveau:glyph_firework", SpellSchoolId.FIRE), Map.of());
        assertEquals(SpellSchoolId.FIRE, resolve("ars_nouveau:glyph_firework"));
        SchoolMappings.reset();
        assertEquals(SpellSchoolId.GENERIC, resolve("ars_nouveau:glyph_firework"));
    }
}
