package com.otectus.arsnspells.glyph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportedGlyphKnowledgeTest {
    @Test
    void levelsOfOneSpellCountOnce() {
        // 20 known glyphs, 3 of them Firebolt I-III: two surplus levels once Ars counted all 20.
        assertEquals(2, ImportedGlyphKnowledge.surplus(20, 3, 1, 20));
    }

    @Test
    void ordinaryGlyphsAndSingleLevelsAreNeverTouched() {
        assertEquals(0, ImportedGlyphKnowledge.surplus(20, 0, 0, 20));
        assertEquals(0, ImportedGlyphKnowledge.surplus(20, 3, 3, 20), "three different spells are three glyphs");
    }

    @Test
    void aBonusSetPastTheKnownCountIsLeftAlone() {
        // A command or another mod raised the bonus to 16 with nothing imported known.
        assertEquals(0, ImportedGlyphKnowledge.surplus(0, 0, 0, 16));
        // ...and with surplus levels known, only the real surplus is removed.
        assertEquals(2, ImportedGlyphKnowledge.surplus(3, 3, 1, 40));
    }

    @Test
    void aBonusThatHasNotCountedTheNewLevelsYetIsNotReducedBelowWhatItCounted() {
        // Ars raises the bonus on the next spell-book use; until then it counted only 17.
        assertEquals(0, ImportedGlyphKnowledge.surplus(20, 3, 1, 17));
        assertEquals(1, ImportedGlyphKnowledge.surplus(20, 3, 1, 19));
    }
}
