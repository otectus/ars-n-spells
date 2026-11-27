package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.ISpellValidator;
import com.hollingsworth.arsnouveau.api.spell.SpellValidationError;
import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.client.gui.SearchBar;
import com.hollingsworth.arsnouveau.client.gui.buttons.CraftingButton;
import com.hollingsworth.arsnouveau.client.gui.buttons.GlyphButton;
import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.hollingsworth.arsnouveau.client.gui.book.EnterTextField;
import com.hollingsworth.arsnouveau.client.gui.book.GlyphFormatter;
import net.minecraft.client.gui.screens.inventory.PageButton;
import org.spongepowered.asm.mixin.gen.Invoker;
import com.hollingsworth.arsnouveau.client.gui.book.SpellSlottedScreen;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.otectus.arsnspells.client.glyph.ArsGlyphEditorHost;
import com.otectus.arsnspells.client.glyph.BrowserController;
import com.otectus.arsnspells.client.glyph.GlyphBookmarkButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Exact Ars 5.13.1 editor host. The library changes the cached palette, never player knowledge. */
@Mixin(GuiSpellBook.class)
public abstract class MixinGuiSpellBookGlyphBrowser extends SpellSlottedScreen implements ArsGlyphEditorHost {
    @Shadow public List<AbstractSpellPart> unlockedSpells;
    @Shadow public List<AbstractSpellPart> displayedGlyphs;
    @Shadow public List<AbstractSpellPart> spell;
    @Shadow public List<CraftingButton> craftingCells;
    @Shadow public int page;
    @Shadow public int spellWindowOffset;
    @Shadow public SearchBar searchBar;
    @Shadow public ISpellValidator spellValidator;
    @Shadow public EnterTextField spellNameBox;
    @Shadow public PageButton previousButton;
    @Shadow private GlyphFormatter glyphFormatter;
    @Shadow public abstract int getNumPages();
    @Shadow public abstract void updateNextPageButtons();
    @Shadow public abstract void updateWindowOffset(int offset);
    @Invoker(value = "validate", remap = false) protected abstract void arsnspells$validateDraft();
    @Shadow public abstract void onGlyphClick(Button button);

    @Unique private BrowserController arsnspells$browser;
    @Unique private Button arsnspells$bookmark;
    @Unique private int arsnspells$hotbarSlot;
    @Unique private String arsnspells$draftName;
    @Unique private int arsnspells$draftOffset;
    @Unique private Component arsnspells$bookmarkLabel;

    protected MixinGuiSpellBookGlyphBrowser(InteractionHand hand) {
        super(hand);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void arsnspells$constructBrowser(InteractionHand hand, CallbackInfo ci) {
        // A subclass may replace init, the palette, or insertion independently.
        if (!getClass().equals(GuiSpellBook.class)) return;
        arsnspells$hotbarSlot = player.getInventory().selected;
        arsnspells$browser = new BrowserController(this);
        // Apply the local default before the first layout. Controller refreshes rebuild from live knowledge.
        unlockedSpells = arsnspells$paletteCandidates();
        displayedGlyphs = new ArrayList<>(unlockedSpells);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void arsnspells$captureLayout(CallbackInfo ci) {
        if (arsnspells$browser == null) return;
        arsnspells$draftName = spellNameBox == null ? null : spellNameBox.getValue();
        arsnspells$draftOffset = spellWindowOffset;
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void arsnspells$finishLayout(CallbackInfo ci) {
        if (arsnspells$browser == null) return;
        // Ars recreates the text box from the saved caster and resets bonus windows during resize.
        if (arsnspells$draftName != null) spellNameBox.setValue(arsnspells$draftName);
        if (arsnspells$draftOffset != spellWindowOffset) updateWindowOffset(arsnspells$draftOffset);
        arsnspells$bookmark = addRenderableWidget(new GlyphBookmarkButton(Math.max(0, bookLeft - 15), bookTop + 1,
            button -> arsnspells$browser.open()));
        arsnspells$bookmarkLabel = null;
        arsnspells$updateBookmark();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void arsnspells$refreshBookmark(GuiGraphics graphics, int mouseX, int mouseY, float frame, CallbackInfo ci) {
        arsnspells$updateBookmark();
    }

    @Unique private void arsnspells$updateBookmark() {
        if (arsnspells$bookmark == null || arsnspells$browser == null) return;
        Component label = arsnspells$browser.bookmark();
        arsnspells$bookmark.visible = label != null && browserOwnsBook();
        if (!java.util.Objects.equals(label, arsnspells$bookmarkLabel)) {
            arsnspells$bookmarkLabel = label;
            arsnspells$bookmark.setMessage(label == null ? Component.empty() : label);
            arsnspells$bookmark.setTooltip(label == null ? null : Tooltip.create(label));
        }
    }

    @Override public BrowserController browserController() {
        return arsnspells$browser;
    }

    @Override public Collection<AbstractSpellPart> browserKnowledge() {
        List<AbstractSpellPart> parts = new ArrayList<>();
        if (arsnspells$isCreativeBook()) {
            parts.addAll(GlyphRegistry.getSpellpartMap().values());
        } else {
            var cap = CapabilityRegistry.getPlayerDataCap(player);
            if (cap != null) parts.addAll(cap.getKnownGlyphs());
            parts.addAll(GlyphRegistry.getDefaultStartingSpells());
        }
        return List.copyOf(parts);
    }

    @Override public int browserBookTier() {
        if (bookStack.getItem() instanceof SpellBook book) return Math.max(1, Math.min(3, book.getTier().value));
        return 1;
    }

    @Override public boolean browserCreative() {
        return arsnspells$isCreativeBook() || player != null && player.isCreative();
    }

    @Override public boolean browserOwnsBook() {
        if (player == null || Minecraft.getInstance().player != player || !player.isAlive()) return false;
        if (hand == InteractionHand.MAIN_HAND && player.getInventory().selected != arsnspells$hotbarSlot) return false;
        ItemStack held = player.getItemInHand(hand);
        return !held.isEmpty() && ItemStack.matches(held, bookStack);
    }

    @Override @Nullable public Component browserError(AbstractSpellPart part) {
        List<SpellValidationError> errors = arsnspells$insertionErrors(part);
        if (errors == null) return Component.translatable("ars_n_spells.browser.no_visible_slot");
        return errors.stream().findFirst().map(error -> (Component) error.makeTextComponentAdding()).orElse(null);
    }

    @Override public void browserInsert(AbstractSpellPart part) {
        List<SpellValidationError> errors = arsnspells$insertionErrors(part);
        if (errors == null || !errors.isEmpty()) return;
        GlyphButton exact = new GlyphButton(0, 0, part, ignored -> {});
        exact.validationErrors.addAll(errors);
        // Use Ars's real mutation path: it updates the concrete CraftingButton, backing spell,
        // bonus-window arrows and all validation/cost state together.
        onGlyphClick(exact);
    }

    @Override public void browserRefreshPalette() {
        int oldPage = page;
        String query = browserQuery();
        unlockedSpells = arsnspells$paletteCandidates();
        if (query.isBlank()) displayedGlyphs = new ArrayList<>(unlockedSpells);
        else {
            String folded = query.toLowerCase(Locale.ROOT);
            displayedGlyphs = unlockedSpells.stream()
                .filter(part -> part.getLocaleName().toLowerCase(Locale.ROOT).contains(folded))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        if (glyphFormatter == null) return;
        glyphFormatter.buildPages(displayedGlyphs);
        page = Math.max(0, Math.min(oldPage, getNumPages() - 1));
        glyphFormatter.layoutAllGlyphs(page);
        previousButton.active = previousButton.visible = page > 0;
        updateNextPageButtons();
        arsnspells$validateDraft();
        arsnspells$updateBookmark();
    }

    @Override public List<?> browserDraft() {
        return List.of(new ArrayList<>(spell), spellWindowOffset, selectedSpellSlot, arsnspells$insertionPosition());
    }

    @Override public String browserQuery() {
        return searchBar == null ? "" : searchBar.getValue();
    }

    @Unique private List<AbstractSpellPart> arsnspells$paletteCandidates() {
        // Upstream appends starting glyphs after explicitly known glyphs; a creative book starts in registry order.
        List<AbstractSpellPart> candidates = new ArrayList<>();
        if (arsnspells$isCreativeBook()) {
            for (var part : GlyphRegistry.getSpellpartMap().values()) {
                if (part.shouldShowInSpellBook() && arsnspells$browser.inPalette(part)) candidates.add(part);
            }
        } else {
            var cap = CapabilityRegistry.getPlayerDataCap(player);
            if (cap != null) for (var part : cap.getKnownGlyphs()) {
                if (part.shouldShowInSpellBook() && arsnspells$browser.inPalette(part)) candidates.add(part);
            }
            for (var part : GlyphRegistry.getDefaultStartingSpells()) {
                if (arsnspells$browser.inPalette(part)) candidates.add(part);
            }
        }
        return candidates;
    }

    @Unique private boolean arsnspells$isCreativeBook() {
        return bookStack.is(ItemsRegistry.CREATIVE_SPELLBOOK.asItem())
            || bookStack.getItem() instanceof SpellBook book && book.getTier() == SpellTier.CREATIVE;
    }

    /** Ars inserts into the first empty cell of the visible ten-slot window. */
    @Unique private int arsnspells$insertionPosition() {
        int end = Math.min(spellWindowOffset + 10, craftingCells.size());
        for (CraftingButton cell : craftingCells.subList(Math.min(spellWindowOffset, end), end)) {
            if (cell.getAbstractSpellPart() == null) return cell.slotNum;
        }
        return -1;
    }

    /** Null means there is no visible insertion cell; otherwise errors are for this exact copied position. */
    @Unique private List<SpellValidationError> arsnspells$insertionErrors(AbstractSpellPart part) {
        int position = arsnspells$insertionPosition();
        if (position < 0) return null;
        List<AbstractSpellPart> candidate = new ArrayList<>(spell);
        while (candidate.size() <= position) candidate.add(null);
        candidate.set(position, part);
        return spellValidator.validate(candidate).stream()
            .filter(error -> error.getPosition() >= position)
            .toList();
    }
}
