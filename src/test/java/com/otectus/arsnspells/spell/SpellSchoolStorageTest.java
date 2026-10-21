package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the explicit school is stored on a Forge entry, what an entry from before the field
 * existed means, and how duplicate detection treats school variants and cosmetic differences.
 * NBT only, no bootstrap.
 */
class SpellSchoolStorageTest {

    /** A Forge 1.20.1-shaped Ars payload: cosmetic keys around a two-part recipe. */
    private static CompoundTag payload(String spellName, int colour) {
        CompoundTag recipe = new CompoundTag();
        recipe.putString("part0", "ars_nouveau:glyph_projectile");
        recipe.putString("part1", "ars_nouveau:glyph_harm");
        recipe.putInt("size", 2);
        CompoundTag tag = new CompoundTag();
        tag.putString("name", spellName);
        CompoundTag color = new CompoundTag();
        color.putInt("r", colour);
        tag.put("spellColor", color);
        tag.put("sound", new CompoundTag());
        tag.put("recipe", recipe);
        return tag;
    }

    private static CompoundTag entry(CompoundTag stack) {
        return stack.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND).getCompound(0);
    }

    private static CompoundTag saveAndLoad(CompoundTag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(bytes));
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    @Test
    void explicitSchool_isWrittenOnTheEntry_andSurvivesSaveAndLoad() throws IOException {
        CompoundTag stack = new CompoundTag();
        CrossCastNbt.addArsEntryWithMetaToTag(stack, IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1,
            payload("Bolt", 1), CrossCastNbt.NO_PROXY_POOL_ID, "Bolt", "fire", "spark", "irons_spellbooks:ice");

        CompoundTag reloaded = saveAndLoad(stack);
        assertEquals("irons_spellbooks:ice", CrossCastNbt.storedSchool(entry(reloaded)));
        assertEquals("fire", entry(reloaded).getString(CrossCastNbt.TAG_NATURE),
            "the cosmetic background is stored separately and unchanged");
        assertEquals(stack, reloaded);
    }

    @Test
    void automatic_writesNoSchoolKey_soTheEntryMatchesTheLegacyShape() {
        CompoundTag withNull = new CompoundTag();
        CrossCastNbt.addArsEntryWithMetaToTag(withNull, IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1,
            payload("Bolt", 1), CrossCastNbt.NO_PROXY_POOL_ID, "Bolt", "fire", "spark", null);
        CompoundTag legacy = new CompoundTag();
        CrossCastNbt.addArsEntryWithMetaToTag(legacy, IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1,
            payload("Bolt", 1), CrossCastNbt.NO_PROXY_POOL_ID, "Bolt", "fire", "spark");

        assertFalse(entry(withNull).contains(CrossCastNbt.TAG_SCHOOL));
        assertEquals(legacy, withNull, "Automatic must be byte-identical to an entry written before schools");
    }

    @Test
    void aLegacyNature_isNeverReadAsASchool() {
        CompoundTag stack = new CompoundTag();
        CrossCastNbt.addArsEntryWithMetaToTag(stack, IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1,
            payload("Old", 1), 3, "Old", "ice", "drop");

        assertEquals("", CrossCastNbt.storedSchool(entry(stack)), "a 3.3.x entry with an ice frame is Automatic");
        assertEquals(null, SpellSchoolChoice.effective(CrossCastNbt.storedSchool(entry(stack)), true, key -> true));
    }

    @Test
    void settingTheSchool_touchesOnlyTheSchoolKey() {
        CompoundTag stack = new CompoundTag();
        CrossCastNbt.addArsEntryWithMetaToTag(stack, IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1,
            payload("Bolt", 1), 2, "Bolt", "fire", "spark");
        CompoundTag before = entry(stack).copy();

        CrossCastNbt.setStoredSchool(entry(stack), "irons_spellbooks:holy");
        CompoundTag withSchool = entry(stack).copy();
        withSchool.remove(CrossCastNbt.TAG_SCHOOL);
        assertEquals(before, withSchool, "the spell, name, background, icon and pool id must not change");
        assertEquals("irons_spellbooks:holy", CrossCastNbt.storedSchool(entry(stack)));

        CrossCastNbt.setStoredSchool(entry(stack), "");
        assertEquals(before, entry(stack), "clearing returns the entry to its Automatic shape exactly");
    }

    @Test
    void dedup_cosmeticOnlyDifferencesAreDuplicates() {
        CompoundTag book = new CompoundTag();
        CrossCastNbt.addArsEntryWithMetaToTag(book, IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1,
            payload("Firebolt", 1), 1, "Firebolt", "fire", "flame");

        assertTrue(IronsBookBindingUtil.tagContainsEquivalentArsSpell(book, payload("Renamed", 9)),
            "a different spell name, particle colour, background or icon must not bypass dedup");
    }

    @Test
    void dedup_schoolVariantsAreDistinct_andTheSameSchoolIsADuplicate() {
        CompoundTag book = new CompoundTag();
        CrossCastNbt.addArsEntryWithMetaToTag(book, IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1,
            payload("Bolt", 1), 1, "Bolt", "", "", "irons_spellbooks:ice");

        assertFalse(IronsBookBindingUtil.tagContainsEquivalentArsSpell(book, payload("Bolt", 1), null),
            "the Automatic variant of the same glyphs is a different spell");
        assertFalse(IronsBookBindingUtil.tagContainsEquivalentArsSpell(book, payload("Bolt", 1), "irons_spellbooks:fire"),
            "a different chosen school is a different spell");
        assertTrue(IronsBookBindingUtil.tagContainsEquivalentArsSpell(book, payload("Bolt", 1), "irons_spellbooks:ice"));
        assertTrue(IronsBookBindingUtil.tagContainsEquivalentArsSpell(book, payload("Other", 4), "ice"),
            "the bare built-in name and the namespaced id are the same school");
    }

    @Test
    void dedup_legacyEntryCountsAsAutomatic() {
        CompoundTag book = new CompoundTag();
        CrossCastNbt.addCrossModSpellToTag(book, IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1,
            CrossSpellType.ARS_NOUVEAU, payload("Legacy", 1));

        assertTrue(IronsBookBindingUtil.tagContainsEquivalentArsSpell(book, payload("Legacy", 1), ""));
        assertFalse(IronsBookBindingUtil.tagContainsEquivalentArsSpell(book, payload("Legacy", 1), "irons_spellbooks:ice"));
    }

    @Test
    void mechanicalPayload_dropsOnlyCosmeticKeys() {
        CompoundTag mechanical = IronsBookBindingUtil.mechanicalPayload(payload("Name", 5));
        assertEquals(1, mechanical.size(), "name, spellColor and sound are cosmetic; the recipe is not");
        assertTrue(mechanical.contains("recipe", Tag.TAG_COMPOUND));
    }
}
