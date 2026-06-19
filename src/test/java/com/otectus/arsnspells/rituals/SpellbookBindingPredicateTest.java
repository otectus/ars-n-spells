package com.otectus.arsnspells.rituals;

import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.CrossSpellType;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the carrier-scroll acceptance rule the binding ritual's classifier
 * relies on: a carrier must hold exactly one Ars entry with a non-empty payload.
 * Tested against the CompoundTag seam ({@code extractSingleArsEntryFromTag}) so
 * it runs without bootstrapping the item registry.
 */
class SpellbookBindingPredicateTest {

    private static CompoundTag arsPayload(String body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", body);
        return tag;
    }

    private static void addArs(CompoundTag tag, CompoundTag payload) {
        CrossCastNbt.addCrossModSpellToTag(tag,
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU, payload);
    }

    @Test
    void noEntries_isRejected() {
        assertFalse(IronsBookBindingUtil.extractSingleArsEntryFromTag(new CompoundTag()).isPresent(),
            "a scroll with no inscription is not a carrier");
    }

    @Test
    void twoEntries_isRejected() {
        CompoundTag tag = new CompoundTag();
        addArs(tag, arsPayload("a"));
        addArs(tag, arsPayload("b"));
        assertFalse(IronsBookBindingUtil.extractSingleArsEntryFromTag(tag).isPresent(),
            "binding requires an unambiguous single-entry carrier");
    }

    @Test
    void ironOnlyEntry_isRejected() {
        CompoundTag tag = new CompoundTag();
        CrossCastNbt.addCrossModSpellToTag(tag,
            new ResourceLocation("irons_spellbooks", "fireball"), 3,
            CrossSpellType.IRONS_SPELLBOOKS, null);
        assertFalse(IronsBookBindingUtil.extractSingleArsEntryFromTag(tag).isPresent(),
            "an Iron's-only scroll is not an Ars carrier");
    }

    @Test
    void emptyArsPayload_isRejected() {
        CompoundTag tag = new CompoundTag();
        addArs(tag, new CompoundTag());
        assertFalse(IronsBookBindingUtil.extractSingleArsEntryFromTag(tag).isPresent(),
            "an entry with an empty ars_spell payload is not a valid carrier");
    }

    @Test
    void singleArsEntry_isAccepted() {
        CompoundTag tag = new CompoundTag();
        addArs(tag, arsPayload("glyph_heal"));
        assertTrue(IronsBookBindingUtil.extractSingleArsEntryFromTag(tag).isPresent(),
            "exactly one non-empty Ars entry is a valid carrier");
    }
}
