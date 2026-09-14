package com.otectus.arsnspells.inscription;

import com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.InscriptionSourceKind;
import com.otectus.arsnspells.rituals.InscriptionInputs;
import com.otectus.arsnspells.spell.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Neo native-component adapter for the shared inscription policy. All reads use copies. */
public final class InscriptionClassifier {
    private InscriptionClassifier() {}
    public static InscriptionSourceKind classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return InscriptionSourceKind.BLANK_PARCHMENT;
        if (IronsBookBindingUtil.isIronsSpellBook(stack)) return InscriptionSourceKind.REUSABLE_BOOK;
        if (IronsBookBindingUtil.isIronsScroll(stack)) return isNativelyEmpty(stack)
            ? InscriptionSourceKind.BLANK_PARCHMENT : InscriptionSourceKind.FILLED_SCROLL;
        boolean parchment = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("ars_nouveau:spell_parchment");
        if (SpellCasterRegistry.hasCaster(stack) && !parchment) return InscriptionSourceKind.REUSABLE_FOCUS;
        if (CrossModSpellComponents.has(stack) || InscriptionInputs.readSource(stack) != null)
            return parchment ? InscriptionSourceKind.CONSUMABLE_SCROLL : InscriptionSourceKind.FILLED_SCROLL;
        return InscriptionSourceKind.BLANK_PARCHMENT;
    }
    public static boolean isNativelyEmpty(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (CrossModSpellComponents.has(stack)) return false;
        if (IronsCompat.isLoaded() && IronsBookBindingUtil.isIronsScroll(stack))
            return com.otectus.arsnspells.spell.irons.IronsScrollFactory.isNativeContainerEmpty(stack);
        return InscriptionInputs.readSource(stack) == null;
    }
    public static ItemStack blankedSingleCopy(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copyWithCount(1);
        CrossModSpellComponents.clearPayloadOnly(copy);
        copy.remove(ModDataComponents.EXPORT_MODE.get());
        copy.remove(ModDataComponents.SCHEMA_VERSION.get());
        if (IronsCompat.isLoaded() && IronsBookBindingUtil.isIronsScroll(copy))
            com.otectus.arsnspells.spell.irons.IronsScrollFactory.clearNativeContainer(copy);
        return copy;
    }
}
