package com.otectus.arsnspells.network;

import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.menu.GlyphPressMenu;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The press inventory as the requesting client saw it: a digest of all four slots and whether a
 * job runs. A request made against anything else is stale and changes nothing.
 */
public final class GlyphPressRevision {
    private GlyphPressRevision() {}

    public static String of(GlyphPressMenu menu) {
        if (menu.slots.size() < GlyphPressBlockEntity.SLOT_COUNT) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int slot = 0; slot < GlyphPressBlockEntity.SLOT_COUNT; slot++) {
                ItemStack stack = menu.getSlot(slot).getItem();
                digest.update(String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()))
                    .getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Integer.toString(stack.getCount()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                if (stack.hasTag()) digest.update(stack.getTag().toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 1);
            }
            digest.update((byte) (menu.data(GlyphPressMenu.D_STATE) == GlyphPressBlockEntity.State.PROCESSING.ordinal() ? 1 : 0));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
