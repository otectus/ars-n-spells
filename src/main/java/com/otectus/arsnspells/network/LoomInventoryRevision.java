package com.otectus.arsnspells.network;

import com.otectus.arsnspells.menu.SpellLoomMenu;
import com.otectus.arsnspells.util.PayloadBudget;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

/** Full native item data matters: native spells can change without any ANS inscription data. */
public final class LoomInventoryRevision {
    private LoomInventoryRevision() {}
    public static String of(SpellLoomMenu menu) {
        if (menu.getBlockEntity() == null || menu.slots.size() < 3) return "";
        try {
            CompoundTag inventory = new CompoundTag();
            for (int slot = 0; slot < 3; slot++) {
                ItemStack stack = menu.getSlot(slot).getItem();
                Tag saved = stack.saveOptional(menu.getBlockEntity().getLevel().registryAccess());
                if (!PayloadBudget.tag(saved)) return "";
                inventory.put(Integer.toString(slot), saved);
            }
            if (!PayloadBudget.tag(inventory)) return "";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            appendTag(digest, inventory);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IllegalArgumentException invalid) { return ""; }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void appendTag(MessageDigest digest, Tag tag) {
        append(digest, Byte.toString(tag.getId()));
        if (tag instanceof CompoundTag compound) {
            for (String key : new TreeSet<>(compound.getAllKeys())) { append(digest, key); appendTag(digest, compound.get(key)); }
        } else if (tag instanceof ListTag list) {
            for (Tag member : list) appendTag(digest, member);
        } else append(digest, tag.toString());
        append(digest, "end");
    }
    private static void append(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
    }
}
