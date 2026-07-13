package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every exported Ars spell shares the same placeholder id ({@code ars_nouveau:spell}),
 * so duplicate detection must key off the serialized {@code arsSpellTag} payload,
 * not the id. These tests lock that contract in at the component-list level
 * (bootstrap-free port of the 1.20.1 CompoundTag-level test).
 */
class ArsExportDeduplicationTest {

    private static CompoundTag arsPayload(String body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", body);
        return tag;
    }

    private static CrossModSpellList bookWith(CompoundTag payload) {
        return CrossModSpellComponents.withArsEntry(CrossModSpellList.EMPTY,
            CrossModSpellComponents.ARS_PLACEHOLDER_ID, 1, payload,
            CrossModSpellComponents.NO_PROXY_POOL_ID, null, null, null);
    }

    @Test
    void samePlaceholderIdDifferentPayload_isNotADuplicate() {
        CrossModSpellList book = bookWith(arsPayload("a"));

        // Identical placeholder id, different recipe -- must NOT be treated as a duplicate.
        assertFalse(CrossModSpellComponents.containsEquivalentArsSpell(book, arsPayload("b")),
            "dedup must not collapse distinct spells that share the placeholder id");
    }

    @Test
    void identicalPayload_isADuplicate() {
        CompoundTag a = arsPayload("a");
        CrossModSpellList book = bookWith(a);

        assertTrue(CrossModSpellComponents.containsEquivalentArsSpell(book, a.copy()),
            "an equal arsSpellTag payload must be detected as already present");
    }

    @Test
    void emptyBook_hasNoEquivalent() {
        assertFalse(CrossModSpellComponents.containsEquivalentArsSpell(CrossModSpellList.EMPTY, arsPayload("a")),
            "a book with no inscriptions can never contain a duplicate");
    }

    @Test
    void nullPayload_isNeverADuplicate() {
        CrossModSpellList book = bookWith(arsPayload("a"));
        assertFalse(CrossModSpellComponents.containsEquivalentArsSpell(book, null));
    }
}
