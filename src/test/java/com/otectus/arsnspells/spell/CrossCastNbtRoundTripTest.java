package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the inscribe / uninscribe / re-inscribe round-trip at the CompoundTag
 * level. CrossCastNbt is deliberately Bootstrap-free so this can run as a
 * plain JUnit test without booting Minecraft's registries -- a regression
 * here is the kind that silently corrupts saves, so it should never need a
 * full server boot to catch.
 */
class CrossCastNbtRoundTripTest {

    private static final ResourceLocation IRONS_FIREBALL =
        new ResourceLocation("irons_spellbooks", "fireball");

    @Test
    void emptyTag_inscribeThenClear_returnsToBitIdenticalEmpty() {
        CompoundTag stack = new CompoundTag();
        CompoundTag baseline = stack.copy();

        CrossCastNbt.addCrossModSpellToTag(stack, IRONS_FIREBALL, 3,
            CrossSpellType.IRONS_SPELLBOOKS, null);
        assertTrue(CrossCastNbt.hasCrossModSpells(stack),
            "after add, the cross-spells list must be present and non-empty");
        assertNotEquals(baseline, stack,
            "the inscribed tag must differ from the baseline");

        CrossCastNbt.clearCrossModSpellsFromTag(stack);
        assertEquals(baseline, stack,
            "after clear, the tag must be bit-identical to the empty baseline");
    }

    @Test
    void preservesUnrelatedRootNbtAcrossInscribeAndClear() {
        CompoundTag stack = new CompoundTag();
        stack.putInt("Damage", 5);
        stack.putString("Note", "preserved");
        CompoundTag baselineWithSiblings = stack.copy();

        CrossCastNbt.addCrossModSpellToTag(stack, IRONS_FIREBALL, 1,
            CrossSpellType.IRONS_SPELLBOOKS, null);
        CrossCastNbt.clearCrossModSpellsFromTag(stack);

        assertEquals(baselineWithSiblings, stack,
            "clearing the inscription must leave unrelated root NBT untouched");
    }

    @Test
    void inscribeUninscribeInscribe_producesIdenticalNbtBothTimes() {
        CompoundTag stack = new CompoundTag();
        stack.putInt("Damage", 7);

        CrossCastNbt.addCrossModSpellToTag(stack, IRONS_FIREBALL, 2,
            CrossSpellType.IRONS_SPELLBOOKS, null);
        CompoundTag firstInscribed = stack.copy();

        CrossCastNbt.clearCrossModSpellsFromTag(stack);
        CompoundTag uninscribed = stack.copy();

        CrossCastNbt.addCrossModSpellToTag(stack, IRONS_FIREBALL, 2,
            CrossSpellType.IRONS_SPELLBOOKS, null);
        CompoundTag secondInscribed = stack.copy();

        CompoundTag expectedUninscribed = new CompoundTag();
        expectedUninscribed.putInt("Damage", 7);
        assertEquals(expectedUninscribed, uninscribed,
            "uninscribed tag must equal the unrelated-NBT-only baseline");
        assertEquals(firstInscribed, secondInscribed,
            "re-inscribing the same spell must produce bit-identical NBT");
    }

    @Test
    void cycleIndexAlsoStrippedByClear() {
        CompoundTag stack = new CompoundTag();
        CrossCastNbt.addCrossModSpellToTag(stack, IRONS_FIREBALL, 1,
            CrossSpellType.IRONS_SPELLBOOKS, null);
        // Simulate sneak-cycle leaving an index behind.
        stack.putInt(CrossCastNbt.TAG_SPELL_INDEX, 1);
        assertTrue(stack.contains(CrossCastNbt.TAG_SPELL_INDEX),
            "preconditon: cycle index is on the stack");

        CrossCastNbt.clearCrossModSpellsFromTag(stack);

        assertFalse(stack.contains(CrossCastNbt.TAG_SPELL_INDEX),
            "clear must drop the cycle index, not just the spell list");
        assertFalse(stack.contains(CrossCastNbt.TAG_CROSS_MOD_SPELLS),
            "clear must drop the spell list");
    }

    @Test
    void multipleInscriptions_listedInInsertionOrder_areAllStrippedByClear() {
        CompoundTag stack = new CompoundTag();
        CrossCastNbt.addCrossModSpellToTag(stack, IRONS_FIREBALL, 1,
            CrossSpellType.IRONS_SPELLBOOKS, null);
        CrossCastNbt.addCrossModSpellToTag(stack,
            new ResourceLocation("irons_spellbooks", "lightning_lance"), 2,
            CrossSpellType.IRONS_SPELLBOOKS, null);
        CrossCastNbt.addCrossModSpellToTag(stack,
            new ResourceLocation("ars_nouveau", "spell"), 1,
            CrossSpellType.ARS_NOUVEAU, new CompoundTag());

        assertTrue(CrossCastNbt.hasCrossModSpells(stack));

        CrossCastNbt.clearCrossModSpellsFromTag(stack);
        assertFalse(CrossCastNbt.hasCrossModSpells(stack),
            "clear must remove every entry, not just the most recent");
        assertEquals(new CompoundTag(), stack,
            "after clearing all inscriptions, the tag is empty");
    }
}
