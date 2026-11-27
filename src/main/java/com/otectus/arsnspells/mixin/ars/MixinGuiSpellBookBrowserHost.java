package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.ISpellValidator;
import com.hollingsworth.arsnouveau.client.gui.book.BaseBook;
import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.hollingsworth.arsnouveau.client.gui.buttons.CraftingButton;
import com.hollingsworth.arsnouveau.client.gui.buttons.GlyphButton;
import com.hollingsworth.arsnouveau.client.gui.buttons.GuiSpellSlot;
import com.otectus.arsnspells.client.glyph.ArsGlyphEditorHost;
import com.otectus.arsnspells.client.glyph.BrowserController;
import com.otectus.arsnspells.client.glyph.forge.ForgeEditorSupport;
import com.otectus.arsnspells.client.glyph.forge.ForgeGlyphBookmarkButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Forge Ars 4.12.7 standard spell editor bridge. */
@Mixin(value = GuiSpellBook.class, remap = false)
public abstract class MixinGuiSpellBookBrowserHost extends BaseBook implements ArsGlyphEditorHost {
    @Shadow public int selectedSpellSlot;
    @Shadow public EditBox spell_name;
    @Shadow public com.hollingsworth.arsnouveau.client.gui.NoShadowTextField searchBar;
    @Shadow public GuiSpellSlot selected_slot;
    @Shadow public List<CraftingButton> craftingCells;
    @Shadow public List<AbstractSpellPart> unlockedSpells;
    @Shadow public List<AbstractSpellPart> displayedGlyphs;
    @Shadow public List<GlyphButton> glyphButtons;
    @Shadow public int page;
    @Shadow public net.minecraft.client.gui.screens.inventory.PageButton nextButton;
    @Shadow public net.minecraft.client.gui.screens.inventory.PageButton previousButton;
    @Shadow public ISpellValidator spellValidator;
    @Shadow public String previousString;
    @Shadow public ItemStack bookStack;
    @Shadow public InteractionHand hand;

    @Unique private BrowserController ans$browserController;
    @Unique private Button ans$browserBookmark;
    @Unique private List<AbstractSpellPart> ans$nativePalette = List.of();
    @Unique private StandardDraft ans$resizeDraft;
    @Unique private ForgeEditorSupport.BookSession ans$bookSession;
    @Unique private boolean ans$supportedEditor;
    @Unique private static final AtomicBoolean ans$warnedReplacement = new AtomicBoolean();

    @Invoker("layoutAllGlyphs") protected abstract void ans$layoutAllGlyphs(int page);
    @Invoker("validate") protected abstract void ans$validate();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ans$createBrowser(InteractionHand ignored, CallbackInfo ci) {
        ans$bookSession = ForgeEditorSupport.capture(hand, bookStack);
        ans$nativePalette = unlockedSpells.stream()
            .filter(part -> !(part instanceof com.otectus.arsnspells.glyph.ImportedIronEffect)).toList();
        ans$supportedEditor = getClass().getName().equals(GuiSpellBook.class.getName());
        if (!ans$supportedEditor && ans$warnedReplacement.compareAndSet(false, true))
            org.slf4j.LoggerFactory.getLogger(MixinGuiSpellBookBrowserHost.class).warn(
                "Unsupported GuiSpellBook replacement subclass {}; preserving its native palette and disabling the ANS library host",
                getClass().getName());
        ans$browserController = new BrowserController(this);
        ans$browserController.tick();
    }

    @Inject(method = {"init()V", "m_7856_()V"}, at = @At("HEAD"))
    private void ans$captureResizeDraft(CallbackInfo ci) {
        if (craftingCells.isEmpty()) return;
        ArrayList<AbstractSpellPart> recipe = new ArrayList<>();
        for (int i = 0; i < Math.min(10, craftingCells.size()); i++) recipe.add(craftingCells.get(i).getAbstractSpellPart());
        ans$resizeDraft = new StandardDraft(selectedSpellSlot, spell_name == null ? "" : spell_name.getValue(),
            searchBar == null ? previousString : searchBar.getValue(), page,
            Collections.unmodifiableList(new ArrayList<>(recipe)));
        // Screen.resize clears the widget lists but Ars leaves these backing lists populated.
        craftingCells.clear();
        glyphButtons.clear();
    }

    @Inject(method = {"init()V", "m_7856_()V"}, at = @At("TAIL"))
    private void ans$restoreResizeDraftAndBookmark(CallbackInfo ci) {
        StandardDraft draft = ans$resizeDraft;
        ans$resizeDraft = null;
        if (draft != null) {
            ans$selectSlot(draft.slot());
            spell_name.setValue(draft.name());
            searchBar.setValue(draft.query());
            for (int i = 0; i < craftingCells.size() && i < draft.recipe().size(); i++) {
                craftingCells.get(i).clear();
                AbstractSpellPart part = draft.recipe().get(i);
                if (part != null) craftingCells.get(i).setAbstractSpellPart(part);
            }
            page = draft.page();
        }
        browserRefreshPalette();
        ans$browserBookmark = addRenderableWidget(new ForgeGlyphBookmarkButton(
            bookLeft - 15, bookTop + 166, ignored -> ans$browserController.open()));
        ans$updateBookmark();
    }

    /** Adds the editor-owned tick override; Ars's BaseBook inherits Screen's no-op implementation. */
    @Override
    public void tick() {
        super.tick();
        if (ans$browserController != null) ans$browserController.tick();
        ans$updateBookmark();
    }

    @Unique
    private void ans$selectSlot(int slot) {
        selectedSpellSlot = slot;
        for (var child : children()) {
            if (child instanceof GuiSpellSlot candidate) {
                candidate.isSelected = candidate.slotNum == slot;
                if (candidate.isSelected) selected_slot = candidate;
            }
        }
    }

    @Unique
    private void ans$updateBookmark() {
        if (ans$browserBookmark == null || ans$browserController == null) return;
        if (!ans$supportedEditor) {
            ans$browserBookmark.visible = ans$browserBookmark.active = false;
            return;
        }
        Component text = ans$browserController.bookmark();
        ans$browserBookmark.visible = text != null;
        ans$browserBookmark.active = text != null;
        if (text != null) ans$browserBookmark.setMessage(text);
        ans$browserBookmark.setTooltip(text == null ? null : Tooltip.create(text));
    }

    @Override public BrowserController browserController() { return ans$browserController; }

    @Override public Collection<AbstractSpellPart> browserKnowledge() {
        return ans$supportedEditor ? ForgeEditorSupport.knowledge(ans$bookSession, false) : List.of();
    }

    @Override public int browserBookTier() { return ForgeEditorSupport.bookTier(bookStack); }
    @Override public boolean browserCreative() { return ForgeEditorSupport.creative(bookStack); }
    @Override public boolean browserOwnsBook() { return ans$supportedEditor && ForgeEditorSupport.ownsBook(ans$bookSession); }

    @Nullable
    @Override
    public Component browserError(AbstractSpellPart part) {
        ArrayList<AbstractSpellPart> draft = new ArrayList<>(craftingCells.size());
        int insertion = -1;
        for (int i = 0; i < craftingCells.size(); i++) {
            AbstractSpellPart current = craftingCells.get(i).getAbstractSpellPart();
            draft.add(current);
            if (insertion < 0 && current == null) insertion = i;
        }
        return ForgeEditorSupport.insertionError(browserKnowledge(), browserOwnsBook(), spellValidator, draft, insertion, part);
    }

    @Override
    public void browserInsert(AbstractSpellPart part) {
        if (browserError(part) != null) return;
        GlyphButton synthetic = new GlyphButton(0, 0, part, ignored -> {});
        ((GuiSpellBook) (Object) this).onGlyphClick(synthetic);
    }

    @Override
    public void browserRefreshPalette() {
        if (!ans$supportedEditor) return;
        int requestedPage = page;
        String query = browserQuery();
        // Keep Ars's constructor-built non-imported projection byte-for-byte ordered: standard
        // creative books deliberately replace capability knowledge with registry-order showable parts.
        ArrayList<AbstractSpellPart> eligible = new ArrayList<>(ans$nativePalette);
        for (AbstractSpellPart part : browserKnowledge())
            if (part instanceof com.otectus.arsnspells.glyph.ImportedIronEffect
                    && ForgeEditorSupport.nativePaletteEligible(part, browserCreative(), false)
                    && ans$browserController.inPalette(part)) eligible.add(part);
        unlockedSpells = eligible;
        String folded = query.toLowerCase(Locale.ROOT);
        displayedGlyphs = folded.isEmpty() ? new ArrayList<>(eligible) : new ArrayList<>(eligible.stream()
            .filter(part -> part.getLocaleName().toLowerCase(Locale.ROOT).contains(folded)).toList());
        previousString = query;
        page = Math.max(0, Math.min(requestedPage, Math.max(0, displayedGlyphs.size() - 1) / 58));
        if (nextButton == null || previousButton == null) return;
        previousButton.visible = previousButton.active = page > 0;
        nextButton.visible = nextButton.active = (page + 1) * 58 < displayedGlyphs.size();
        ans$layoutAllGlyphs(page);
        ans$validate();
    }

    @Override
    public List<?> browserDraft() {
        ArrayList<AbstractSpellPart> recipe = new ArrayList<>(craftingCells.size());
        for (CraftingButton cell : craftingCells) recipe.add(cell.getAbstractSpellPart());
        return ForgeEditorSupport.draftSnapshot(selectedSpellSlot, spell_name == null ? "" : spell_name.getValue(), page, recipe);
    }

    @Override public String browserQuery() { return searchBar == null ? previousString : searchBar.getValue(); }

    @Unique
    private record StandardDraft(int slot, String name, String query, int page, List<AbstractSpellPart> recipe) {}
}
