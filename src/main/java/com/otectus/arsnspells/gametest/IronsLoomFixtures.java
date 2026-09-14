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
    static void assertNativeBookRoundTrip(net.minecraft.gametest.framework.GameTestHelper helper) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .filter(candidate -> com.otectus.arsnspells.spell.IronsBookBindingUtil.isIronsSpellBook(new ItemStack(candidate))).findFirst().orElse(null);
        if (item == null) { helper.fail("real Iron book missing"); return; }
        ItemStack book = new ItemStack(item);
        var nativeSpells = ISpellContainer.getOrCreate(book).mutableCopy();
        if (nativeSpells.getMaxSpellCount() < 1) nativeSpells.setMaxSpellCount(1);
        if (!nativeSpells.addSpellAtIndex(firstRealSpell(), 1, 0, false)) {
            helper.fail("fixture must contain a real native spell"); return;
        }
        ISpellContainer.set(book, nativeSpells.toImmutable());
        book.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Preserve this name"));
        ItemStack original = book.copy();
        var payload = com.otectus.arsnspells.spell.CrossCastingHandler.encodeArsSpell(new com.hollingsworth.arsnouveau.api.spell.Spell(
            com.hollingsworth.arsnouveau.common.spell.method.MethodSelf.INSTANCE,
            com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal.INSTANCE));
        var added = com.otectus.arsnspells.spell.IronsBookBindingUtil.appendArsSpellToBook(book, payload);
        if (!added) { helper.fail("native book binding failed"); return; }
        ItemStack bound = book.copy();
        if (com.otectus.arsnspells.spell.IronsBookBindingUtil.appendArsSpellToBook(book, payload)) {
            helper.fail("duplicate spell was added"); return;
        }
        if (!ItemStack.isSameItemSameComponents(book, bound)) { helper.fail("duplicate refusal mutated native book"); return; }
        com.otectus.arsnspells.spell.IronsBookBindingUtil.removeAllArsEntries(book);
        if (!ItemStack.isSameItemSameComponents(book, original)) {
            helper.fail("unbind changed native spell, name, capacity, or non-ANS metadata"); return;
        }
        helper.succeed();
    }

    static void assertNativeSchoolBinding(net.minecraft.gametest.framework.GameTestHelper helper) {
        var fire = com.otectus.arsnspells.compat.IronsSchoolAttributes.power("irons_spellbooks:fire");
        var resistance = com.otectus.arsnspells.compat.IronsSchoolAttributes.resistance("irons_spellbooks:fire");
        if (fire == null || resistance == null) { helper.fail("native SchoolType attribute binding missing"); return; }
        if (com.otectus.arsnspells.compat.IronsSchoolAttributes.power("audit_absent:fire") != null) {
            helper.fail("unregistered custom school aliased built-in fire"); return;
        }
        helper.succeed();
    }

}
