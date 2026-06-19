package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every exported Ars spell shares the same placeholder id ({@code ars_nouveau:spell}),
 * so duplicate detection must key off the serialized {@code ars_spell} payload,
 * not the id. These tests lock that contract in at the CompoundTag level.
 */
class ArsExportDeduplicationTest {

    private static CompoundTag arsPayload(String body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", body);
        return tag;
    }

    @Test
    void samePlaceholderIdDifferentPayload_isNotADuplicate() {
        CompoundTag book = new CompoundTag();
        CrossCastNbt.addCrossModSpellToTag(book,
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU, arsPayload("a"));

        // Identical placeholder id, different recipe -- must NOT be treated as a duplicate.
        assertFalse(IronsBookBindingUtil.tagContainsEquivalentArsSpell(book, arsPayload("b")),
            "dedup must not collapse distinct spells that share the placeholder id");
    }

    @Test
    void identicalPayload_isADuplicate() {
        CompoundTag book = new CompoundTag();
        CompoundTag a = arsPayload("a");
        CrossCastNbt.addCrossModSpellToTag(book,
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU, a);

        assertTrue(IronsBookBindingUtil.tagContainsEquivalentArsSpell(book, a.copy()),
            "an equal ars_spell payload must be detected as already present");
    }

    @Test
    void emptyBook_hasNoEquivalent() {
        assertFalse(IronsBookBindingUtil.tagContainsEquivalentArsSpell(new CompoundTag(), arsPayload("a")),
            "a book with no inscriptions can never contain a duplicate");
    }
}
