package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that binding an Ars spell onto a spellbook coexists with Iron's own
 * native container. The ANS sidecar only ever touches its own root keys, so a
 * book's {@code ISB_Spells} compound (and any unrelated root NBT) must survive
 * both binding and a later uninscribe. CompoundTag-only, Bootstrap-free.
 */
class IronsBookBindingNbtTest {

    private static final String ISB = "ISB_Spells";

    private static CompoundTag arsPayload(String body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", body);
        return tag;
    }

    /** A simulated native Iron's container as it would appear on a real spellbook. */
    private static CompoundTag nativeContainer() {
        CompoundTag isb = new CompoundTag();
        isb.putInt("maxSpells", 3);
        isb.putBoolean("mustEquip", true);
        ListTag data = new ListTag();
        CompoundTag slot = new CompoundTag();
        slot.putString("id", "irons_spellbooks:fireball");
        slot.putInt("level", 3);
        slot.putInt("index", 0);
        data.add(slot);
        isb.put("data", data);
        return isb;
    }

    @Test
    void appendingArsEntry_preservesNativeContainerAndOtherRootKeys() {
        CompoundTag book = new CompoundTag();
        CompoundTag isb = nativeContainer();
        book.put(ISB, isb);
        book.putInt("Damage", 2);
        CompoundTag isbBaseline = isb.copy();

        CrossCastNbt.addCrossModSpellToTag(book,
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU, arsPayload("heal"));

        assertEquals(isbBaseline, book.getCompound(ISB),
            "binding must not mutate the native ISB_Spells container");
        assertEquals(2, book.getInt("Damage"), "unrelated root NBT must survive");
        assertTrue(CrossCastNbt.hasCrossModSpells(book));
    }

    @Test
    void multipleDistinctArsEntries_coexistOnOneBook() {
        CompoundTag book = new CompoundTag();
        book.put(ISB, nativeContainer());

        CrossCastNbt.addCrossModSpellToTag(book,
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU, arsPayload("a"));
        CrossCastNbt.addCrossModSpellToTag(book,
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU, arsPayload("b"));

        ListTag list = book.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        assertEquals(2, list.size(),
            "the sidecar list model holds multiple Ars exports without native slots");
    }

    @Test
    void payloadIsStoredVerbatim_includingNestedListsAndUnknownKeys() {
        // The Ars spell tag is opaque, upstream-owned data: ANS must copy it byte-for-byte
        // and never rewrite or normalize it. Build a deliberately gnarly payload (nested
        // compound, a list, an int-array, and keys ANS knows nothing about) and assert the
        // stored copy equals the input exactly. Uses the same append primitive
        // appendArsSpellToBook routes through, so the verbatim contract is pinned here.
        CompoundTag complex = new CompoundTag();
        complex.putString("name", "My Custom Spell");
        complex.putInt("unknown_future_field", 42);
        complex.putIntArray("colors", new int[] {1, 2, 3});
        CompoundTag nested = new CompoundTag();
        nested.putBoolean("flag", true);
        nested.put("ints", new IntArrayTag(new int[] {9, 8, 7}));
        complex.put("meta", nested);
        ListTag recipe = new ListTag();
        recipe.add(StringTag.valueOf("glyph_touch"));
        recipe.add(StringTag.valueOf("glyph_break"));
        complex.put("recipe", recipe);
        CompoundTag expected = complex.copy();

        CompoundTag book = new CompoundTag();
        CrossCastNbt.addCrossModSpellToTag(book,
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU, complex);

        CompoundTag stored = book.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND)
            .getCompound(0).getCompound(CrossCastNbt.TAG_ARS_SPELL);
        assertEquals(expected, stored,
            "the opaque ars_spell payload must be stored verbatim, nested keys and all");
    }

    @Test
    void uninscribe_removesAnsEntriesButLeavesNativeContainer() {
        CompoundTag book = new CompoundTag();
        book.put(ISB, nativeContainer());
        CompoundTag isbBaseline = book.getCompound(ISB).copy();

        CrossCastNbt.addCrossModSpellToTag(book,
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU, arsPayload("c"));
        CrossCastNbt.clearCrossModSpellsFromTag(book);

        assertFalse(CrossCastNbt.hasCrossModSpells(book), "uninscribe drops the ANS sidecar");
        assertFalse(book.contains(CrossCastNbt.TAG_CROSS_MOD_SPELLS),
            "the cross-spells key must be gone, not just emptied");
        assertEquals(isbBaseline, book.getCompound(ISB),
            "the native container is untouched by uninscribe");
    }
}
