package com.otectus.arsnspells.glyph;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImportedGlyphKeyTest {

    @Test
    void encodingIsFrozen() {
        // Changing this string orphans every learned glyph and saved spell: it must never move.
        assertEquals("ars_n_spells:iron/irons_spellbooks/firebolt/level_3",
            new ImportedGlyphKey(new ResourceLocation("irons_spellbooks", "firebolt"), 3).glyphId().toString());
    }

    @Test
    void decodesWhatItEncodes() {
        for (String spell : new String[] {"irons_spellbooks:firebolt", "addon:fire/bolt", "a.b-c:x_y/level_2/z"}) {
            for (int level = 1; level <= ImportedGlyphKey.MAX_LEVEL; level++) {
                ImportedGlyphKey key = new ImportedGlyphKey(new ResourceLocation(spell), level);
                assertEquals(key, ImportedGlyphKey.decode(key.glyphId()).orElseThrow(), key.toString());
            }
        }
    }

    @Test
    void distinctPairsNeverCollide() {
        // Spell paths that contain the level marker themselves are the adversarial case.
        String[] spells = {"irons_spellbooks:firebolt", "irons_spellbooks:firebolt/level_1", "irons_spellbooks:fire/bolt",
            "irons_spellbooks:fire", "irons_spellbooks_x:firebolt", "irons:spellbooks/firebolt", "x:level_1", "x:level_1/level_1"};
        Map<ResourceLocation, ImportedGlyphKey> seen = new HashMap<>();
        for (String spell : spells) {
            for (int level = 1; level <= 12; level++) {
                ImportedGlyphKey key = new ImportedGlyphKey(new ResourceLocation(spell), level);
                ImportedGlyphKey previous = seen.put(key.glyphId(), key);
                assertNull(previous, () -> key + " collides with " + previous);
            }
        }
    }

    @Test
    void rejectsForeignAndNonCanonicalIds() {
        assertTrue(ImportedGlyphKey.decode(new ResourceLocation("ars_nouveau", "glyph_break")).isEmpty());
        assertTrue(ImportedGlyphKey.decode(new ResourceLocation("ars_n_spells", "ars_cross_1")).isEmpty());
        assertTrue(ImportedGlyphKey.decode(new ResourceLocation("ars_n_spells", "iron/irons_spellbooks/firebolt/level_03")).isEmpty());
        assertTrue(ImportedGlyphKey.decode(new ResourceLocation("ars_n_spells", "iron/irons_spellbooks/firebolt/level_0")).isEmpty());
        assertTrue(ImportedGlyphKey.decode(new ResourceLocation("ars_n_spells", "iron/irons_spellbooks/firebolt/level_100")).isEmpty());
        assertTrue(ImportedGlyphKey.decode(new ResourceLocation("ars_n_spells", "iron/irons_spellbooks/level_3")).isEmpty());
        assertTrue(ImportedGlyphKey.decode(new ResourceLocation("ars_n_spells", "iron/irons_spellbooks/firebolt/three")).isEmpty());
        assertTrue(ImportedGlyphKey.decode(new ResourceLocation("other", "iron/irons_spellbooks/firebolt/level_3")).isEmpty());
    }

    @Test
    void refusesOutOfRangeLevels() {
        ResourceLocation spell = new ResourceLocation("irons_spellbooks", "firebolt");
        assertThrows(IllegalArgumentException.class, () -> new ImportedGlyphKey(spell, 0));
        assertThrows(IllegalArgumentException.class, () -> new ImportedGlyphKey(spell, ImportedGlyphKey.MAX_LEVEL + 1));
    }
}
