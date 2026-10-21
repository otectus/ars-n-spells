package com.otectus.arsnspells.glyph.recipe;

import com.otectus.arsnspells.glyph.GlyphTestFixtures;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.behavior.GlyphMappings;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Press's single conversion rule: every refusal by name, in the order a player fixes them, and exact terms. */
class GlyphPressPlanTest {
    private static final String GEMS = "ars_n_spells:glyph_press_catalysts";

    private static GlyphPressPlan.Inputs inputs(ScrollReading scroll, boolean plate, int gems, ResourceLocation output,
                                                int outputCount, Boolean learned) {
        return new GlyphPressPlan.Inputs(scroll, plate, gems, GEMS::equals, output, outputCount, 64, learned, 4000);
    }

    private static GlyphPressPlan.Reason reason(GlyphPressPlan.Inputs in, GlyphBehaviorSnapshot snapshot) {
        return GlyphPressPlan.plan(in, GlyphTestFixtures.catalog(), snapshot).reason();
    }

    private static GlyphPressPlan.Reason reason(GlyphPressPlan.Inputs in) {
        return reason(in, GlyphTestFixtures.snapshot());
    }

    @Test
    void aValidScrollPlansTheExactLevelAndTerms() {
        GlyphPressPlan.Plan plan = GlyphPressPlan.plan(inputs(ScrollReading.spell(GlyphTestFixtures.FIREBOLT, 3), true, 2, null, 0, true),
            GlyphTestFixtures.catalog(), GlyphTestFixtures.snapshot());
        assertTrue(plan.ok(), plan.reason().toString());
        assertEquals(new ImportedGlyphKey(GlyphTestFixtures.FIREBOLT, 3), plan.key(), "level III makes level III, nothing else");
        assertEquals(2, plan.behavior().tier());
        assertEquals(1500, plan.behavior().pressSource());
    }

    @Test
    void everyScrollProblemHasItsOwnReason() {
        assertEquals(GlyphPressPlan.Reason.INSERT_SCROLL, reason(inputs(ScrollReading.EMPTY, true, 4, null, 0, true)));
        assertEquals(GlyphPressPlan.Reason.IRONS_ABSENT, reason(inputs(ScrollReading.IRONS_ABSENT, true, 4, null, 0, true)));
        assertEquals(GlyphPressPlan.Reason.NOT_A_SCROLL, reason(inputs(ScrollReading.NOT_A_SCROLL, true, 4, null, 0, true)));
        assertEquals(GlyphPressPlan.Reason.BLANK_SCROLL, reason(inputs(ScrollReading.BLANK, true, 4, null, 0, true)));
        assertEquals(GlyphPressPlan.Reason.EXPORTED_ARS_SPELL, reason(inputs(ScrollReading.EXPORTED, true, 4, null, 0, true)));
        assertEquals(GlyphPressPlan.Reason.MULTIPLE_SPELLS, reason(inputs(ScrollReading.MULTIPLE, true, 4, null, 0, true)));
        assertEquals(GlyphPressPlan.Reason.MALFORMED_SCROLL, reason(inputs(ScrollReading.MALFORMED, true, 4, null, 0, true)));
        assertEquals(GlyphPressPlan.Reason.INVALID_LEVEL,
            reason(inputs(ScrollReading.invalidLevel(GlyphTestFixtures.FIREBOLT, 0), true, 4, null, 0, true)));
        assertEquals(GlyphPressPlan.Reason.NO_ADAPTER,
            reason(inputs(ScrollReading.spell(ResourceLocation.parse("addon:unknown"), 1), true, 4, null, 0, true)));
        assertEquals(GlyphPressPlan.Reason.UNSUPPORTED_SPELL,
            reason(inputs(ScrollReading.spell(GlyphTestFixtures.STORM, 1), true, 4, null, 0, true)));
        assertEquals(GlyphPressPlan.Reason.LEVEL_NOT_CATALOGUED,
            reason(inputs(ScrollReading.spell(GlyphTestFixtures.FIREBOLT, 4), true, 4, null, 0, true)), "never clamped to III");
    }

    @Test
    void serverStatePlayerAndMaterialsAreCheckedInOrder() {
        ScrollReading firebolt = ScrollReading.spell(GlyphTestFixtures.FIREBOLT, 1);
        GlyphBehaviorSnapshot pressOff = GlyphTestFixtures.snapshot(GlyphMappings.Result.EMPTY,
            new GlyphBehaviorSnapshot.Settings(true, false, "per_spell", 4, 16, 8, 4000), Set.of());
        assertEquals(GlyphPressPlan.Reason.PRESS_DISABLED, reason(inputs(firebolt, true, 4, null, 0, true), pressOff));
        GlyphBehaviorSnapshot ironsOff = GlyphTestFixtures.snapshot(GlyphMappings.Result.EMPTY,
            GlyphBehaviorSnapshot.Settings.DEFAULTS, Set.of(GlyphTestFixtures.FIREBOLT));
        assertEquals(GlyphPressPlan.Reason.SPELL_DISABLED, reason(inputs(firebolt, true, 4, null, 0, true), ironsOff));
        assertEquals(GlyphPressPlan.Reason.NOT_LEARNED,
            reason(inputs(ScrollReading.spell(GlyphTestFixtures.SHROUD, 1), true, 4, null, 0, false)));
        assertEquals(GlyphPressPlan.Reason.INSERT_PLATE,
            reason(inputs(ScrollReading.spell(GlyphTestFixtures.SHROUD, 1), false, 4, null, 0, null)),
            "without an acting player the learning prerequisite is not judged");
        assertEquals(GlyphPressPlan.Reason.INSERT_PLATE, reason(inputs(firebolt, false, 4, null, 0, true)));
        assertEquals(GlyphPressPlan.Reason.NEEDS_CATALYST, reason(inputs(firebolt, true, 0, null, 0, true)));
        GlyphPressPlan.Inputs wrongTag = new GlyphPressPlan.Inputs(firebolt, true, 4, tag -> false, null, 0, 64, true, 4000);
        assertEquals(GlyphPressPlan.Reason.NEEDS_CATALYST, reason(wrongTag));
    }

    @Test
    void outputRoomAndSourceCapacityAreChecked() {
        ScrollReading firebolt = ScrollReading.spell(GlyphTestFixtures.FIREBOLT, 1);
        ResourceLocation glyph = new ImportedGlyphKey(GlyphTestFixtures.FIREBOLT, 1).itemId();
        assertTrue(GlyphPressPlan.plan(inputs(firebolt, true, 1, glyph, 63, true), GlyphTestFixtures.catalog(),
            GlyphTestFixtures.snapshot()).ok(), "the same glyph stacks onto a partial output");
        assertEquals(GlyphPressPlan.Reason.OUTPUT_FULL, reason(inputs(firebolt, true, 1, glyph, 64, true)));
        assertEquals(GlyphPressPlan.Reason.OUTPUT_FULL,
            reason(inputs(firebolt, true, 1, ResourceLocation.parse("minecraft:dirt"), 1, true)), "a different item blocks the output");
        GlyphMappings.Result costly = GlyphTestFixtures.mappings(
            "{\"schema_version\": 1, \"spell\": \"irons_spellbooks:firebolt\", \"defaults\": {\"source_cost\": 5000}}");
        GlyphBehaviorSnapshot snapshot = GlyphTestFixtures.snapshot(costly, GlyphBehaviorSnapshot.Settings.DEFAULTS, Set.of());
        assertEquals(GlyphPressPlan.Reason.SOURCE_OVER_CAPACITY, reason(inputs(firebolt, true, 1, null, 0, true), snapshot),
            "a recipe that can never fit the buffer says so");
    }

    @Test
    void sameTermsComparesEverythingAJobFixes() {
        var catalog = GlyphTestFixtures.catalog();
        GlyphPressPlan.Inputs in = inputs(ScrollReading.spell(GlyphTestFixtures.FIREBOLT, 1), true, 2, null, 0, true);
        GlyphPressPlan.Plan before = GlyphPressPlan.plan(in, catalog, GlyphTestFixtures.snapshot());
        GlyphPressPlan.Plan same = GlyphPressPlan.plan(in, catalog, GlyphTestFixtures.snapshot());
        GlyphPressPlan.Plan cheaper = GlyphPressPlan.plan(in, catalog, GlyphTestFixtures.snapshot(GlyphTestFixtures.mappings(
            "{\"schema_version\": 1, \"spell\": \"irons_spellbooks:firebolt\", \"defaults\": {\"source_cost\": 10}}"),
            GlyphBehaviorSnapshot.Settings.DEFAULTS, Set.of()));
        assertTrue(before.sameTerms(same));
        assertFalse(before.sameTerms(cheaper), "a changed Source cost is a different recipe");
    }
}
