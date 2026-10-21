package com.otectus.arsnspells.spell;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The request and use-time rules for an explicit spell school, over plain inputs: which values
 * mean Automatic, which are refused and why, and what a stored value resolves to when its school
 * has been removed or the server has turned overrides off.
 */
class SpellSchoolChoiceTest {

    /** Stand-in for the installed Iron's registry: two built-ins and one addon school. */
    private static final Predicate<String> INSTALLED = Set.of(
        "irons_spellbooks:fire", "irons_spellbooks:ice", "traveloptics:aqua")::contains;

    @Test
    void automaticSpellings_normalizeToEmpty() {
        assertEquals("", SpellSchoolChoice.normalize(null));
        assertEquals("", SpellSchoolChoice.normalize(""));
        assertEquals("", SpellSchoolChoice.normalize("   "));
        assertEquals("", SpellSchoolChoice.normalize("auto"));
        assertEquals("", SpellSchoolChoice.normalize(" AUTO "));
    }

    @Test
    void builtInShortNames_becomeIronsIds_andAddonsKeepTheirNamespace() {
        assertEquals("irons_spellbooks:fire", SpellSchoolChoice.normalize("fire"));
        assertEquals("irons_spellbooks:ice", SpellSchoolChoice.normalize("Irons_Spellbooks:Ice"));
        assertEquals("traveloptics:aqua", SpellSchoolChoice.normalize("traveloptics:aqua"));
        // A custom namespace with a built-in's path is still its own school.
        assertEquals("somemod:fire", SpellSchoolChoice.normalize("somemod:fire"));
    }

    @Test
    void verdicts_withOverridesAllowed() {
        assertEquals(SpellSchoolChoice.Verdict.AUTOMATIC, SpellSchoolChoice.check("", true, INSTALLED));
        assertEquals(SpellSchoolChoice.Verdict.ACCEPTED, SpellSchoolChoice.check("fire", true, INSTALLED));
        assertEquals(SpellSchoolChoice.Verdict.ACCEPTED, SpellSchoolChoice.check("traveloptics:aqua", true, INSTALLED));
        assertEquals(SpellSchoolChoice.Verdict.NOT_INSTALLED,
            SpellSchoolChoice.check("removed_addon:aqua", true, INSTALLED));
        assertEquals(SpellSchoolChoice.Verdict.MALFORMED, SpellSchoolChoice.check("not a school!", true, INSTALLED));
        assertEquals(SpellSchoolChoice.Verdict.MALFORMED, SpellSchoolChoice.check("generic", true, INSTALLED),
            "generic is the absence of a school, never a school to choose");
        assertEquals(SpellSchoolChoice.Verdict.MALFORMED,
            SpellSchoolChoice.check("a:" + "b".repeat(300), true, INSTALLED), "over-long ids are refused");
    }

    @Test
    void disabledServer_refusesEveryExplicitSchoolButStillAcceptsAutomatic() {
        assertEquals(SpellSchoolChoice.Verdict.AUTOMATIC, SpellSchoolChoice.check("auto", false, INSTALLED));
        assertEquals(SpellSchoolChoice.Verdict.DISABLED, SpellSchoolChoice.check("fire", false, INSTALLED));
        assertEquals(SpellSchoolChoice.Verdict.DISABLED, SpellSchoolChoice.check("removed_addon:aqua", false, INSTALLED));
        // A malformed request is reported as malformed, not as a policy refusal.
        assertEquals(SpellSchoolChoice.Verdict.MALFORMED, SpellSchoolChoice.check("!!", false, INSTALLED));
    }

    @Test
    void usable_isOnlyAutomaticOrAccepted() {
        assertTrue(SpellSchoolChoice.Verdict.AUTOMATIC.usable());
        assertTrue(SpellSchoolChoice.Verdict.ACCEPTED.usable());
        assertFalse(SpellSchoolChoice.Verdict.MALFORMED.usable());
        assertFalse(SpellSchoolChoice.Verdict.NOT_INSTALLED.usable());
        assertFalse(SpellSchoolChoice.Verdict.DISABLED.usable());
    }

    @Test
    void storedValues_resolveAtUse_andFallBackToAutomaticWithoutBeingRewritten() {
        assertEquals("irons_spellbooks:ice", SpellSchoolChoice.effective("irons_spellbooks:ice", true, INSTALLED));
        assertEquals("traveloptics:aqua", SpellSchoolChoice.effective("traveloptics:aqua", true, INSTALLED));
        assertNull(SpellSchoolChoice.effective("", true, INSTALLED), "an entry without a school is Automatic");
        assertNull(SpellSchoolChoice.effective("removed_addon:aqua", true, INSTALLED),
            "a school whose addon was removed casts as Automatic");
        assertNull(SpellSchoolChoice.effective("irons_spellbooks:ice", false, INSTALLED),
            "a disabled server casts a stored choice as Automatic");
        assertNull(SpellSchoolChoice.effective("garbage value", true, INSTALLED));
    }

    @Test
    void wellFormed_requiresANamespacedNonGenericId() {
        assertTrue(SpellSchoolChoice.isWellFormed("irons_spellbooks:fire"));
        assertTrue(SpellSchoolChoice.isWellFormed("traveloptics:aqua"));
        assertFalse(SpellSchoolChoice.isWellFormed("fire"));
        assertFalse(SpellSchoolChoice.isWellFormed("ars_n_spells:generic"));
        assertFalse(SpellSchoolChoice.isWellFormed(null));
        assertFalse(SpellSchoolChoice.isWellFormed("Upper:Case"));
    }
}
