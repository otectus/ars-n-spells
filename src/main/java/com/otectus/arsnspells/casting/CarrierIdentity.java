package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.contract.CarrierPolicy;
import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.CrossModSpellList;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Derives what a cast came from, and how that thing behaves (audit V02).
 *
 * <p>Closes the finding that carrier semantics were read from a serialized {@code CastSource}
 * riding along in the item's own cross-cast data, defaulting to {@code SCROLL}. That value is
 * edit-controllable and it survives being copied onto a different item, and in the pinned Iron's
 * 3.16.3 both {@code CastSource.consumesMana()} and {@code CastSource.respectsCooldown()} return
 * <b>false</b> for {@code SCROLL} - {@code AbstractSpell.castSpell} only subtracts the event cost
 * when the source consumes mana. The default therefore made every cross-cast free and
 * cooldown-exempt.
 *
 * <p>A {@link CarrierPolicy} is derived here from the carrier's <b>item kind at the moment of the
 * cast</b>, and from nothing else. The serialized cast source is ignored for billing.
 */
public final class CarrierIdentity {

    private CarrierIdentity() {}

    /**
     * How {@code stack} behaves as a spell carrier.
     *
     * <ul>
     *   <li>A real Iron's scroll is consumed by casting: {@link
     *       CarrierPolicy#CONSUMABLE_SCROLL_SEMANTICS}.</li>
     *   <li>An Iron's spellbook, or any other item carrying an ANS inscription (a wand, a focus,
     *       a proxy book), is reusable: {@link CarrierPolicy#REUSABLE_BOOK_SEMANTICS}.</li>
     *   <li>Anything with no inscription is not an ANS carrier at all; the owning mod prices and
     *       charges it: {@link CarrierPolicy#NATIVE_ONLY}.</li>
     * </ul>
     */
    public static CarrierPolicy policyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return CarrierPolicy.NATIVE_ONLY;
        }
        if (IronsBookBindingUtil.isIronsScroll(stack)) {
            return CarrierPolicy.CONSUMABLE_SCROLL_SEMANTICS;
        }
        if (!CrossModSpellComponents.has(stack)) {
            return CarrierPolicy.NATIVE_ONLY;
        }
        return CarrierPolicy.REUSABLE_BOOK_SEMANTICS;
    }

    /**
     * The opaque identity the {@link com.otectus.arsnspells.contract.AttemptLedger} files an
     * attempt under.
     *
     * <p>Item id plus the selected inscription, which is what distinguishes two attempts from the
     * same book. Deliberately not the {@code ItemStack} itself: the stack can be swapped, split
     * or copied while a long cast is in flight, and the ledger must still be able to find the
     * attempt it opened.
     */
    public static String identityOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        CrossModSpellList list = CrossModSpellComponents.get(stack);
        return item + "#" + (list.isEmpty() ? -1 : list.normalizedIndex());
    }

    /**
     * The revision of the carrier's spell payload, so a caller can detect that the inscription
     * changed under an open attempt. The list's size and selection are the only parts a player
     * can change mid-cast (by inscribing or cycling), which is exactly what must invalidate it.
     */
    public static int payloadRevision(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        CrossModSpellList list = CrossModSpellComponents.get(stack);
        return list.isEmpty() ? 0 : 31 * list.size() + list.normalizedIndex();
    }
}
