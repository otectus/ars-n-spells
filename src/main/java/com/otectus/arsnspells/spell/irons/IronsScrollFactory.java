package com.otectus.arsnspells.spell.irons;

import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Creates and inspects the <em>native</em> Iron's spell container on ANS-created scroll
 * carriers.
 *
 * <p><b>Why this exists.</b> Iron's treats "is a {@code Scroll} item" and "has a spell
 * container" as the same thing. {@code ISpellContainer.get(stack)} returns {@code null}
 * for a scroll with no container NBT, and Iron's inscription code dereferences that
 * result without a null check in three places — one on the client
 * ({@code InscriptionTableScreen.onInscription}) and two on the server
 * ({@code InscriptionTableMenu.clickMenuButton} and {@code doInscription}). ANS used to
 * build its carrier with a bare {@code new ItemStack(scrollItem)} plus sidecar NBT, which
 * satisfies {@code instanceof Scroll} but fails that unwritten invariant, so putting one
 * in an inscription table crashed the client and would crash a dedicated server.
 *
 * <p>The container written here is a valid, <em>empty</em>, single-slot, non-wheel
 * container — the same shape Iron's own {@code createScrollContainer} builds, minus the
 * spell. Empty is correct: the carrier's payload is an Ars spell in the ANS sidecar, which
 * has no Iron's {@code AbstractSpell} to put in a native slot (a proxy pool id is not
 * allocated until the scroll is bound onto a book). {@code getSpellAtIndex(0)} on an empty
 * container returns {@code SpellData.EMPTY} rather than null, so every Iron's dereference
 * is satisfied.
 *
 * <p><b>Iron's-isolated.</b> All Iron's API imports are confined here; callers gate on
 * {@code IronsCompat.isLoaded()} first.
 */
public final class IronsScrollFactory {

    private IronsScrollFactory() {}

    /**
     * Give {@code scroll} a valid empty native spell container.
     *
     * <p>Returns true when the stack now satisfies Iron's container invariant. Callers must
     * treat false as fatal for the carrier and discard the stack — handing back a real
     * {@code Scroll} item that fails the invariant is what caused the crash this method
     * exists to prevent.
     */
    public static boolean initializeCarrierContainer(ItemStack scroll) {
        if (scroll == null || scroll.isEmpty()) {
            return false;
        }
        if (hasNativeContainer(scroll)) {
            return true;
        }
        ISpellContainer.set(scroll, ISpellContainer.create(1, false, false));
        // Verify rather than assume: `set` goes through a codec, and a silent encode
        // failure here would recreate the exact defect we are guarding against.
        return hasNativeContainer(scroll);
    }

    /** True when {@code stack} carries a native container Iron's can read (modern or legacy). */
    public static boolean hasNativeContainer(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ISpellContainer.isSpellContainer(stack);
    }
}
