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
        if (hasReadableContainer(scroll)) {
            return true;
        }
        ISpellContainer.set(scroll, ISpellContainer.create(1, false, false));
        // Verify rather than assume: `set` goes through a codec, and a silent encode
        // failure here would recreate the exact defect we are guarding against.
        return hasReadableContainer(scroll);
    }

    /**
     * True when {@code stack} carries container NBT under a key Iron's recognises.
     *
     * <p>This is only a <em>presence</em> test — it is what Iron's own
     * {@code ISpellContainer.isSpellContainer} does, namely
     * {@code CodecHelper.hasWithLegacy(stack, "irons_spellbooks:spell_container", "ISB_Spells")},
     * a bare {@code CompoundTag.contains(key)}. Use it to answer "would Iron's take this
     * branch?", never to answer "is this carrier safe to hand out".
     */
    public static boolean hasNativeContainer(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ISpellContainer.isSpellContainer(stack);
    }

    /**
     * True when the container NBT is not merely present but actually <em>decodes</em>.
     *
     * <p>The distinction has teeth. {@code ISpellContainer.get} resolves to
     * {@code CodecHelper.get}, which ends in {@code DataResult.getOrThrow} — it throws a
     * {@code RuntimeException} on a decode failure rather than returning null. So a carrier
     * whose container key exists but does not decode throws on <em>every</em> subsequent read,
     * including the tooltip Iron's builds when a player hovers the stack. A presence check
     * would have called such a carrier valid and handed it out; this one refuses it, which is
     * what {@link #initializeCarrierContainer}'s contract has always claimed to do.
     */
    public static boolean hasReadableContainer(ItemStack stack) {
        if (!hasNativeContainer(stack)) {
            return false;
        }
        try {
            return ISpellContainer.get(stack) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * True when {@code stack}'s <em>own</em> Iron's container reports itself empty.
     *
     * <p>Audit V18's whole point: "ANS has not written here" and "this is blank" are different
     * questions, and answering the second with the first is what destroyed scrolls another mod
     * had already filled. The answer comes from Iron's own accessor,
     * {@code io.redspace.ironsspellbooks.api.spells.ISpellContainer#isEmpty()Z}, confirmed
     * against the pinned jar (Iron's 1.20.1-3.15.0, CurseForge file 7402504, sha256
     * {@code 92c046383b4960c655f840d8846732a481edcf7c5ed89028b3d7b2cc2910b224}).
     *
     * <p>A stack with no container at all holds no native spell, so it is empty. A container
     * that will not decode is <em>not</em> reported empty: the only safe reading of an
     * unreadable container is that something is in there, and refusing to inscribe over it
     * costs a player nothing.
     */
    public static boolean isNativeContainerEmpty(ItemStack stack) {
        if (!hasNativeContainer(stack)) {
            return true;
        }
        try {
            ISpellContainer container = ISpellContainer.get(stack);
            return container == null || container.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Strip {@code stack}'s native container back to the empty single-slot shape, for the
     * explicit filled-scroll conversion route. Callers pass a copy: this destroys the spell
     * that was in there, so it may only ever run on a stack the player has chosen to convert.
     */
    public static boolean clearNativeContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ISpellContainer.remove(stack);
        return initializeCarrierContainer(stack);
    }

    /**
     * True when {@code stack} is an Iron's spell book, by Iron's own published interface.
     *
     * <p>Replaces a registry-path substring test ({@code path.contains("spell_book")}), which
     * matched on naming convention rather than on what an item <em>is</em> — so any Iron's item
     * that happened to contain that fragment qualified, and a book that did not follow the
     * convention did not. {@code ISpellbook} lives in Iron's {@code api} package and is
     * identical in 3.15.0 and 3.16.2.
     */
    public static boolean isSpellBookItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && stack.getItem() instanceof io.redspace.ironsspellbooks.api.item.ISpellbook;
    }
}
