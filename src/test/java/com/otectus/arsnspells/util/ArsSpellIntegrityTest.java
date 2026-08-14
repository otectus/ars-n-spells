package com.otectus.arsnspells.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the "addon removed while an exported item still exists" case.
 *
 * <p>Only the pure list-shaping half is exercised here: {@link ArsSpellIntegrity#missingGlyphIds}
 * needs Ars Nouveau's glyph registry to be populated, which requires a game runtime, so the
 * registry-backed detection is asserted in the GameTest layer instead. What this pins down is
 * the message formatting a player actually sees, which is pure and easy to get wrong.
 */
class ArsSpellIntegrityTest {

    @Test
    void describeMissing_isEmptyForAnIntactPayload() {
        assertEquals("", ArsSpellIntegrity.describeMissing(List.of()));
        assertEquals("", ArsSpellIntegrity.describeMissing(null));
    }

    @Test
    void describeMissing_listsEachMissingGlyph() {
        String text = ArsSpellIntegrity.describeMissing(
            List.of("ars_elemental:glyph_water_grave", "ars_elemental:glyph_spark"));
        assertTrue(text.contains("ars_elemental:glyph_water_grave"), text);
        assertTrue(text.contains("ars_elemental:glyph_spark"), text);
    }

    @Test
    void describeMissing_truncatesLongListsRatherThanFloodingChat() {
        String text = ArsSpellIntegrity.describeMissing(
            List.of("a:one", "b:two", "c:three", "d:four", "e:five"));
        assertTrue(text.contains("a:one"), text);
        assertTrue(text.contains("c:three"), text);
        assertTrue(text.contains("(+2 more)"),
            "a badly broken payload must summarise rather than print an unreadable wall: " + text);
        assertTrue(!text.contains("d:four"), "entries past the cap must be summarised, not shown");
    }
}
