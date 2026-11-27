package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.client.gui.book.BaseBook;
import com.hollingsworth.arsnouveau.client.gui.book.GlyphUnlockMenu;
import com.hollingsworth.arsnouveau.common.crafting.recipes.GlyphRecipe;
import com.otectus.arsnspells.client.glyph.BrowserPlatform;
import com.otectus.arsnspells.client.GlyphBrowserClientEvents;
import com.otectus.arsnspells.client.glyph.GlyphAcquisitionScreen;
import com.otectus.arsnspells.config.AnsClientConfig;
import com.otectus.arsnspells.config.DisplayMode;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Filters only the acquisition presentation; every recipe remains in the recipe manager. */
@Mixin(GlyphUnlockMenu.class)
public abstract class MixinGlyphUnlockMenuBrowser extends BaseBook {
    @Shadow public List<RecipeHolder<GlyphRecipe>> allParts;
    @Shadow public List<RecipeHolder<GlyphRecipe>> displayedGlyphs;
    @Shadow public RecipeHolder<GlyphRecipe> selectedRecipe;
    @Unique private List<RecipeHolder<GlyphRecipe>> arsnspells$sourceRecipes = List.of();
    @Unique private List<RecipeHolder<GlyphRecipe>> arsnspells$groupedRecipes = List.of();
    @Unique private DisplayMode arsnspells$mode;
    @Unique private long arsnspells$recipeRevision;
    @Unique private long arsnspells$generation = Long.MIN_VALUE;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void arsnspells$captureRecipes(net.minecraft.core.BlockPos pos, CallbackInfo ci) {
        arsnspells$sourceRecipes = List.copyOf(allParts);
        arsnspells$recipeRevision = GlyphBrowserClientEvents.recipeRevision();
        arsnspells$projectRecipes();
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void arsnspells$refreshBeforeLayout(CallbackInfo ci) {
        arsnspells$projectRecipes();
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void arsnspells$addGroupedEntry(CallbackInfo ci) {
        if (arsnspells$mode != DisplayMode.GROUPED || arsnspells$groupedRecipes.isEmpty() && !BrowserPlatform.enabled()) return;
        addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.browser.acquisition.bookmark"), button ->
                BrowserPlatform.push(new GlyphAcquisitionScreen((GlyphUnlockMenu) (Object) this)))
            .bounds(bookLeft + 18, bookTop + 2, 126, 14).build());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void arsnspells$observeMode(GuiGraphics graphics, int mouseX, int mouseY, float frame, CallbackInfo ci) {
        if (arsnspells$mode != AnsClientConfig.mode() || arsnspells$generation != BrowserPlatform.generation()
                || arsnspells$recipeRevision != GlyphBrowserClientEvents.recipeRevision()) {
            GlyphUnlockMenu menu = (GlyphUnlockMenu) (Object) this;
            String query = menu.searchBar == null ? "" : menu.searchBar.getValue();
            rebuildWidgets();
            if (!query.isEmpty()) menu.searchBar.setValue(query);
        }
    }

    @Unique private void arsnspells$projectRecipes() {
        if (arsnspells$recipeRevision != GlyphBrowserClientEvents.recipeRevision()) {
            arsnspells$recipeRevision = GlyphBrowserClientEvents.recipeRevision();
            arsnspells$sourceRecipes = GlyphAcquisitionScreen.recipes();
            if (selectedRecipe != null) {
                var id = selectedRecipe.id();
                selectedRecipe = arsnspells$sourceRecipes.stream().filter(holder -> holder.id().equals(id)).findFirst().orElse(null);
            }
        }
        arsnspells$mode = AnsClientConfig.mode();
        arsnspells$generation = BrowserPlatform.generation();
        List<RecipeHolder<GlyphRecipe>> nativeRecipes = new ArrayList<>();
        List<RecipeHolder<GlyphRecipe>> importedRecipes = new ArrayList<>();
        for (var recipe : arsnspells$sourceRecipes) {
            if (recipe.value().getSpellPart() instanceof ImportedIronEffect effect) {
                if (BrowserPlatform.available(effect)) importedRecipes.add(recipe);
            } else nativeRecipes.add(recipe);
        }
        arsnspells$groupedRecipes = arsnspells$mode == DisplayMode.GROUPED ? List.copyOf(importedRecipes) : List.of();
        if (arsnspells$mode == DisplayMode.FLAT) nativeRecipes.addAll(importedRecipes);
        if (selectedRecipe != null && selectedRecipe.value().getSpellPart() instanceof ImportedIronEffect selected
                && (arsnspells$mode == DisplayMode.HIDDEN || !BrowserPlatform.available(selected))) selectedRecipe = null;
        allParts = nativeRecipes;
        displayedGlyphs = new ArrayList<>(allParts);
    }
}
