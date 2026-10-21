package com.otectus.arsnspells.client.jei;

import com.otectus.arsnspells.compat.jei.GlyphPressJeiRecipe;
import com.otectus.arsnspells.client.screen.VanillaGui;

import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.ImportedGlyphTooltip;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** JEI category for Glyph Press conversions: scroll, plate and catalysts in, the level's glyph out. */
public final class GlyphPressCategory implements IRecipeCategory<GlyphPressJeiRecipe> {
    public static final RecipeType<GlyphPressJeiRecipe> TYPE = RecipeType.create("ars_n_spells", "glyph_press", GlyphPressJeiRecipe.class);
    private static final int WIDTH = 150, HEIGHT = 64;
    private static final int ARROW_X = 74, ARROW_Y = 4;

    private final IDrawable icon;
    private final IDrawableStatic arrow;

    public GlyphPressCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemLike(ModBlocksRegistry.GLYPH_PRESS_ITEM.get());
        this.arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<GlyphPressJeiRecipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("ars_n_spells.jei.glyph_press"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public ResourceLocation getRegistryName(GlyphPressJeiRecipe recipe) { return recipe.id(); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, GlyphPressJeiRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 4, 4).setStandardSlotBackground().addItemStack(recipe.scroll());
        builder.addSlot(RecipeIngredientRole.INPUT, 24, 4).setStandardSlotBackground().addItemStack(recipe.plate());
        builder.addSlot(RecipeIngredientRole.INPUT, 44, 4).setStandardSlotBackground().addItemStacks(recipe.catalysts());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 110, 4).setOutputSlotBackground().addItemStack(recipe.output())
            .addTooltipCallback((view, tooltip) -> {
                List<Component> lines = new ArrayList<>();
                ImportedGlyphTooltip.append(recipe.definition(), lines);
                tooltip.addAll(lines);
            });
    }

    @Override
    public void draw(GlyphPressJeiRecipe recipe, IRecipeSlotsView view, GuiGraphics g, double mouseX, double mouseY) {
        arrow.draw(g, ARROW_X, ARROW_Y);
        var font = Minecraft.getInstance().font;
        int y = 27;
        for (Component line : description(recipe)) {
            g.drawString(font, VanillaGui.ellipsize(font, line.getString(), WIDTH - 4), 2, y, 0xFF404040, false);
            y += 11;
        }
    }

    private List<Component> description(GlyphPressJeiRecipe recipe) {
        String seconds = String.format(Locale.ROOT, "%.1f", recipe.terms().durationTicks() / 20.0);
        return List.of(
            Component.translatable("ars_n_spells.jei.glyph_press.source",
                NumberFormat.getIntegerInstance(Locale.ROOT).format(recipe.terms().sourceCost()), seconds),
            Component.translatable("ars_n_spells.jei.glyph_press.tier", ImportedGlyphDefinition.Numerals.roman(recipe.tier()))
                .append(" · ").append(Component.translatable("ars_n_spells.jei.glyph_press.level", recipe.definition().level())),
            Component.translatable("ars_n_spells.jei.glyph_press.learn"));
    }

    @Override
    public List<Component> getTooltipStrings(GlyphPressJeiRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        return mouseX >= 0 && mouseX < WIDTH && mouseY >= 26 && mouseY < HEIGHT ? description(recipe) : List.of();
    }
}
