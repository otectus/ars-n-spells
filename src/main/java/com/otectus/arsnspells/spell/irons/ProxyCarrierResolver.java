package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.spell.CrossModSpell;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Component adapter: native context wins, context-free lookup refuses ambiguous books. */
public final class ProxyCarrierResolver {
    private ProxyCarrierResolver() {}
    public record Carrier(ItemStack stack, CrossModSpell entry) {}
    public static Carrier from(ItemStack stack, int poolId) {
        if (stack == null || stack.isEmpty()) return null;
        return CrossModSpellComponents.findEntryByProxyPoolId(stack, poolId)
            .map(entry -> new Carrier(stack, entry)).orElse(null);
    }
    public static Carrier casting(Player player, MagicData data, int poolId) {
        ItemStack item = data.getPlayerCastingItem();
        if (item != null && !item.isEmpty()) return from(item, poolId);
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
