package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.ISpellValidator;
import com.hollingsworth.arsnouveau.client.gui.book.BaseBook;
import com.hollingsworth.arsnouveau.client.gui.book.InfinityGuiSpellBook;
import com.hollingsworth.arsnouveau.client.gui.buttons.GlyphButton;
import com.hollingsworth.arsnouveau.client.gui.buttons.GuiSpellSlot;
import com.hollingsworth.arsnouveau.client.gui.buttons.InfinityCraftingButton;
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

/** Forge Ars 4.12.7 Infinity editor bridge, including its full backing recipe/window state. */
@Mixin(value = InfinityGuiSpellBook.class, remap = false)
public abstract class MixinInfinityGuiSpellBookBrowserHost extends BaseBook implements ArsGlyphEditorHost {
    @Shadow public int selectedSpellSlot;
    @Shadow public EditBox spell_name;
    @Shadow public com.hollingsworth.arsnouveau.client.gui.NoShadowTextField searchBar;
    @Shadow public GuiSpellSlot selected_slot;
    @Shadow public List<InfinityCraftingButton> craftingCells;
    @Shadow public List<AbstractSpellPart> unlockedSpells;
    @Shadow public List<AbstractSpellPart> displayedGlyphs;
    @Shadow public List<AbstractSpellPart> spell;
    @Shadow public List<GlyphButton> glyphButtons;
    @Shadow public int page;
    @Shadow public net.minecraft.client.gui.screens.inventory.PageButton nextButton;
    @Shadow public net.minecraft.client.gui.screens.inventory.PageButton previousButton;
    @Shadow public ISpellValidator spellValidator;
    @Shadow public String previousString;
    @Shadow public ItemStack bookStack;
    @Shadow public InteractionHand hand;
    @Shadow public int spellWindowOffset;

    @Unique private BrowserController ans$browserController;
    @Unique private Button ans$browserBookmark;
    @Unique private List<AbstractSpellPart> ans$nativePalette = List.of();
    @Unique private InfinityDraft ans$resizingDraft;
    @Unique private ForgeEditorSupport.BookSession ans$bookSession;
    @Unique private boolean ans$supportedEditor;
    @Unique private static final AtomicBoolean ans$warnedReplacement = new AtomicBoolean();

    @Invoker("layoutAllGlyphs") protected abstract void ans$layoutAllGlyphs(int page);
    @Invoker("validate") protected abstract void ans$validateEditor();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ans$install(InteractionHand ignored, CallbackInfo ci) {
        ans$bookSession = ForgeEditorSupport.capture(hand, bookStack);
        ans$nativePalette = unlockedSpells.stream()
            .filter(part -> !(part instanceof com.otectus.arsnspells.glyph.ImportedIronEffect)).toList();
        ans$supportedEditor = getClass().getName().equals(InfinityGuiSpellBook.class.getName());
        if (!ans$supportedEditor && ans$warnedReplacement.compareAndSet(false, true))
            org.slf4j.LoggerFactory.getLogger(MixinInfinityGuiSpellBookBrowserHost.class).warn(
                "Unsupported InfinityGuiSpellBook replacement subclass {}; preserving its native palette and disabling the ANS library host",
                getClass().getName());
        ans$browserController = new BrowserController(this);
        ans$browserController.tick();
    }

    @Inject(method = {"init()V", "m_7856_()V"}, at = @At("HEAD"))
    private void ans$captureResizeDraft(CallbackInfo ci) {
        if (craftingCells.isEmpty()) return;
        ans$resizingDraft = new InfinityDraft(selectedSpellSlot, spell_name == null ? "" : spell_name.getValue(),
            searchBar == null ? previousString : searchBar.getValue(), page, spellWindowOffset,
            Collections.unmodifiableList(new ArrayList<>(spell)));
        craftingCells.clear();
        glyphButtons.clear();
    }

    @Inject(method = {"init()V", "m_7856_()V"}, at = @At("TAIL"))
    private void ans$restoreResizeDraftAndBookmark(CallbackInfo ci) {
        InfinityDraft draft = ans$resizingDraft;
        ans$resizingDraft = null;
        if (draft != null) {
            ans$selectSlot(draft.slot());
            spell_name.setValue(draft.name());
            searchBar.setValue(draft.query());
            spell = new ArrayList<>(draft.recipe());
            page = draft.page();
            if (((InfinityGuiSpellBook) (Object) this).nextGlyphButton != null) {
                ((InfinityGuiSpellBook) (Object) this).updateWindowOffset(draft.windowOffset());
            } else {
                for (int i = 0; i < craftingCells.size(); i++) {
                    InfinityCraftingButton cell = craftingCells.get(i);
                    cell.clear();
                    cell.slotNum = i;
                    if (i < spell.size() && spell.get(i) != null) {
                        cell.setAbstractSpellPart(spell.get(i));
                        cell.spellTag = spell.get(i).getRegistryName();
                    }
                }
            }
        }
        browserRefreshPalette();
        ans$browserBookmark = addRenderableWidget(new ForgeGlyphBookmarkButton(
            bookLeft - 15, bookTop + 166, ignored -> ans$browserController.open()));
        ans$updateBookmark();
    }

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
        return ans$supportedEditor ? ForgeEditorSupport.knowledge(ans$bookSession, true) : List.of();
    }
    @Override public int browserBookTier() { return ForgeEditorSupport.bookTier(bookStack); }
    @Override public boolean browserCreative() { return ForgeEditorSupport.creative(bookStack); }
    @Override public boolean browserOwnsBook() { return ans$supportedEditor && ForgeEditorSupport.ownsBook(ans$bookSession); }

    @Nullable
    @Override
    public Component browserError(AbstractSpellPart part) {
        int insertion = -1;
        for (InfinityCraftingButton cell : craftingCells) {
            if (cell.getAbstractSpellPart() == null) {
                insertion = cell.slotNum;
                break;
            }
        }
        return ForgeEditorSupport.insertionError(browserKnowledge(), browserOwnsBook(), spellValidator,
            new ArrayList<>(spell), insertion, part);
    }

    @Override
    public void browserInsert(AbstractSpellPart part) {
        if (browserError(part) != null) return;
        GlyphButton synthetic = new GlyphButton(0, 0, part, ignored -> {});
        ((InfinityGuiSpellBook) (Object) this).onGlyphClick(synthetic);
    }

    @Override
    public void browserRefreshPalette() {
        if (!ans$supportedEditor) return;
        int requestedPage = page;
        String query = browserQuery();
        // Infinity's creative editor includes every registry value, in registry order; retain
        // exactly the non-imported native projection and only append locally projected imports.
        ArrayList<AbstractSpellPart> eligible = new ArrayList<>(ans$nativePalette);
        for (AbstractSpellPart part : browserKnowledge())
            if (part instanceof com.otectus.arsnspells.glyph.ImportedIronEffect
                    && ForgeEditorSupport.nativePaletteEligible(part, browserCreative(), true)
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
        ans$validateEditor();
    }

    @Override
    public List<?> browserDraft() {
        return ForgeEditorSupport.draftSnapshot(selectedSpellSlot, spell_name == null ? "" : spell_name.getValue(),
            spellWindowOffset, new ArrayList<>(spell));
    }

    @Override public String browserQuery() { return searchBar == null ? previousString : searchBar.getValue(); }

    @Unique
    private record InfinityDraft(int slot, String name, String query, int page, int windowOffset,
                                 List<AbstractSpellPart> recipe) {}
}
