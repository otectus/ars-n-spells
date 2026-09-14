package com.otectus.arsnspells.util;

import net.minecraft.nbt.*;

/** Casting/transport limits only. A refused saved payload is never rewritten or discarded. */
public final class PayloadBudget {
    public static final int MAX_BYTES = 65_536;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_GLYPHS = 256;
    private PayloadBudget() {}

    public static boolean name(String value) {
        return value != null && value.codePointCount(0, value.length()) <= 40
            && value.codePoints().noneMatch(Character::isISOControl);
    }

    public static boolean tag(Tag value) {
        return value != null && bounded(value, 0, new int[]{MAX_BYTES, 8192});
    }

    public static boolean arsSpell(CompoundTag value) {
        if (!tag(value)) return false;
        Tag raw = value.get("recipe");
        if (raw instanceof CompoundTag recipe) {
            int size = recipe.getInt("size");
            if (size < 1 || size > MAX_GLYPHS) return false;
            for (int i = 0; i < size; i++) if (!glyphId(recipe.getString("part" + i))) return false;
        } else if (raw instanceof ListTag recipe) {
            if (recipe.isEmpty() || recipe.size() > MAX_GLYPHS) return false;
            for (int i = 0; i < recipe.size(); i++) if (!glyphId(recipe.getString(i))) return false;
        } else return false;
        return true;
    }

    private static boolean glyphId(String value) {
        return value != null && !value.isEmpty() && value.length() <= 256
            && value.chars().noneMatch(Character::isISOControl);
    }

    private static boolean bounded(Tag tag, int depth, int[] budget) {
        if (tag == null || depth > MAX_DEPTH || --budget[1] < 0 || !spend(budget, 8)) return false;
        if (tag instanceof CompoundTag compound) {
            if (compound.size() > budget[1]) return false;
            for (String key : compound.getAllKeys()) {
                if (key.length() > 256 || !spend(budget, 3L * key.length() + 2)
                    || !bounded(compound.get(key), depth + 1, budget)) return false;
            }
        } else if (tag instanceof ListTag list) {
            if (list.size() > budget[1]) return false;
            for (Tag child : list) if (!bounded(child, depth + 1, budget)) return false;
        } else if (tag instanceof StringTag text) {
            String value = text.getAsString();
            if (value.length() > 4096 || !spend(budget, 3L * value.length() + 2)) return false;
        } else if (tag instanceof ByteArrayTag array) {
            return spend(budget, array.size());
        } else if (tag instanceof IntArrayTag array) {
            return spend(budget, 4L * array.size());
        } else if (tag instanceof LongArrayTag array) {
            return spend(budget, 8L * array.size());
        }
        return true;
    }

    private static boolean spend(int[] budget, long amount) {
        if (amount > budget[0]) return false;
        budget[0] -= (int) amount;
        return true;
    }
}
