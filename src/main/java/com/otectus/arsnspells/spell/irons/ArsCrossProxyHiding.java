package com.otectus.arsnspells.spell.irons;

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
     * True when {@code stack} carries any native Iron's spell container at all.
     *
     * <p>Lets callers distinguish "a scroll holding a genuine spell" from "not a spell item",
     * which {@link #isProxyOnlyStack(ItemStack)} alone cannot: both return false there.
     */
    public static boolean isSpellContainerStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ISpellContainer.isSpellContainer(stack);
    }
}
