package com.otectus.arsnspells.compat.jei;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.glyph.recipe.GlyphPressRecipes;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** JEI category for Glyph Press conversions: real scroll in, exact level-specific glyph out. */
public final class GlyphPressRecipeCategory extends AbstractRecipeCategory<GlyphPressRecipes.Entry> {
    public static final RecipeType<GlyphPressRecipes.Entry> TYPE =
        RecipeType.create(ArsNSpells.MODID, "glyph_press", GlyphPressRecipes.Entry.class);

    public GlyphPressRecipeCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("ars_n_spells.jei.glyph_press"),
            gui.createDrawableItemLike(ModBlocksRegistry.GLYPH_PRESS_ITEM.get()), 150, 64);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, GlyphPressRecipes.Entry recipe, IFocusGroup focuses) {
        builder.addInputSlot(4, 4).setStandardSlotBackground().addItemStack(recipe.scroll());
        builder.addInputSlot(24, 4).setStandardSlotBackground().addItemStack(new ItemStack(ModItemsRegistry.blankGlyphPlate().get()));
        builder.addInputSlot(44, 4).setStandardSlotBackground().addItemStacks(recipe.catalysts());
        builder.addOutputSlot(110, 4).setStandardSlotBackground().addItemStack(recipe.output());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, GlyphPressRecipes.Entry recipe, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(Math.max(1, recipe.ticks())).setPosition(74, 4);
        List<FormattedText> lines = new ArrayList<>();
        lines.add(Component.translatable("ars_n_spells.jei.glyph_press.terms", recipe.source(),
            String.format(Locale.ROOT, "%.1f", recipe.ticks() / 20.0), recipe.tier()));
        if (recipe.requiresLearning()) {
            lines.add(Component.translatable("ars_n_spells.jei.glyph_press.learning").withStyle(ChatFormatting.DARK_PURPLE));
        }
        builder.addText(lines, 146, 36).setPosition(2, 26).setColor(0xFF404040);
    }
}
