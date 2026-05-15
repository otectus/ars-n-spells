package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bootstrap-free unit tests for {@link CrossCastValidator}. Uses the
 * package-private {@code validate} overload that accepts a synthetic Iron's
 * registry predicate, so the test classpath does not need {@link
 * net.minecraftforge.fml.ModList} initialized.
 */
class CrossCastValidatorTest {

    private static final Predicate<ResourceLocation> ACCEPT_ALL = id -> true;
    private static final Predicate<ResourceLocation> REJECT_ALL = id -> false;

    @Test
    void invalidIndex_returnsIndexOutOfRange() {
        CompoundTag entry = arsEntry();
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(entry, 5, 3, ACCEPT_ALL);
        assertFalse(r.ok());
        assertEquals("arsnspells.crosscast.invalid.index_out_of_range", r.reasonKey());
    }

    @Test
    void negativeIndex_returnsIndexOutOfRange() {
        CompoundTag entry = arsEntry();
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(entry, -1, 1, ACCEPT_ALL);
        assertFalse(r.ok());
        assertEquals("arsnspells.crosscast.invalid.index_out_of_range", r.reasonKey());
    }

    @Test
    void emptyListSize_returnsIndexOutOfRange() {
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(new CompoundTag(), 0, 0, ACCEPT_ALL);
        assertFalse(r.ok());
        assertEquals("arsnspells.crosscast.invalid.index_out_of_range", r.reasonKey());
    }

    @Test
    void nullEntry_returnsEmptyPayload() {
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(null, 0, 1, ACCEPT_ALL);
        assertFalse(r.ok());
        assertEquals("arsnspells.crosscast.invalid.empty_payload", r.reasonKey());
    }

    @Test
    void emptyEntry_returnsEmptyPayload() {
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(new CompoundTag(), 0, 1, ACCEPT_ALL);
        assertFalse(r.ok());
        assertEquals("arsnspells.crosscast.invalid.empty_payload", r.reasonKey());
    }

    @Test
    void missingSpellTypeAndUnknownNamespace_returnsSpellType() {
        CompoundTag entry = new CompoundTag();
        entry.putString(CrossCastNbt.TAG_SPELL_ID, "unknown_mod:fireball");
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(entry, 0, 1, ACCEPT_ALL);
        assertFalse(r.ok());
        assertEquals("arsnspells.crosscast.invalid.spell_type", r.reasonKey());
    }

    @Test
    void arsEntryWithoutArsSpellTag_returnsArsSpellMissing() {
        CompoundTag entry = new CompoundTag();
        entry.putString(CrossCastNbt.TAG_SPELL_TYPE, CrossSpellType.ARS_NOUVEAU.name());
        entry.putString(CrossCastNbt.TAG_SPELL_ID, "ars_nouveau:spell");
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(entry, 0, 1, ACCEPT_ALL);
        assertFalse(r.ok());
        assertEquals("arsnspells.crosscast.invalid.ars_spell_missing", r.reasonKey());
    }

    @Test
    void arsEntryWithEmptyArsSpellTag_returnsArsSpellEmpty() {
        CompoundTag entry = new CompoundTag();
        entry.putString(CrossCastNbt.TAG_SPELL_TYPE, CrossSpellType.ARS_NOUVEAU.name());
        entry.putString(CrossCastNbt.TAG_SPELL_ID, "ars_nouveau:spell");
        entry.put(CrossCastNbt.TAG_ARS_SPELL, new CompoundTag());
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(entry, 0, 1, ACCEPT_ALL);
        assertFalse(r.ok());
        assertEquals("arsnspells.crosscast.invalid.ars_spell_empty", r.reasonKey());
    }

    @Test
    void wellFormedArsEntry_succeeds() {
        CompoundTag entry = arsEntry();
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(entry, 0, 1, ACCEPT_ALL);
        assertTrue(r.ok());
        assertEquals(CrossSpellType.ARS_NOUVEAU, r.resolvedType());
        assertNotNull(r.spellId());
        assertEquals("ars_nouveau", r.spellId().getNamespace());
    }

    @Test
    void ironEntryWithInvalidSpellId_returnsIronId() {
        CompoundTag entry = new CompoundTag();
        entry.putString(CrossCastNbt.TAG_SPELL_TYPE, CrossSpellType.IRONS_SPELLBOOKS.name());
        entry.putString(CrossCastNbt.TAG_SPELL_ID, "::not-a-resource-loc");
        entry.putInt(CrossCastNbt.TAG_SPELL_LEVEL, 1);
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(entry, 0, 1, ACCEPT_ALL);
        assertFalse(r.ok());
        assertEquals("arsnspells.crosscast.invalid.iron_id", r.reasonKey());
    }

    @Test
    void ironEntryWithZeroLevel_returnsIronLevel() {
        CompoundTag entry = new CompoundTag();
        entry.putString(CrossCastNbt.TAG_SPELL_TYPE, CrossSpellType.IRONS_SPELLBOOKS.name());
        entry.putString(CrossCastNbt.TAG_SPELL_ID, "irons_spellbooks:fireball");
        entry.putInt(CrossCastNbt.TAG_SPELL_LEVEL, 0);
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(entry, 0, 1, ACCEPT_ALL);
        assertFalse(r.ok());
        assertEquals("arsnspells.crosscast.invalid.iron_level", r.reasonKey());
    }

    @Test
    void ironEntryUnresolvedByRegistry_returnsIronUnresolved() {
        CompoundTag entry = new CompoundTag();
        entry.putString(CrossCastNbt.TAG_SPELL_TYPE, CrossSpellType.IRONS_SPELLBOOKS.name());
        entry.putString(CrossCastNbt.TAG_SPELL_ID, "irons_spellbooks:phantom_spell");
        entry.putInt(CrossCastNbt.TAG_SPELL_LEVEL, 1);
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(entry, 0, 1, REJECT_ALL);
        assertFalse(r.ok());
        assertEquals("arsnspells.crosscast.invalid.iron_unresolved", r.reasonKey());
    }

    @Test
    void wellFormedIronEntry_succeeds() {
        CompoundTag entry = new CompoundTag();
        entry.putString(CrossCastNbt.TAG_SPELL_TYPE, CrossSpellType.IRONS_SPELLBOOKS.name());
        entry.putString(CrossCastNbt.TAG_SPELL_ID, "irons_spellbooks:fireball");
        entry.putInt(CrossCastNbt.TAG_SPELL_LEVEL, 3);
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(entry, 0, 1, ACCEPT_ALL);
        assertTrue(r.ok());
        assertEquals(CrossSpellType.IRONS_SPELLBOOKS, r.resolvedType());
        assertEquals("irons_spellbooks", r.spellId().getNamespace());
        assertEquals("fireball", r.spellId().getPath());
    }

    @Test
    void typeInference_fallsBackToNamespaceWhenSpellTypeMissing() {
        CompoundTag entry = new CompoundTag();
        // No TAG_SPELL_TYPE — must infer from spell_id namespace
        entry.putString(CrossCastNbt.TAG_SPELL_ID, "irons_spellbooks:fireball");
        entry.putInt(CrossCastNbt.TAG_SPELL_LEVEL, 1);
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(entry, 0, 1, ACCEPT_ALL);
        assertTrue(r.ok());
        assertEquals(CrossSpellType.IRONS_SPELLBOOKS, r.resolvedType());
    }

    @Test
    void typeInference_invalidEnumValueFallsThroughToNamespace() {
        CompoundTag entry = new CompoundTag();
        entry.putString(CrossCastNbt.TAG_SPELL_TYPE, "TOTALLY_NOT_A_TYPE");
        entry.putString(CrossCastNbt.TAG_SPELL_ID, "ars_nouveau:spell");
        entry.put(CrossCastNbt.TAG_ARS_SPELL, nonEmptyArsTag());
        CrossCastValidator.ValidationResult r =
            CrossCastValidator.validate(entry, 0, 1, ACCEPT_ALL);
        assertTrue(r.ok(),
            "invalid enum should not abort validation; namespace inference rescues it");
        assertEquals(CrossSpellType.ARS_NOUVEAU, r.resolvedType());
    }

    @Test
    void resolveType_returnsNullWhenNothingResolvable() {
        CompoundTag entry = new CompoundTag();
        entry.putString(CrossCastNbt.TAG_SPELL_ID, "unknown_mod:thing");
        assertNull(CrossCastValidator.resolveType(entry));
    }

    private static CompoundTag arsEntry() {
        CompoundTag entry = new CompoundTag();
        entry.putString(CrossCastNbt.TAG_SPELL_TYPE, CrossSpellType.ARS_NOUVEAU.name());
        entry.putString(CrossCastNbt.TAG_SPELL_ID, "ars_nouveau:spell");
        entry.put(CrossCastNbt.TAG_ARS_SPELL, nonEmptyArsTag());
        return entry;
    }

    private static CompoundTag nonEmptyArsTag() {
        CompoundTag tag = new CompoundTag();
        // Any non-empty inner tag; the validator only checks isEmpty().
        tag.putString("placeholder", "x");
        return tag;
    }
}
