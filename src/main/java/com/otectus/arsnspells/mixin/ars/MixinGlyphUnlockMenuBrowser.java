package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.client.gui.book.BaseBook;
import com.hollingsworth.arsnouveau.client.gui.book.GlyphUnlockMenu;
import com.hollingsworth.arsnouveau.client.gui.buttons.UnlockGlyphButton;
import com.hollingsworth.arsnouveau.client.gui.NoShadowTextField;
import com.hollingsworth.arsnouveau.common.crafting.recipes.GlyphRecipe;
import com.otectus.arsnspells.client.glyph.BrowserPlatform;
import com.otectus.arsnspells.client.glyph.forge.ForgeGlyphAcquisition;
import com.otectus.arsnspells.client.glyph.forge.ForgeGlyphAcquisitionScreen;
import com.otectus.arsnspells.config.AnsClientConfig;
import com.otectus.arsnspells.config.DisplayMode;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local presentation filtering for the real Forge 1.20.1 Scribe's Table candidate screen. */
@Mixin(value = GlyphUnlockMenu.class, remap = false)
public abstract class MixinGlyphUnlockMenuBrowser extends BaseBook {
    @Shadow public List<AbstractSpellPart> allParts;
    @Shadow public List<AbstractSpellPart> displayedGlyphs;
    @Shadow public List<UnlockGlyphButton> glyphButtons;
    @Shadow public NoShadowTextField searchBar;
    @Shadow public int page;
    @Shadow public net.minecraft.client.gui.screens.inventory.PageButton nextButton;
    @Shadow public net.minecraft.client.gui.screens.inventory.PageButton previousButton;
    @Shadow public GlyphRecipe selectedRecipe;
    @Shadow int maxPerPage;

    @Unique private List<AbstractSpellPart> ans$sourceParts = List.of();
    @Unique private Button ans$acquisitionRoute;
    @Unique private long ans$generation = Long.MIN_VALUE;
    @Unique private DisplayMode ans$mode;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ans$projectCandidates(net.minecraft.core.BlockPos ignored, CallbackInfo ci) {
        ans$sourceParts = List.copyOf(allParts);
        ans$applyMode();
        ans$generation = BrowserPlatform.generation();
        ans$mode = AnsClientConfig.mode();
    }

    @Inject(method = {"init()V", "m_7856_()V"}, at = @At("TAIL"))
    private void ans$addAcquisitionRoute(CallbackInfo ci) {
        ans$acquisitionRoute = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                boolean grouped = AnsClientConfig.mode() == DisplayMode.GROUPED;
                BrowserPlatform.push(new ForgeGlyphAcquisitionScreen((GlyphUnlockMenu) (Object) this, grouped));
            })
            .bounds(bookLeft + 128, bookBottom - 13, 86, 12).build());
        ans$updateRoute();
    }

    @Override
    public void tick() {
        super.tick();
        long generation = BrowserPlatform.generation();
        DisplayMode mode = AnsClientConfig.mode();
        if (generation == ans$generation && mode == ans$mode) return;
        ans$generation = generation;
        ans$mode = mode;
        int requestedPage = page;
        String query = searchBar == null ? "" : searchBar.getValue();
        ans$sourceParts = GlyphRegistry.getSpellpartMap().values().stream()
            .filter(AbstractSpellPart::shouldShowInUnlock).toList();
        ans$applyMode();
        if (selectedRecipe != null && selectedRecipe.getSpellPart() instanceof ImportedIronEffect) selectedRecipe = null;
        if (searchBar != null) ((GlyphUnlockMenu) (Object) this).onSearchChanged(query);
        page = Math.max(0, Math.min(requestedPage, Math.max(0, displayedGlyphs.size() - 1) / Math.max(1, maxPerPage)));
        if (nextButton != null && previousButton != null) {
            previousButton.visible = previousButton.active = page > 0;
            nextButton.visible = nextButton.active = (page + 1) * maxPerPage < displayedGlyphs.size();
            ((GlyphUnlockMenu) (Object) this).layoutAllGlyphs(page);
        }
        ans$updateRoute();
    }

    @Unique
    private void ans$updateRoute() {
        if (ans$acquisitionRoute == null) return;
        DisplayMode mode = AnsClientConfig.mode();
        boolean grouped = mode == DisplayMode.GROUPED;
        boolean show = grouped ? !ForgeGlyphAcquisition.groups().isEmpty() || ForgeGlyphAcquisition.hasAvailableImports()
            : mode == DisplayMode.FLAT && ForgeGlyphAcquisition.hasPressOnlyImports();
        ans$acquisitionRoute.visible = show;
        ans$acquisitionRoute.active = show;
        ans$acquisitionRoute.setMessage(Component.translatable(grouped
            ? "ars_n_spells.browser.acquisition.bookmark"
            : "ars_n_spells.browser.acquisition.press_button"));
    }

    /** Ars normally assumes recipe ID == glyph ID; preserve real datapack routes that do not. */
    @Inject(method = "layoutAllGlyphs", at = @At("TAIL"))
    private void ans$attachActualRecipes(int ignoredPage, CallbackInfo ci) {
        if (AnsClientConfig.mode() != DisplayMode.FLAT) return;
        Map<AbstractSpellPart, com.hollingsworth.arsnouveau.common.crafting.recipes.GlyphRecipe> recipes = new IdentityHashMap<>();
        for (ForgeGlyphAcquisition.Group group : ForgeGlyphAcquisition.groups())
            for (ForgeGlyphAcquisition.Choice choice : group.choices()) recipes.putIfAbsent(choice.part(), choice.recipe());
        for (UnlockGlyphButton button : glyphButtons) {
            if (button.spellPart instanceof ImportedIronEffect && recipes.containsKey(button.spellPart))
                button.recipe = recipes.get(button.spellPart);
        }
    }

    @Unique
    private void ans$applyMode() {
        DisplayMode mode = AnsClientConfig.mode();
        Set<AbstractSpellPart> realRecipes = ForgeGlyphAcquisition.realRecipeParts();
        ArrayList<AbstractSpellPart> projected = new ArrayList<>(ans$sourceParts.size());
        for (AbstractSpellPart part : ans$sourceParts) {
            if (!(part instanceof ImportedIronEffect imported)) {
                projected.add(part);
            } else if (mode == DisplayMode.FLAT && BrowserPlatform.available(imported) && realRecipes.contains(part)) {
                projected.add(part);
            }
        }
        allParts = projected;
        displayedGlyphs = new ArrayList<>(projected);
    }
}
