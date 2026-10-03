package com.otectus.arsnspells.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the school every Ars Elemental 0.6.8.0 and Ars Zero 2.0.2 glyph resolves to.
 *
 * <p>One assertion per row of the shipped addon mappings, plus the two rules those rows lean
 * on: {@code necromancy} translates to eldritch, and a glyph that only shapes a cast — filter,
 * augment, form — is generic no matter what it declares. Written against
 * {@link SchoolResolver#resolve(String, List)} so it runs with no Ars runtime; the declared
 * school ids in each call are the ones javap reports from the addon jars.
 *
 * <p>These are behavioural pins, not documentation: silently reclassifying an addon glyph
 * moves a player's affinity track and their elemental scaling at once, and nothing else in the
 * build would notice.
 */
class SchoolResolverAddonTest {

    @AfterEach
    void restoreDefaults() {
        SchoolMappings.reset();
    }

    private static SpellSchoolId resolve(String id, String... schools) {
        return SchoolResolver.resolve(id, List.of(schools));
    }

    // --- the two rules ---------------------------------------------------------------------

    @Test
    void necromancy_resolvesEldritch() {
        // Both addons construct their own SpellSchool("necromancy"); lookup is by id, so the
        // single ARS_SCHOOL_TO_CANONICAL entry serves both.
        assertEquals(SpellSchoolId.ELDRITCH, resolve("some_addon:glyph_thing", "necromancy"),
            "the Ars school id \"necromancy\" must translate, not fall through to GENERIC");
    }

    @Test
    void filtersAugmentsAndForms_areGeneric() {
        assertEquals(SpellSchoolId.GENERIC, resolve("ars_elemental:glyph_aquatic_filter", "water"),
            "a filter says what the spell targets, not what it deals — water here would make a "
                + "fire spell filtered to aquatic mobs resolve ICE");
        assertEquals(SpellSchoolId.GENERIC, resolve("ars_elemental:glyph_fiery_filter", "fire"));
        assertEquals(SpellSchoolId.GENERIC,
            resolve("ars_elemental:glyph_summon_filter", "conjuration"));
        assertEquals(SpellSchoolId.GENERIC, resolve("ars_zero:augment_amplify_two"));
        assertEquals(SpellSchoolId.GENERIC, resolve("ars_zero:augment_cube"));
        assertEquals(SpellSchoolId.GENERIC, resolve("ars_zero:near_form"));
        assertEquals(SpellSchoolId.GENERIC, resolve("ars_zero:temporal_context_form"));
    }

    // --- Ars Elemental 0.6.8.0 -------------------------------------------------------------

    @Test
    void arsElemental_metadataRows() {
        assertEquals(SpellSchoolId.ICE, resolve("ars_elemental:glyph_bubble_shield", "water"));
        assertEquals(SpellSchoolId.ICE, resolve("ars_elemental:glyph_watery_grave", "water"));
        assertEquals(SpellSchoolId.LIGHTNING, resolve("ars_elemental:glyph_discharge", "air"));
        assertEquals(SpellSchoolId.LIGHTNING, resolve("ars_elemental:glyph_spark", "air"));
        assertEquals(SpellSchoolId.NATURE, resolve("ars_elemental:glyph_envenom", "earth"));
        assertEquals(SpellSchoolId.NATURE, resolve("ars_elemental:glyph_spike", "earth"));
        assertEquals(SpellSchoolId.NATURE, resolve("ars_elemental:glyph_poison_spores", "earth"));
        assertEquals(SpellSchoolId.ELDRITCH,
            resolve("ars_elemental:glyph_phantom_grasp", "necromancy"));
        assertEquals(SpellSchoolId.ELDRITCH, resolve("ars_elemental:glyph_charm", "necromancy"));
    }

    @Test
    void conjureTerrain_resolvesNatureByDeclarationOrder() {
        // Declares both conjuration (→ EVOCATION) and earth (→ NATURE). NATURE is earlier in
        // SpellSchoolId, so it wins — deliberately, and pinned here rather than overridden.
        assertEquals(SpellSchoolId.NATURE,
            resolve("ars_elemental:glyph_conjure_terrain", "conjuration", "earth"),
            "the multi-school tie-break is SpellSchoolId declaration order; reordering the enum "
                + "silently reclassifies every multi-school addon glyph");
    }

    @Test
    void arsElemental_explicitRows() {
        // Life Link declares necromancy, but its payload is a drain, so it feeds Blood.
        assertEquals(SpellSchoolId.BLOOD,
            resolve("ars_elemental:glyph_life_link", "necromancy"));
        // The propagators declare manipulation but only chain a spell onward.
        assertEquals(SpellSchoolId.GENERIC,
            resolve("ars_elemental:glyph_propagator_arc", "manipulation"));
        assertEquals(SpellSchoolId.GENERIC,
            resolve("ars_elemental:glyph_propagator_homing", "manipulation"));
    }

    // --- Ars Zero 2.0.2 --------------------------------------------------------------------

    @Test
    void arsZero_metadataRows() {
        assertEquals(SpellSchoolId.NATURE, resolve("ars_zero:effect_geometrize", "earth"));
        assertEquals(SpellSchoolId.LIGHTNING, resolve("ars_zero:push_effect", "air"));
        assertEquals(SpellSchoolId.ENDER,
            resolve("ars_zero:conjure_voxel_effect", "manipulation"));
        assertEquals(SpellSchoolId.ENDER,
            resolve("ars_zero:zero_gravity_effect", "manipulation"));
        assertEquals(SpellSchoolId.ENDER, resolve("ars_zero:effect_beam", "manipulation"));
        assertEquals(SpellSchoolId.ELDRITCH,
            resolve("ars_zero:effect_conjure_blight", "necromancy"));
    }

    @Test
    void arsZero_explicitRows() {
        // A ward that declares manipulation; abjuration-style wards are holy here.
        assertEquals(SpellSchoolId.HOLY,
            resolve("ars_zero:conjure_arcane_shield_effect", "manipulation"));
    }

    @Test
    void arsZero_controlFlowGlyphsEarnNoAffinity() {
        // All six declare manipulation. Deciding when and where the rest of the spell runs is
        // not an ender act, and a multi-phase staff should not farm ender affinity for it.
        for (String id : List.of("anchor_effect", "select_effect", "sustain_effect",
            "discard_effect", "effect_convergence", "enlarge_effect")) {
            assertEquals(SpellSchoolId.GENERIC, resolve("ars_zero:" + id, "manipulation"),
                id + " is control flow and must resolve GENERIC");
        }
    }
}
