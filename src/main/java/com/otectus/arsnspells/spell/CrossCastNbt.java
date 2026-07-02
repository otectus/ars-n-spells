package com.otectus.arsnspells.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

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

    // 3.0.0 native-proxy fields. Additive on each Ars entry: they let an Ars
    // spell surface as its own entry in Iron's native spell wheel without
    // touching Iron's own container schema. Entries written before 3.0.0 simply
    // lack these keys and fall back to the sidecar right-click cast path.
    /** Pool id k (1..N): which registered ars_cross_k proxy drives this entry, and its wheel identity. */
    public static final String TAG_PROXY_POOL_ID = "proxy_pool_id";
    /** Player-authored display name shown in Iron's wheel for this entry. */
    public static final String TAG_CUSTOM_NAME = "custom_name";
    /** Chosen nature (string key); cosmetic tint plus optional affinity tie-in. */
    public static final String TAG_NATURE = "nature";
    /** Rudimentary icon: a shipped symbol key. */
    public static final String TAG_ICON_SYMBOL = "icon_symbol";
    /** Rudimentary icon: packed ARGB tint applied to the symbol/background. */
    public static final String TAG_ICON_COLOR = "icon_color";

    /** Sentinel meaning "no proxy pool slot allocated for this entry". */
    public static final int NO_PROXY_POOL_ID = -1;

    /**
     * Number of distinct native-wheel proxy slots per Iron's spellbook. Declared
     * here (Iron's-free) so binding/allocation logic and unit tests can reference
     * it without classloading the Iron's-gated {@code ArsCrossProxyRegistry}. The
     * registry's {@code POOL_SIZE} mirrors this value.
     */
    public static final int PROXY_POOL_SIZE = 8;

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
     * Append an Ars inscription entry carrying the 3.0.0 native-proxy metadata
     * (pool id, custom name, nature, icon). The base {@code spellId}/{@code level}
     * /{@code type}/{@code arsSpellTag} are written exactly as the legacy overload;
     * the extra fields are written only when meaningful so unchanged behaviour is
     * preserved for callers that pass {@code NO_PROXY_POOL_ID} / nulls. Mutates
     * {@code stackTag} in place. Returns the index of the appended entry.
     */
    public static int addArsEntryWithMetaToTag(CompoundTag stackTag,
                                               ResourceLocation spellId,
                                               int spellLevel,
                                               CompoundTag arsSpellTag,
                                               int proxyPoolId,
                                               String customName,
                                               String nature,
                                               String iconSymbol,
                                               int iconColor) {
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
        spellData.putString(TAG_SPELL_TYPE, CrossSpellType.ARS_NOUVEAU.name());
        if (arsSpellTag != null) {
            spellData.put(TAG_ARS_SPELL, arsSpellTag);
        }
        if (proxyPoolId != NO_PROXY_POOL_ID) {
            spellData.putInt(TAG_PROXY_POOL_ID, proxyPoolId);
        }
        if (customName != null && !customName.isEmpty()) {
            spellData.putString(TAG_CUSTOM_NAME, customName);
        }
        if (nature != null && !nature.isEmpty()) {
            spellData.putString(TAG_NATURE, nature);
        }
        if (iconSymbol != null && !iconSymbol.isEmpty()) {
            spellData.putString(TAG_ICON_SYMBOL, iconSymbol);
        }
        if (iconColor != 0) {
            spellData.putInt(TAG_ICON_COLOR, iconColor);
        }

        spellList.add(spellData);
        stackTag.put(TAG_CROSS_MOD_SPELLS, spellList);
        return spellList.size() - 1;
    }

    /**
     * The set of proxy pool ids currently in use across {@code stackTag}'s Ars
     * entries. Entries without a {@link #TAG_PROXY_POOL_ID} contribute nothing.
     */
    public static Set<Integer> usedProxyPoolIds(CompoundTag stackTag) {
        Set<Integer> used = new HashSet<>();
        if (stackTag == null || !stackTag.contains(TAG_CROSS_MOD_SPELLS, Tag.TAG_LIST)) {
            return used;
        }
        ListTag list = stackTag.getList(TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.contains(TAG_PROXY_POOL_ID, Tag.TAG_INT)) {
                used.add(entry.getInt(TAG_PROXY_POOL_ID));
            }
        }
        return used;
    }

    /**
     * Smallest free proxy pool id in {@code [1, maxPool]} for {@code stackTag},
     * or {@link #NO_PROXY_POOL_ID} when all {@code maxPool} ids are taken. The
     * wheel de-duplicates by spell id, so every entry on one book must own a
     * distinct id.
     */
    public static int allocateProxyPoolId(CompoundTag stackTag, int maxPool) {
        Set<Integer> used = usedProxyPoolIds(stackTag);
        for (int k = 1; k <= maxPool; k++) {
            if (!used.contains(k)) {
                return k;
            }
        }
        return NO_PROXY_POOL_ID;
    }

    /**
     * The Ars entry whose {@link #TAG_PROXY_POOL_ID} equals {@code poolId}, or
     * {@code null} if no such entry exists. Used by the proxy spell's cast
     * delegation to map a wheel slot back to its serialized Ars payload.
     */
    public static CompoundTag findEntryByProxyPoolId(CompoundTag stackTag, int poolId) {
        if (stackTag == null || !stackTag.contains(TAG_CROSS_MOD_SPELLS, Tag.TAG_LIST)) {
            return null;
        }
        ListTag list = stackTag.getList(TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.contains(TAG_PROXY_POOL_ID, Tag.TAG_INT)
                && entry.getInt(TAG_PROXY_POOL_ID) == poolId) {
                return entry;
            }
        }
        return null;
    }

    /** Count of Ars-type entries (those carrying an {@link #TAG_ARS_SPELL} payload). */
    public static int countArsEntries(CompoundTag stackTag) {
        if (stackTag == null || !stackTag.contains(TAG_CROSS_MOD_SPELLS, Tag.TAG_LIST)) {
            return 0;
        }
        ListTag list = stackTag.getList(TAG_CROSS_MOD_SPELLS, Tag.TAG_COMPOUND);
        int count = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).contains(TAG_ARS_SPELL, Tag.TAG_COMPOUND)) {
                count++;
            }
        }
        return count;
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
