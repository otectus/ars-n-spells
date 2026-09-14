package com.otectus.arsnspells.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

/** Stable request-time carrier revision. A fingerprint is a stale-input check, never authority. */
public final class CarrierFingerprint {
    private static final int MAX_BYTES = 65_536;
    private CarrierFingerprint() {}

    /** Empty means oversized/recoverable data; the saved stack is never rewritten or discarded. */
    public static String of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int[] remaining = {MAX_BYTES};
            append(digest, ForgeRegistries.ITEMS.getKey(stack.getItem()) + ":" + stack.getCount(), remaining);
            if (stack.hasTag()) appendTag(digest, stack.getTag(), remaining, 0);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IllegalArgumentException oversized) {
            return "";
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java must support SHA-256", impossible);
        }
    }

    private static void appendTag(MessageDigest digest, Tag tag, int[] remaining, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Payload depth exceeds request budget");
        append(digest, Byte.toString(tag.getId()), remaining);
        if (tag instanceof CompoundTag compound) {
            for (String key : new TreeSet<>(compound.getAllKeys())) {
                append(digest, key, remaining);
                appendTag(digest, compound.get(key), remaining, depth + 1);
            }
        } else if (tag instanceof ListTag list) {
            for (Tag element : list) appendTag(digest, element, remaining, depth + 1);
        } else {
            append(digest, tag.toString(), remaining);
        }
        append(digest, "end", remaining);
    }

    private static void append(MessageDigest digest, String value, int[] remaining) {
        if (value.length() > remaining[0]) throw new IllegalArgumentException("Payload exceeds request budget");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        remaining[0] -= bytes.length + 1;
        if (remaining[0] < 0) throw new IllegalArgumentException("Payload exceeds request budget");
        digest.update(bytes);
        digest.update((byte) 0);
    }
}
