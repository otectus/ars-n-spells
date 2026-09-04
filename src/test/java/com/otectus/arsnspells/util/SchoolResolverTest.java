package com.otectus.arsnspells.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static List<SpellSchoolId> resolveAll(String id, String... schools) {
        // A List, not a Set, so the assertions can pin the *order* resolveAll promises.
        return new ArrayList<>(SchoolResolver.resolveAll(id, List.of(schools)));
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

    // --- multi-school resolution -------------------------------------------------------------

    @Test
    void resolveAll_returnsEverySchoolTheGlyphDeclares() {
        assertEquals(List.of(SpellSchoolId.FIRE, SpellSchoolId.LIGHTNING),
            resolveAll("some_addon:glyph_storm_flame", "fire", "air"),
            "a dual-element glyph declares two schools and must resolve to both - keeping only "
                + "the dominant one silently discarded half of what the addon said");
    }

    @Test
    void resolveAll_preservesDeclarationOrder() {
        assertEquals(List.of(SpellSchoolId.LIGHTNING, SpellSchoolId.FIRE),
            resolveAll("some_addon:glyph_storm_flame", "air", "fire"),
            "resolveAll is ordered by declaration, so the caller can see which school the addon "
                + "listed first");
    }

    @Test
    void resolveAll_singleSchoolGlyphYieldsExactlyOne() {
        assertEquals(List.of(SpellSchoolId.FIRE), resolveAll("ars_nouveau:glyph_ignite", "fire"));
    }

    @Test
    void resolveAll_excludesGenericAndMayBeEmpty() {
        assertTrue(resolveAll("ars_nouveau:glyph_firework").isEmpty(),
            "generic is the absence of a school, so it is expressed as an empty set rather than "
                + "as a GENERIC member");
        assertTrue(resolveAll("some_addon:glyph_thing", "elemental").isEmpty(),
            "the parent 'elemental' school stays unmapped: expanding it to its four children "
                + "would let any generic-elemental glyph claim the caster's best element");
        assertTrue(resolveAll("some_addon:glyph_nondescript").isEmpty());
    }

    @Test
    void resolveAll_dropsUnrecognisedSchoolsButKeepsTheRest() {
        assertEquals(List.of(SpellSchoolId.FIRE),
            resolveAll("some_addon:glyph_x", "chronomancy", "fire"),
            "an Ars school ANS cannot translate is dropped; the ones it can are kept");
    }

    @Test
    void resolve_isStillTheDeterministicWinnerAmongResolveAll() {
        // resolve() is now defined in terms of resolveAll(), so pin that it did not inherit
        // declaration order: the winner is still earliest in SpellSchoolId order, whichever way
        // round the addon declared the pair.
        assertEquals(SpellSchoolId.FIRE, resolve("some_addon:glyph_x", "air", "fire"));
        assertEquals(SpellSchoolId.FIRE, resolve("some_addon:glyph_x", "fire", "air"));
        assertEquals(SpellSchoolId.GENERIC, resolve("some_addon:glyph_nondescript"),
            "an empty resolveAll still means GENERIC to every existing caller");
    }

    @Test
    void overlay_acceptsSeveralSchoolsForOneGlyph() {
        // The datapack array form: {"glyphs": {"addon:glyph_x": ["ice", "nature"]}}
        SchoolMappings.applyMultiOverlay(
            Map.of("addon:glyph_x", List.of(SpellSchoolId.ICE, SpellSchoolId.NATURE)), Map.of());
        assertEquals(List.of(SpellSchoolId.ICE, SpellSchoolId.NATURE),
            resolveAll("addon:glyph_x", "fire"),
            "an override lists every school the glyph counts as, and stays authoritative over "
                + "the glyph's own declared metadata");
        assertEquals(SpellSchoolId.ICE, resolve("addon:glyph_x", "fire"));
    }

    @Test
    void overlay_singleSchoolFormIsUnchangedByTheArrayForm() {
        SchoolMappings.applyOverlay(Map.of("addon:glyph_y", SpellSchoolId.BLOOD), Map.of());
        assertEquals(SpellSchoolId.BLOOD, resolve("addon:glyph_y", "fire"));
        assertEquals(List.of(SpellSchoolId.BLOOD), resolveAll("addon:glyph_y", "fire"),
            "a one-school override is just a one-element list; existing pack files must keep "
                + "behaving identically");
    }

    @Test
    void overlay_canRemapAnArsSchoolToSeveralCanonicalSchools() {
        SchoolMappings.applyMultiOverlay(Map.of(),
            Map.of("elemental", List.of(SpellSchoolId.FIRE, SpellSchoolId.ICE)));
        assertEquals(Set.of(SpellSchoolId.FIRE, SpellSchoolId.ICE),
            SchoolResolver.resolveAll("some_addon:glyph_thing", List.of("elemental")),
            "ANS will not expand 'elemental' itself, but a pack may opt in to doing so");
    }

    @Test
    void reset_restoresShippedBehaviour() {
        SchoolMappings.applyOverlay(Map.of("ars_nouveau:glyph_firework", SpellSchoolId.FIRE), Map.of());
        assertEquals(SpellSchoolId.FIRE, resolve("ars_nouveau:glyph_firework"));
        SchoolMappings.reset();
        assertEquals(SpellSchoolId.GENERIC, resolve("ars_nouveau:glyph_firework"));
    }
}
