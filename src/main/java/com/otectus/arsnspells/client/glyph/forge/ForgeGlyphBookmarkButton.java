package com.otectus.arsnspells.client.glyph.forge;

import com.otectus.arsnspells.registry.ModItemsRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Ars-sized side bookmark carrying the real blank glyph plate instead of a text-menu glyph. */
public final class ForgeGlyphBookmarkButton extends Button {
    private static final ResourceLocation BOOKMARK =
        new ResourceLocation("ars_nouveau", "textures/gui/worn_book_bookmark.png");
    private final ItemStack plate = new ItemStack(ModItemsRegistry.blankGlyphPlate().get());

    public ForgeGlyphBookmarkButton(int x, int y, OnPress onPress) {
        super(x, y, 23, 20, Component.translatable("ars_n_spells.browser.bookmark"), onPress,
            supplied -> AbstractWidget.wrapDefaultNarrationMessage(supplied.get()));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(BOOKMARK, getX(), getY(), 0, 0, width, height, width, height);
        if (isHoveredOrFocused()) graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x20FFFFFF);
        renderString(graphics, Minecraft.getInstance().font, active ? 0xFFFFFF : 0xA0A0A0);
    }

    /** AbstractButton routes its visual label here; this bookmark uses the item model as that label. */
    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
        graphics.renderItem(plate, getX() + 3, getY() + 2);
    }
}
