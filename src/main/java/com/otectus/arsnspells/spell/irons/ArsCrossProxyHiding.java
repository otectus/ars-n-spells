package com.otectus.arsnspells.spell.irons;

import com.otectus.arsnspells.spell.CrossModSpellComponents;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Recognizes item stacks that exist only because ANS registers proxy spells, so they can be
 * kept out of player-facing item lists.
 *
 * <p><b>Why this is needed.</b> {@code ars_cross_1..8} are real registered
 * {@code AbstractSpell}s — they have to be, or Iron's native spell wheel could not resolve
 * them. Iron's then treats them like any other spell: {@code CreativeTabRegistry} builds one
 * scroll per enabled spell into the Scrolls creative tab, which is exactly where JEI and EMI
 * source their ingredient lists. The result was eight ghost {@code ars_cross_*} scrolls in
 * every recipe viewer, none of which does anything on its own.
 *
 * <p>Filtering at the creative tab rather than in a JEI plugin is deliberate: the tab is the
 * upstream source, so one removal covers the creative menu, JEI, EMI, and any other viewer
 * that reads tab contents — with no recipe-viewer dependency and nothing to gate on JEI being
 * installed. Recipe <em>categories</em> that enumerate spells directly still need viewer-side
 * hiding; see {@code compat.jei}.
 *
 * <p>These spells stay enabled and castable. Hiding an item is not disabling a spell: a proxy
 * bound into a spellbook resolves by id at cast time and never goes near an item list.
 *
 * <p><b>Iron's-isolated.</b> Callers gate on {@code IronsCompat.isLoaded()}.
 */
public final class ArsCrossProxyHiding {

    private ArsCrossProxyHiding() {}

    /**
     * True when {@code stack} is a scroll (or any spell container) whose only spell is an ANS
     * proxy.
     *
     * <p>Checks every active slot rather than just index 0, and requires that <em>all</em> of
     * them be proxies, so a real player-made book that happens to carry a bound Ars entry
     * alongside genuine Iron's spells is never mistaken for a generated ghost.
     */
    public static boolean isProxyOnlyStack(ItemStack stack) {
        // A player's book or an inscription-bearing carrier is never generated proxy debris,
        // even when its only native slots are ANS proxies. Preserve recoverable payloads too.
        if (stack != null && !stack.isEmpty() && (IronsBookBindingUtil.isIronsSpellBook(stack)
            || CrossModSpellComponents.has(stack))) return false;
        if (stack == null || stack.isEmpty() || !ISpellContainer.isSpellContainer(stack)) {
            return false;
        }
        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null) {
            return false;
        }
        boolean sawProxy = false;
        for (SpellSlot slot : container.getActiveSpells()) {
            SpellData data = slot == null ? null : slot.spellData();
            if (data == null || data.getSpell() == null) {
                continue;
            }
            if (ArsCrossProxyRegistry.poolIdOf(data.getSpell().getSpellResource()) < 0) {
                return false; // a genuine spell is present — leave the stack alone
            }
            sawProxy = true;
        }
        return sawProxy;
    }

    /**
     * Turn a stray proxy-bearing scroll back into a blank Iron's scroll, returning true when
     * the stack was rewritten.
     *
     * <p><b>What these are.</b> Until {@code ArsCrossProxySpell.allowLooting()} existed, the
     * proxies passed Iron's {@code SpellFilter} and could be rolled into any
     * {@code randomize_spell} table - chests, scroll pouches, mob drops, wandering trades. The
     * resulting scroll has no book behind it, so it can never do anything but fail the
     * carrier lookup in {@code onCast}. Blanking it is the honest outcome: the player is not
     * left holding a permanent dud, and nothing is destroyed - a container-less
     * {@code irons_spellbooks:scroll} is the same item Iron's itself shows as "None Scroll".
     *
     * <p><b>Lazy, never swept.</b> Callers invoke this only where the stack is already being
     * decoded for another reason - the scroll-use mixin and the failed proxy cast. Scanning
     * inventories on a tick to find these would mean an {@code ISpellContainer} codec decode
     * per scroll per tick, which is exactly the cost {@code CarrierReconciler}'s design note
     * rules out.
     *
     * <p><b>Cannot touch a real item.</b> Three guards must all hold: the stack is an Iron's
     * scroll, it carries no ANS sidecar (so a genuine ANS carrier is excluded even though its
     * native container is empty and could never match anyway), and
     * {@link #isProxyOnlyStack(ItemStack)} agrees that every active slot is a proxy.
     */
    public static boolean neutralizeStrayProxyScroll(ItemStack stack) {
        if (!IronsBookBindingUtil.isIronsScroll(stack)) {
            return false;
        }
        if (CrossModSpellComponents.has(stack)) {
            return false; // an ANS carrier, not loot debris
        }
        // Ordered before isProxyOnlyStack: that reads through ISpellContainer.get, which ends
        // in DataResult.getOrThrow and throws rather than returning null on a bad decode.
        if (!IronsScrollFactory.hasReadableContainer(stack) || !isProxyOnlyStack(stack)) {
            return false;
        }
        ISpellContainer.remove(stack);
        return true;
    }

    /**
     * True when {@code stack} carries any native Iron's spell container at all.
     *
     * <p>Lets callers distinguish "a scroll holding a genuine spell" from "not a spell item",
     * which {@link #isProxyOnlyStack(ItemStack)} alone cannot: both return false there.
     */
    public static boolean isSpellContainerStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ISpellContainer.isSpellContainer(stack);
    }
}
