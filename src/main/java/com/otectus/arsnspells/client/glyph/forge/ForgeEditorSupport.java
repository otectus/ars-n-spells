package com.otectus.arsnspells.client.glyph.forge;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.ISpellValidator;
import com.hollingsworth.arsnouveau.api.spell.SpellValidationError;
import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.otectus.arsnspells.client.glyph.BrowserPlatform;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Shared, Forge/Ars-4.12.7-specific operations used by both native spell editors. */
public final class ForgeEditorSupport {
    private ForgeEditorSupport() {}

    /**
     * Read the capability afresh instead of reusing an editor's already projected list. Dormant
     * imported identities remain in this collection so the library can explain and archive them.
     */
    public record BookSession(InteractionHand hand, int selectedSlot, ItemStack identity) {}

    public static BookSession capture(InteractionHand hand, ItemStack originalBook) {
        Minecraft minecraft = Minecraft.getInstance();
        int selected = minecraft.player == null ? -1 : minecraft.player.getInventory().selected;
        return new BookSession(hand, selected, originalBook.copy());
    }

    public static Collection<AbstractSpellPart> knowledge(BookSession session,
                                                           boolean creativeIncludesAllNativeParts) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return List.of();
        ItemStack held = minecraft.player.getItemInHand(session.hand());
        LinkedHashSet<AbstractSpellPart> parts = new LinkedHashSet<>();
        IPlayerCap cap = CapabilityRegistry.getPlayerDataCap(minecraft.player).orElse(null);
        if (cap != null) parts.addAll(cap.getKnownGlyphs());
        parts.addAll(GlyphRegistry.getDefaultStartingSpells());
        if (ownsBook(session) && held.getItem() == ItemsRegistry.CREATIVE_SPELLBOOK.get()) {
            for (AbstractSpellPart part : GlyphRegistry.getSpellpartMap().values()) {
                if (part instanceof ImportedIronEffect || creativeIncludesAllNativeParts || part.shouldShowInSpellBook()) parts.add(part);
            }
        }
        return List.copyOf(parts);
    }

    /** Preserve each upstream editor's native candidate eligibility while imports use local projection. */
    public static boolean nativePaletteEligible(AbstractSpellPart part, boolean creative, boolean creativeIncludesAllNativeParts) {
        return part instanceof ImportedIronEffect
            || GlyphRegistry.getDefaultStartingSpells().contains(part)
            || creative && creativeIncludesAllNativeParts
            || part.shouldShowInSpellBook();
    }

    public static int bookTier(ItemStack stack) {
        return stack.getItem() instanceof SpellBook book ? book.getTier().value : 1;
    }

    public static boolean creative(ItemStack stack) {
        return stack.getItem() == ItemsRegistry.CREATIVE_SPELLBOOK.get();
    }

    public static boolean ownsBook(BookSession session) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        if (session.hand() == InteractionHand.MAIN_HAND && minecraft.player.getInventory().selected != session.selectedSlot()) return false;
        ItemStack held = minecraft.player.getItemInHand(session.hand());
        // Accept an inventory packet replacing the object only when the complete item+NBT identity
        // is unchanged. A successful Save changes NBT and deliberately requires reopening/rebinding.
        return ItemStack.isSameItemSameTags(held, session.identity());
    }

    /** Validate a copied draft at the exact cell the native editor will fill. */
    @Nullable
    public static Component insertionError(Collection<AbstractSpellPart> knowledge, boolean ownsBook,
                                           ISpellValidator validator, List<AbstractSpellPart> draft,
                                           int insertion, AbstractSpellPart requested) {
        if (!ownsBook) return Component.translatable("ars_n_spells.browser.book_changed");
        if (insertion < 0) return Component.translatable("ars_n_spells.browser.no_visible_slot");
        if (requested == null || requested.getRegistryName() == null
                || GlyphRegistry.getSpellpartMap().get(requested.getRegistryName()) != requested)
            return Component.translatable("ars_n_spells.browser.missing_part");
        if (!knowledge.contains(requested))
            return Component.translatable("ars_n_spells.browser.unlearned");
        if (requested instanceof ImportedIronEffect imported && !BrowserPlatform.available(imported))
            return Component.translatable("ars_n_spells.browser.unavailable_now");

        ArrayList<AbstractSpellPart> candidate = new ArrayList<>(draft);
        while (candidate.size() <= insertion) candidate.add(null);
        candidate.set(insertion, requested);
        for (SpellValidationError error : validator.validate(candidate)) {
            if (error.getPosition() >= insertion) return error.makeTextComponentAdding();
        }
        return null;
    }

    /** Equality snapshot used by the controller's bounded draft-change polling. */
    public static List<?> draftSnapshot(int selectedSlot, String name, int position,
                                        List<AbstractSpellPart> recipe) {
        ArrayList<Object> snapshot = new ArrayList<>(recipe.size() + 3);
        snapshot.add(selectedSlot);
        snapshot.add(name == null ? "" : name);
        snapshot.add(position);
        for (AbstractSpellPart part : recipe)
            snapshot.add(part == null || part.getRegistryName() == null ? null : part.getRegistryName());
        return java.util.Collections.unmodifiableList(snapshot);
    }
}
