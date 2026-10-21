package com.otectus.arsnspells.util;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.config.MultiSchoolPowerPolicy;
import com.otectus.arsnspells.cooldown.CooldownCategory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit mode is one school: every school-keyed answer of the analysis becomes that school
 * alone, while what the recipe actually is stays the glyphs'.
 */
class SpellAnalysisExplicitSchoolTest {

    private static SpellAnalysis.Result empty() {
        return SpellAnalysis.analyze((Spell) null);
    }

    @Test
    void explicitBuiltIn_replacesEverySchoolAnswer() {
        SpellAnalysis.Result glyphs = empty();
        SpellAnalysis.Result ice = glyphs.withExplicitSchool("irons_spellbooks:ice");

        assertTrue(ice.isExplicit());
        assertEquals("irons_spellbooks:ice", ice.schoolKey(), "affinity and progression key");
        assertEquals(List.of("irons_spellbooks:ice"), ice.allSchoolKeys(), "the only scaling candidate");
        assertEquals("ice", ice.dominantSchool(), "the built-in projection school-matched discounts read");
        assertEquals(Set.of("ice"), ice.schools());
    }

    @Test
    void explicitMode_keepsTheRecipeFacts() {
        SpellAnalysis.Result glyphs = empty();
        SpellAnalysis.Result ice = glyphs.withExplicitSchool("irons_spellbooks:ice");

        assertEquals(glyphs.category(), ice.category(), "cooldown categories follow the glyphs");
        assertEquals(CooldownCategory.UTILITY, ice.category());
        assertSame(glyphs.firstEffect(), ice.firstEffect());
        assertSame(glyphs, ice.glyphProfile(), "the glyph profile stays available for display");
        assertFalse(glyphs.isExplicit());
        assertEquals(SchoolKeys.GENERIC, glyphs.schoolKey(), "narrowing never mutates the glyph profile");
    }

    @Test
    void explicitAddonSchool_hasNoBuiltInProjection() {
        SpellAnalysis.Result aqua = empty().withExplicitSchool("traveloptics:aqua");

        assertEquals("traveloptics:aqua", aqua.schoolKey());
        assertEquals(List.of("traveloptics:aqua"), aqua.allSchoolKeys());
        assertEquals("generic", aqua.dominantSchool(), "no built-in school-matched bonus applies");
        assertTrue(aqua.schools().isEmpty());
    }

    @Test
    void absentOrMalformedSchool_leavesTheProfileAutomatic() {
        SpellAnalysis.Result glyphs = empty();
        assertSame(glyphs, glyphs.withExplicitSchool(null));
        assertSame(glyphs, glyphs.withExplicitSchool(""));
        assertSame(glyphs, glyphs.withExplicitSchool("fire"), "a bare name is not a stored school id");
        assertSame(glyphs, glyphs.withExplicitSchool(SchoolKeys.GENERIC));
    }

    @Test
    void renarrowing_startsFromTheGlyphs_notFromThePreviousChoice() {
        SpellAnalysis.Result glyphs = empty();
        SpellAnalysis.Result fire = glyphs.withExplicitSchool("irons_spellbooks:fire");
        SpellAnalysis.Result ice = fire.withExplicitSchool("irons_spellbooks:ice");

        assertEquals(List.of("irons_spellbooks:ice"), ice.allSchoolKeys());
        assertSame(glyphs, ice.glyphProfile());
    }

    @Test
    void explicitMode_offersOneCandidate_soNoPolicyCanCollectAGlyphSchoolBonus() {
        // What getMultiplierForCaster builds from allSchoolKeys(): with an explicit school there
        // is exactly one candidate, so MAX and AVERAGE cannot reach a higher or extra glyph school.
        SpellAnalysis.Result ice = empty().withExplicitSchool("irons_spellbooks:ice");
        Map<String, Float> powers = new LinkedHashMap<>();
        for (String key : ice.allSchoolKeys()) {
            powers.put(key, "irons_spellbooks:ice".equals(key) ? 1.5f : 3.0f);
        }
        for (MultiSchoolPowerPolicy policy : MultiSchoolPowerPolicy.values()) {
            SpellScalingUtil.SchoolAggregate aggregate =
                SpellScalingUtil.aggregateSchoolPower(policy, powers, ice.schoolKey());
            assertEquals("irons_spellbooks:ice", aggregate.matchedSchool(), policy.name());
            assertEquals(1.5f, aggregate.schoolPower(), 1e-6, policy.name());
        }
    }
}
