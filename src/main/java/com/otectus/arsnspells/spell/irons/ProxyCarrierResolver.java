package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.spell.CrossCastNbt;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Exact native context wins; a context-free lookup may only use an unambiguous carrier. */
public final class ProxyCarrierResolver {
    private ProxyCarrierResolver() {}
    public record Carrier(ItemStack stack, CompoundTag entry) {}

    public static Carrier from(ItemStack stack, int poolId) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return null;
        CompoundTag entry = CrossCastNbt.findEntryByProxyPoolId(stack.getTag(), poolId);
        return entry == null ? null : new Carrier(stack, entry);
    }

    public static Carrier casting(Player player, MagicData data, int poolId) {
        ItemStack nativeItem = data.getPlayerCastingItem();
        // A real native item without this entry is an authoritative miss. Falling
        // through would silently cast a different book sharing the eight proxy IDs.
        if (nativeItem != null && !nativeItem.isEmpty()) return from(nativeItem, poolId);
        String slot = data.getCastingEquipmentSlot();
        if (SpellSelectionManager.MAINHAND.equals(slot)) return from(player.getMainHandItem(), poolId);
        if (SpellSelectionManager.OFFHAND.equals(slot)) return from(player.getOffhandItem(), poolId);
        if (data.getCastSource() == CastSource.SPELLBOOK) {
            ItemStack equipped = Utils.getPlayerSpellbookStack(player);
            if (equipped != null && !equipped.isEmpty()) return from(equipped, poolId);
        }
        return unambiguous(player, poolId);
    }

    public static Carrier unambiguous(Player player, int poolId) {
        return unique(poolId, Utils.getPlayerSpellbookStack(player), player.getMainHandItem(), player.getOffhandItem());
    }

    public static Carrier unique(int poolId, ItemStack... candidates) {
        Carrier found = null;
        for (ItemStack stack : candidates) {
            Carrier next = from(stack, poolId);
            if (next == null) continue;
            if (found != null && found.stack() != next.stack()) return null;
            found = next;
        }
        return found;
    }
}
