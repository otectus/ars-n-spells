package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.compat.IronsCompat;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.world.item.ItemStack;

/**
 * Builds the one fixture audit V18 turns on: a scroll that is filled in <em>Iron's own</em>
 * container, not in ANS sidecar NBT.
 *
 * <p>The defect was that "blank" meant "carries no ANS data", so a scroll another mod had
 * already written was read as blank and overwritten. A fixture built out of ANS NBT cannot
 * catch that regression -- only a natively filled scroll can, which means calling
 * {@code ISpellContainer.createScrollContainer} exactly as Iron's inscription table does.
 *
 * <p><b>Iron's-isolated and test-only</b>, in the shape {@link IronsProxyCastDriver}
 * established: every Iron's import lives here, no method signature names an Iron's type, and
 * callers gate on {@code IronsCompat.isLoaded()} before touching the class at all -- so the
 * JVM never resolves it on the Iron's-absent profile.
 */
final class IronsLoomFixtures {

    private IronsLoomFixtures() {
    }

    /** A real, blank Iron's scroll: the legal target. */
    static ItemStack blankScroll() {
        if (!IronsCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(ItemRegistry.SCROLL.get());
    }

    /**
     * A real Iron's scroll carrying a real Iron's spell in its native container, with no ANS
     * data anywhere on it. This is the stack the old classifier called blank.
     */
    static ItemStack nativelyFilledScroll() {
        if (!IronsCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        AbstractSpell spell = firstRealSpell();
        if (spell == null) {
            return ItemStack.EMPTY;
        }
        ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.set(scroll, ISpellContainer.createScrollContainer(spell, 1, scroll));
        return scroll;
    }

    /** What Iron's itself says about the stack's container -- the assertion side of the test. */
    static boolean nativeContainerIsEmpty(ItemStack stack) {
        if (!IronsCompat.isLoaded() || !ISpellContainer.isSpellContainer(stack)) {
            return true;
        }
        ISpellContainer container = ISpellContainer.get(stack);
        return container == null || container.isEmpty();
    }

    private static AbstractSpell firstRealSpell() {
        for (AbstractSpell spell : SpellRegistry.getEnabledSpells()) {
            if (spell != SpellRegistry.none() && spell.getSpellId() != null) {
                return spell;
            }
        }
        return null;
    }
}
