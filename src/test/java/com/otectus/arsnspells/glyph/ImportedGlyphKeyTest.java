package com.otectus.arsnspells.glyph;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The frozen 3.4.0 glyph id encoding: exact form, round trip, and no two keys sharing an id. */
class ImportedGlyphKeyTest {

    private static ResourceLocation id(String raw) {
        return ResourceLocation.parse(raw);
    }

    @Test
    void encodingIsFrozen() {
        ImportedGlyphKey key = new ImportedGlyphKey(id("irons_spellbooks:firebolt"), 3);
        assertEquals("ars_n_spells:iron/irons_spellbooks/firebolt/level_3", key.partId().toString());
        assertEquals("ars_n_spells:glyph_iron/irons_spellbooks/firebolt/level_3", key.itemId().toString());
    }

    @Test
    void decodeRoundTripsIncludingNestedPaths() {
        for (ImportedGlyphKey key : new ImportedGlyphKey[]{
            new ImportedGlyphKey(id("irons_spellbooks:firebolt"), 1),
            new ImportedGlyphKey(id("irons_spellbooks:firebolt"), 10),
            new ImportedGlyphKey(id("addon:school/fire/blast"), 99),
            new ImportedGlyphKey(id("a_b:c_d"), 2)}) {
            assertEquals(Optional.of(key), ImportedGlyphKey.decode(key.partId()), key.toString());
        }
    }

    @Test
    void distinctKeysNeverCollide() {
        String[] spells = {"irons_spellbooks:firebolt", "irons_spellbooks:fire_bolt", "irons_spellbooks:fire/bolt",
            "irons_spellbooks_firebolt:x", "addon:irons_spellbooks/firebolt"};
        Set<ResourceLocation> ids = new HashSet<>();
        Set<ResourceLocation> items = new HashSet<>();
        int keys = 0;
        for (String spell : spells) {
            for (int level = 1; level <= 12; level++) {
                ids.add(new ImportedGlyphKey(id(spell), level).partId());
                items.add(new ImportedGlyphKey(id(spell), level).itemId());
                keys++;
            }
        }
        assertEquals(keys, ids.size(), "every (spell, level) pair has its own part id");
        assertEquals(keys, items.size(), "every (spell, level) pair has its own item id");
    }

    @Test
    void decodeRejectsAnythingElse() {
        for (String raw : new String[]{"ars_nouveau:glyph_break", "ars_n_spells:iron/irons_spellbooks/firebolt",
            "ars_n_spells:iron/irons_spellbooks/firebolt/level_0", "ars_n_spells:iron/irons_spellbooks/firebolt/level_x",
            "ars_n_spells:iron//firebolt/level_1", "ars_n_spells:iron/irons_spellbooks/firebolt/level_100",
            "ars_n_spells:glyph_iron/irons_spellbooks/firebolt/level_3", "ars_n_spells:ars_cross_1"}) {
            assertTrue(ImportedGlyphKey.decode(id(raw)).isEmpty(), raw);
        }
    }

    @Test
    void levelsOutsideTheRangeAreNotKeys() {
        assertThrows(IllegalArgumentException.class, () -> new ImportedGlyphKey(id("irons_spellbooks:firebolt"), 0));
        assertThrows(IllegalArgumentException.class, () -> new ImportedGlyphKey(id("irons_spellbooks:firebolt"), ImportedGlyphKey.MAX_LEVEL + 1));
    }

    @Test
    void knowledgeBonusCountsEachSpellOnce() {
        ResourceLocation firebolt = id("irons_spellbooks:firebolt");
        ResourceLocation heal = id("irons_spellbooks:heal");
        assertEquals(0, GlyphKnowledgeBonus.excessVariantKeys(java.util.List.of(new ImportedGlyphKey(firebolt, 1))));
        assertEquals(2, GlyphKnowledgeBonus.excessVariantKeys(java.util.List.of(new ImportedGlyphKey(firebolt, 1),
            new ImportedGlyphKey(firebolt, 2), new ImportedGlyphKey(firebolt, 3), new ImportedGlyphKey(heal, 5))));
        // A repeated sync listing the same glyphs twice cannot inflate the excess.
        assertEquals(1, GlyphKnowledgeBonus.excessVariantKeys(java.util.List.of(new ImportedGlyphKey(firebolt, 1),
            new ImportedGlyphKey(firebolt, 2), new ImportedGlyphKey(firebolt, 2), new ImportedGlyphKey(firebolt, 1))));
    }
}
