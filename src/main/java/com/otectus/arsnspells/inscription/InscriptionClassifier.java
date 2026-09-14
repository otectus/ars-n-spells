package com.otectus.arsnspells.inscription;

import com.hollingsworth.arsnouveau.api.item.ICasterTool;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellCaster;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.InscriptionSourceKind;
import com.otectus.arsnspells.spell.ArsSpellExportUtil;
import com.otectus.arsnspells.spell.CrossCastNbt;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * The Forge 1.20.1 loader adapter for {@link InscriptionSourceKind}: turns a real
 * {@link ItemStack} into the kind the planner reasons about (audit V18).
 *
 * <p>Every "is this blank?" decision in the loom, the menu and the transcription ritual now
 * ends here, and the two answers this class gives are deliberately separate:
 *
 * <ul>
 *   <li>{@link #classify(ItemStack)} says <em>what the item is</em> -- a reusable book, a
 *       reusable focus, a consumable scroll, a blank, or a scroll that already holds a spell.
 *       A filled scroll is its own kind, so no caller can reach a "blank" branch with one.</li>
 *   <li>{@link #isNativelyEmpty(ItemStack)} says whether the item's <em>own</em> spell
 *       container is empty, asked of whichever mod owns that container. This is never the
 *       "does it carry ANS data" question that shipped before, which was true for a scroll
 *       Iron's had filled and cost the player the spell in it.</li>
 * </ul>
 *
 * <p><b>Optional-mod loading.</b> Ars Nouveau is a hard dependency, so its types are named
 * directly. Every Iron's-typed read goes through {@code spell.irons.IronsScrollFactory} behind
 * an {@link IronsCompat#isLoaded()} gate at the call site and an FQN reference, so no method
 * here names an Iron's type and the verifier never resolves one on an Iron's-less install.
 * {@code IronsBookBindingUtil} recognises the scroll by registry id and carries the same gate
 * internally, so it is safe to call unconditionally.
 */
public final class InscriptionClassifier {

    private InscriptionClassifier() {
    }

    /**
     * What {@code stack} is, for inscription purposes. Never returns null; an empty stack is
     * reported as {@link InscriptionSourceKind#BLANK_PARCHMENT} because there is nothing there
     * to protect, and every caller rejects an empty slot on its own terms first.
     */
    public static InscriptionSourceKind classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return InscriptionSourceKind.BLANK_PARCHMENT;
        }

        // Reusable first: whatever else is true of a book, it is never eaten.
        if (stack.getItem() instanceof SpellBook || IronsBookBindingUtil.isIronsSpellBook(stack)) {
            return InscriptionSourceKind.REUSABLE_BOOK;
        }

        boolean ironsScroll = IronsBookBindingUtil.isIronsScroll(stack);
        boolean carriesAns = CrossCastNbt.hasCrossModSpells(stack.getTag());

        if (ironsScroll) {
            // The carrier the loom itself produces is a scroll with an ANS payload and an
            // intentionally empty native container -- filled, and not to be overwritten.
            if (carriesAns || !isNativelyEmpty(stack)) {
                return InscriptionSourceKind.FILLED_SCROLL;
            }
            return InscriptionSourceKind.BLANK_PARCHMENT;
        }

        // An Ars caster tool that is not a spellbook: a tome, focus, or an addon's caster item.
        // Reusable by construction -- Ars never consumes one to read its spell either.
        if (stack.getItem() instanceof ICasterTool && !isArsSpellParchment(stack)) {
            return InscriptionSourceKind.REUSABLE_FOCUS;
        }

        if (carriesAns || arsSpellPresent(stack)) {
            // A written parchment, or any item already carrying a cross-cast payload. Single-use
            // as a source; never a legal target.
            return isArsSpellParchment(stack)
                ? InscriptionSourceKind.CONSUMABLE_SCROLL
                : InscriptionSourceKind.FILLED_SCROLL;
        }

        return InscriptionSourceKind.BLANK_PARCHMENT;
    }

    /**
     * Whether {@code stack}'s own native spell container is empty, as the mod that owns it
     * reports. Asks Ars through {@link SpellCaster} and Iron's through {@code ISpellContainer};
     * an item with neither container has nothing native in it and is empty.
     */
    public static boolean isNativelyEmpty(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        if (arsSpellPresent(stack)) {
            return false;
        }
        // Gated + FQN so the Iron's-importing helper only classloads with Iron's present.
        if (IronsCompat.isLoaded()
            && !com.otectus.arsnspells.spell.irons.IronsScrollFactory.isNativeContainerEmpty(stack)) {
            return false;
        }
        return true;
    }

    /**
     * A single-unit copy of {@code stack} with every spell container on it emptied -- both the
     * ANS cross-cast sidecar and the native Iron's one.
     *
     * <p>This is the intentional filled-scroll conversion route from audit V18, and it exists
     * so that route never has to bypass the planner: the caller plans against this copy, the
     * planner sees an honest {@link InscriptionSourceKind#BLANK_PARCHMENT}, and only a
     * permitted plan is committed. The original stack is untouched, so a preview of a
     * conversion still mutates nothing.
     */
    public static ItemStack blankedSingleCopy(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copyWithCount(1);
        CrossCastNbt.clearCrossModSpells(copy);
        if (copy.hasTag()) {
            copy.getOrCreateTag().remove(ArsSpellExportUtil.TAG_EXPORT_MODE);
        }
        if (IronsCompat.isLoaded() && IronsBookBindingUtil.isIronsScroll(copy)) {
            com.otectus.arsnspells.spell.irons.IronsScrollFactory.clearNativeContainer(copy);
        }
        return copy;
    }

    /**
     * True when Ars's own caster reports a non-empty recipe on the stack.
     *
     * <p><b>Reads the existing tag only.</b> {@code new SpellCaster(ItemStack)} resolves to
     * {@code ItemStack.getOrCreateTag()}, which stamps an empty compound onto a tagless stack --
     * so classifying a fresh blank scroll through it would leave a mark, and a preview that is
     * meant to move nothing would already have changed the item. The {@code CompoundTag}
     * constructor parses exactly the same data without touching the stack, and a stack with no
     * tag at all cannot be carrying a spell anyway.
     */
    private static boolean arsSpellPresent(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return false;
        }
        try {
            Spell spell = new SpellCaster(tag).getSpell();
            if (spell != null && spell.recipe != null && !spell.recipe.isEmpty()) {
                return true;
            }
        } catch (Exception ignored) {
            // Narrow to Exception so LinkageError still propagates, as elsewhere in this repo.
        }
        try {
            Spell rooted = Spell.fromTag(tag);
            return rooted != null && rooted.recipe != null && !rooted.recipe.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Ars spell parchment, the one Ars caster tool that is genuinely single-use. */
    private static boolean isArsSpellParchment(ItemStack stack) {
        return stack.getItem() instanceof com.hollingsworth.arsnouveau.common.items.SpellParchment;
    }
}
