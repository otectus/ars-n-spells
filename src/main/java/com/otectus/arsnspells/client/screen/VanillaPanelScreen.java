package com.otectus.arsnspells.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Keeps the native backdrop behind the opaque panel, including on Minecraft 1.21. */
public abstract class VanillaPanelScreen extends Screen {
    protected VanillaPanelScreen(Component title) { super(title); }

    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float tick,
                               int left, int top, int width, int height) {
        super.renderBackground(g);
        VanillaGui.panel(g, left, top, width, height);
    }
}
