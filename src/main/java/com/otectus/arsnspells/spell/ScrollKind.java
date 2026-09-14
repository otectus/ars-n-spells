package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * What a scroll-shaped stack actually is, from the point of view of everything that has to
 * decide whether Iron's Inscription Table, the Spell Loom, or the binder may act on it.
 *
 * <p>One classifier rather than four: the table mixin, the binder, the policy and the
 * tooltip all used to re-derive "is this an ANS carrier" from slightly different component
 * tests, and they disagreed at the edges — most visibly on a carrier whose payload no longer
 * deserializes, which one path called valid and another called broken.
 *
 * <p><b>No Iron's imports.</b> Iron's scrolls are recognized by registry id through
 * {@link IronsBookBindingUtil#isIronsScroll(ItemStack)}, and the one test that genuinely
 * needs Iron's API ({@code hasReadableContainer}) is reached behind an
 * {@link IronsCompat#isLoaded()} gate and a fully-qualified call, exactly as
 * {@code inscription/InscriptionClassifier} does. That keeps this class loadable — and
 * unit-testable — with Iron's absent.
 */
public enum ScrollKind {
    /**
     * A real Iron's scroll holding a real Iron's spell in its own container, with no ANS
     * sidecar. Iron's owns it end to end; ANS must not interfere.
     */
    NATIVE,

    /**
     * A real Iron's scroll carrying exactly one castable Ars Nouveau spell in the ANS
     * cross-spell data component. Iron's cannot read the payload, so any native inscription
     * of it has to be rerouted through {@link IronsSpellbookBinder}.
     */
    ANS_CARRIER,

    /**
     * ANS's own blank scroll item. Carries nothing; it is the Spell Loom's input, not
     * something that can be inscribed or bound.
     */
    ANS_BLANK,

    /**
     * A scroll ANS refuses to act on: a sidecar written by a newer schema, a sidecar that
     * does not hold exactly one Ars entry, a payload that no longer deserializes, or an
     * Iron's scroll whose native container is missing or undecodable. Never mutated and
     * never deleted — reported, so the player can tell a broken item from a rejected one.
     */
    INVALID,

    /** Anything else, including an empty stack. Not a scroll ANS has an opinion about. */
    OTHER;

    /** Classify {@code stack}. Cheap and side-effect free; safe to call every frame. */
    public static ScrollKind classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return OTHER;
        }
        if (isAnsBlankScroll(stack)) {
            return ANS_BLANK;
        }
        if (!IronsBookBindingUtil.isIronsScroll(stack)) {
            return OTHER;
        }
        if (CrossModSpellComponents.has(stack)) {
            if (CrossModSpellComponents.schemaVersion(stack) > CrossModSpellComponents.SCHEMA_VERSION) {
                return INVALID;
            }
            return IronsBookBindingUtil.extractSingleArsEntry(stack)
                .filter(IronsBookBindingUtil::isCastableArsPayload)
                .map(arsTag -> ANS_CARRIER)
                .orElse(INVALID);
        }
        // No sidecar: Iron's own scroll. "Readable", not merely present -- a container
        // component that fails to decode throws out of every later read just as surely as a
        // missing one.
        if (IronsCompat.isLoaded()
            && com.otectus.arsnspells.spell.irons.IronsScrollFactory.hasReadableContainer(stack)) {
            return NATIVE;
        }
        return INVALID;
    }

    /**
     * True for ANS's blank scroll. Written against {@link DeferredHolder#isBound()} so a
     * classify call made before registration (or in a unit test with no registries) answers
     * "not the blank scroll" instead of throwing.
     */
    private static boolean isAnsBlankScroll(ItemStack stack) {
        DeferredHolder<Item, Item> blank = ModItemsRegistry.blankScroll();
        return blank != null && blank.isBound() && stack.is(blank.get());
    }
}
