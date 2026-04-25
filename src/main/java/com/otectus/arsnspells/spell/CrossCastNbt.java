package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Pure NBT manipulation for the cross-cast inscription payload. Extracted
 * from {@link CrossCastingHandler} so the inscribe / uninscribe round-trip
 * can be exercised in unit tests against {@link CompoundTag} alone, without
 * the full Minecraft {@code Bootstrap} dance.
 *
 * The on-stack representation is a single root-level list under
 * {@link #TAG_CROSS_MOD_SPELLS}; each entry is a {@link CompoundTag} carrying
 * the spell id, level, originating mod, and (for Ars spells) the serialized
 * spell sub-compound. The cycle index lives in a separate root-level int
 * under {@link #TAG_SPELL_INDEX}, written only when the player sneak-cycles.
 */
public final class CrossCastNbt {
    public static final String TAG_CROSS_MOD_SPELLS = "arsnspells:cross_spells";
    public static final String TAG_SPELL_INDEX = "arsnspells:cross_spell_index";
    public static final String TAG_SPELL_ID = "spell_id";
    public static final String TAG_SPELL_LEVEL = "spell_level";
    public static final String TAG_SPELL_TYPE = "spell_type";
    public static final String TAG_ARS_SPELL = "ars_spell";
    public static final String TAG_CAST_SOURCE = "cast_source";

    private CrossCastNbt() {}

    /**
     * Append an inscription entry to {@code stackTag}'s cross-spell list,
     * creating the list if necessary. Mutates {@code stackTag} in place.
     */
    public static void addCrossModSpellToTag(CompoundTag stackTag,
                                             ResourceLocation spellId,
                                             int spellLevel,
                                             CrossSpellType type,
                                             CompoundTag arsSpellTag) {
        ListTag spellList;
        if (stackTag.contains(TAG_CROSS_MOD_SPELLS, Tag.TAG_LIST)) {
            spellList = stackTag.getList(TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        } else {
            spellList = new ListTag();
        }

        CompoundTag spellData = new CompoundTag();
        if (spellId != null) {
            spellData.putString(TAG_SPELL_ID, spellId.toString());
        }
        spellData.putInt(TAG_SPELL_LEVEL, spellLevel);
        if (type != null) {
            spellData.putString(TAG_SPELL_TYPE, type.name());
        }
        if (arsSpellTag != null) {
            spellData.put(TAG_ARS_SPELL, arsSpellTag);
        }

        spellList.add(spellData);
        stackTag.put(TAG_CROSS_MOD_SPELLS, spellList);
    }

    /**
     * Remove every inscription artifact from {@code stackTag}. Drops both the
     * spell list and the cycle index, leaving any unrelated NBT untouched.
     * Mutates {@code stackTag} in place.
     */
    public static void clearCrossModSpellsFromTag(CompoundTag stackTag) {
        stackTag.remove(TAG_CROSS_MOD_SPELLS);
        stackTag.remove(TAG_SPELL_INDEX);
    }

    /**
     * Strip every inscription artifact from {@code stack}. The result is
     * bit-identical to a stack that was never inscribed: the cross-spell
     * list and cycle index are removed, and an empty residual root tag is
     * collapsed to {@code null} so equality checks against a fresh blank
     * target succeed.
     */
    public static void clearCrossModSpells(ItemStack stack) {
        if (!stack.hasTag()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        clearCrossModSpellsFromTag(tag);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    /**
     * True when {@code stackTag} carries at least one inscription entry.
     */
    public static boolean hasCrossModSpells(CompoundTag stackTag) {
        if (stackTag == null) return false;
        if (!stackTag.contains(TAG_CROSS_MOD_SPELLS, Tag.TAG_LIST)) return false;
        return !stackTag.getList(TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND).isEmpty();
    }
}
