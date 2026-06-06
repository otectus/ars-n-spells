package com.otectus.arsnspells.affinity;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Bootstrap-free tests for {@link SchoolKeys}, the single source of truth that
 * keeps the Ars-side and Iron's-side affinity handlers (and the damage-bonus
 * site) agreeing on one key per school.
 */
class SchoolKeysTest {

    private static ResourceLocation irons(String path) {
        return ResourceLocation.fromNamespaceAndPath(SchoolKeys.IRONS_NS, path);
    }

    @Test
    void arsAndIronsAgreeOnSharedElementals() {
        // An Ars fireball and an Iron's fireball must accrue the same track.
        for (String word : new String[] {
            "fire", "ice", "lightning", "holy", "ender", "blood", "evocation", "nature", "eldritch"
        }) {
            assertEquals(
                SchoolKeys.fromResourceLocation(irons(word)),
                SchoolKeys.fromArsSchool(word),
                "Ars/Iron's key disagreement for " + word);
            assertEquals(SchoolKeys.IRONS_NS + ":" + word, SchoolKeys.fromArsSchool(word));
        }
    }

    @Test
    void arsOnlyWordsAreTrackedNotDropped() {
        // Pre-2.5.0 these threw in AffinityType.valueOf and were silently lost.
        assertEquals(SchoolKeys.ANS_NS + ":aqua", SchoolKeys.fromArsSchool("aqua"));
        assertEquals(SchoolKeys.ANS_NS + ":geo", SchoolKeys.fromArsSchool("geo"));
        assertEquals(SchoolKeys.ANS_NS + ":wind", SchoolKeys.fromArsSchool("wind"));
    }

    @Test
    void caseInsensitive() {
        assertEquals(SchoolKeys.IRONS_NS + ":fire", SchoolKeys.fromArsSchool("FIRE"));
        assertEquals(SchoolKeys.IRONS_NS + ":fire", SchoolKeys.fromArsSchool("Fire"));
    }

    @Test
    void genericAndUnknownAndNullReturnNull() {
        assertNull(SchoolKeys.fromArsSchool("generic"));
        assertNull(SchoolKeys.fromArsSchool("not_a_school"));
        assertNull(SchoolKeys.fromArsSchool(null));
        assertNull(SchoolKeys.fromResourceLocation(null));
    }

    @Test
    void addonSchoolIdsRoundTripVerbatim() {
        ResourceLocation abyssal = ResourceLocation.fromNamespaceAndPath("cataclysm_spellbooks", "abyssal");
        assertEquals("cataclysm_spellbooks:abyssal", SchoolKeys.fromResourceLocation(abyssal));
    }
}
