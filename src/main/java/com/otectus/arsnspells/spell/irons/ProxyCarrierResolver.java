package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.spell.CrossModSpell;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Component adapter: native context wins, context-free lookup refuses ambiguous books.
 *
 * <p>Iron's records three things about a cast in flight: its source, the equipment slot it was
 * started from and the casting item, and they do not all name the carrier of the selected spell.
 * A hotkey cast ({@code Utils.serverSideInitiateCast}) passes an empty casting item and the slot
 * the selection came from. A casting implement such as a staff ({@code ServerPlayerEvents.onUseItem}
 * for any item with the {@code casting_implement} component, verified against Iron's 1.21.1-3.16.3)
 * passes itself as the casting item and the hand it is held in as the slot, while the source still
 * says {@code SPELLBOOK} because the selection came from the equipped book. 3.3.5 treated a
 * non-empty casting item as the authoritative carrier, so every staff right-click of a bound Ars
 * spell was refused with "no carried spellbook holds a sidecar entry" although the hotkey cast of
 * the same wheel slot worked. The casting item names the carrier only when it is itself a spell
 * container, that is when the selection came from it (a scroll, an imbued weapon); otherwise the
 * source and the slot do. Iron's puts a spellbook on the wheel only while it is equipped, so a
 * {@code SPELLBOOK} cast belongs to the equipped book and to nothing else.
 */
public final class ProxyCarrierResolver {
    private ProxyCarrierResolver() {}
    public record Carrier(ItemStack stack, CrossModSpell entry) {}
    public static Carrier from(ItemStack stack, int poolId) {
        if (stack == null || stack.isEmpty()) return null;
        return CrossModSpellComponents.findEntryByProxyPoolId(stack, poolId)
            .map(entry -> new Carrier(stack, entry)).orElse(null);
    }
    public static Carrier casting(Player player, MagicData data, int poolId) {
        // A spell container that started its own cast is the exact carrier. A miss on it is a
        // miss, never a reason to cast a different book sharing the eight proxy ids.
        ItemStack nativeItem = data.getPlayerCastingItem();
        if (isSpellContainer(nativeItem)) return from(nativeItem, poolId);
        // Selected from the equipped spellbook, whichever item began the cast: a staff is the
        // casting item and its hand is the slot, and neither can carry the entry.
        if (data.getCastSource() == CastSource.SPELLBOOK) {
            ItemStack equipped = Utils.getPlayerSpellbookStack(player);
            if (equipped != null && !equipped.isEmpty()) return from(equipped, poolId);
        }
        // Selected from a container held in that hand (an imbued weapon).
        String slot = data.getCastingEquipmentSlot();
        if (SpellSelectionManager.MAINHAND.equals(slot) && isSpellContainer(player.getMainHandItem()))
            return from(player.getMainHandItem(), poolId);
        if (SpellSelectionManager.OFFHAND.equals(slot) && isSpellContainer(player.getOffhandItem()))
            return from(player.getOffhandItem(), poolId);
        return unambiguous(player, poolId);
    }
    /** Whether {@code stack} can carry a wheel selection at all: a staff cannot, a book or scroll can. */
    private static boolean isSpellContainer(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ISpellContainer.isSpellContainer(stack);
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
