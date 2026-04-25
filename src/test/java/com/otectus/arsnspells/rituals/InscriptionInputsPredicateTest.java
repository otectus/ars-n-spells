package com.otectus.arsnspells.rituals;

import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.CrossSpellType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the disambiguation predicates that the inscribe / uninscribe rituals
 * use to bucket items in their work area. These run against {@link CompoundTag}
 * directly so the contract is locked in without needing Minecraft's item
 * registry bootstrap.
 *
 * Coverage maps to the smoke-test edge cases called out in the Phase 3 spec:
 * empty range, single source, single target, two sources, two targets, and
 * (most importantly) "conflicting NBT at non-root paths should still pass" --
 * the inscription ritual only forbids Ars spells at the root.
 */
class InscriptionInputsPredicateTest {

    @Test
    void emptyTag_isNotInscribed() {
        assertFalse(InscriptionInputs.isInscribed(new CompoundTag()),
            "a fresh empty tag must not classify as inscribed");
    }

    @Test
    void nullTag_isNotInscribed() {
        assertFalse(InscriptionInputs.isInscribed((CompoundTag) null),
            "null tag is the no-NBT case and must not classify as inscribed");
    }

    @Test
    void emptyListTag_isNotInscribed() {
        CompoundTag stack = new CompoundTag();
        // Manually create the list key but leave it empty -- this is the
        // shape a stack might land in if we ever cleared entries without
        // dropping the list key. The predicate must treat empty-list as
        // not inscribed.
        CrossCastNbt.addCrossModSpellToTag(stack,
            new ResourceLocation("irons_spellbooks", "fireball"), 1,
            CrossSpellType.IRONS_SPELLBOOKS, null);
        // Drop the entry but keep the (now-empty) list shape via a manual edit
        stack.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, 10).clear();
        assertFalse(InscriptionInputs.isInscribed(stack),
            "an empty cross-spells list must not count as inscribed");
    }

    @Test
    void inscribedTag_isInscribed() {
        CompoundTag stack = new CompoundTag();
        CrossCastNbt.addCrossModSpellToTag(stack,
            new ResourceLocation("irons_spellbooks", "fireball"), 1,
            CrossSpellType.IRONS_SPELLBOOKS, null);
        assertTrue(InscriptionInputs.isInscribed(stack));
    }

    @Test
    void unrelatedNbtAtRoot_isNotInscribed() {
        CompoundTag stack = new CompoundTag();
        stack.putInt("Damage", 7);
        stack.putBoolean("Unbreakable", true);
        stack.putString("RandomKey", "hello");
        assertFalse(InscriptionInputs.isInscribed(stack),
            "non-cross-cast root NBT must not register as inscribed");
    }

    @Test
    void conflictingNbtAtNonRootPaths_isNotInscribed() {
        // Spec edge case: "target with conflicting NBT at non-root paths
        // (this last one *should pass*)". The predicate only inspects the
        // root cross-spells key; nested NBT under any other path is fine.
        CompoundTag stack = new CompoundTag();
        CompoundTag display = new CompoundTag();
        display.putString("Name", "{\"text\":\"My Item\"}");
        stack.put("display", display);

        CompoundTag fakeNested = new CompoundTag();
        fakeNested.putString("looks_like", CrossCastNbt.TAG_CROSS_MOD_SPELLS);
        stack.put("BlockEntityTag", fakeNested);

        assertFalse(InscriptionInputs.isInscribed(stack),
            "non-root NBT, even with confusingly-named keys, must not count as inscribed");
    }

    @Test
    void wrongTagTypeAtCrossSpellsKey_isNotInscribed() {
        // Defensive: if a third party wrote the key with the wrong NBT type
        // (e.g., a string instead of a list), the predicate must reject
        // gracefully rather than throw.
        CompoundTag stack = new CompoundTag();
        stack.putString(CrossCastNbt.TAG_CROSS_MOD_SPELLS, "not-a-list");
        assertFalse(InscriptionInputs.isInscribed(stack),
            "wrong tag type at the cross-spells key must not register as inscribed");
    }
}
