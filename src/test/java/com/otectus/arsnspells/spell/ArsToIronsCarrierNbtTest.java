package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the NBT shape an exported Ars-on-Iron's-scroll carrier carries. The
 * carrier reuses the exact {@code arsnspells:cross_spells} schema (placeholder
 * id, level 1, ARS_NOUVEAU type, opaque {@code ars_spell} blob) so it casts
 * through the same pipeline as every other inscribed item. CompoundTag-only so
 * it runs without Minecraft's item-registry bootstrap.
 */
class ArsToIronsCarrierNbtTest {

    private static CompoundTag arsPayload(String body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", body);
        return tag;
    }

    @Test
    void carrierEntry_hasPlaceholderIdArsTypeAndRoundTripsPayload() {
        CompoundTag scroll = new CompoundTag();
        CompoundTag ars = arsPayload("glyph_touch,glyph_break");

        CrossCastNbt.addCrossModSpellToTag(scroll,
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU, ars);

        assertTrue(CrossCastNbt.hasCrossModSpells(scroll));
        ListTag list = scroll.getList(CrossCastNbt.TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        assertEquals(1, list.size(), "carrier holds exactly one entry");

        CompoundTag entry = list.getCompound(0);
        assertEquals("ars_nouveau:spell", entry.getString(CrossCastNbt.TAG_SPELL_ID),
            "Ars exports share the placeholder id");
        assertEquals(CrossSpellType.ARS_NOUVEAU, CrossCastValidator.resolveType(entry));
        assertEquals(ars, entry.getCompound(CrossCastNbt.TAG_ARS_SPELL),
            "the opaque Ars payload must round-trip verbatim");
    }

    @Test
    void extractSingleArsEntry_returnsTheOpaquePayload() {
        CompoundTag scroll = new CompoundTag();
        CompoundTag ars = arsPayload("glyph_projectile,glyph_harm");
        CrossCastNbt.addCrossModSpellToTag(scroll,
            IronsBookBindingUtil.ARS_PLACEHOLDER_ID, 1, CrossSpellType.ARS_NOUVEAU, ars);

        Optional<CompoundTag> extracted =
            IronsBookBindingUtil.extractSingleArsEntryFromTag(scroll);
        assertTrue(extracted.isPresent(), "a valid single-Ars carrier must yield its payload");
        assertEquals(ars, extracted.get());
    }
}
