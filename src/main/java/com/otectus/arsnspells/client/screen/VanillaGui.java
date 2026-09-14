package com.otectus.arsnspells.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Pixel-aligned container chrome. Labels on light panels never have a text shadow. */
public final class VanillaGui {
    public static final int PANEL = 0xFFC6C6C6;
    public static final int TEXT = 0x404040;
    public static final int MUTED = 0x505050;
    private VanillaGui() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        // The stepped black corners and two-pixel bevel follow vanilla containers.
        g.fill(x + 2, y, x + w - 2, y + h, 0xFF000000);
        g.fill(x, y + 2, x + w, y + h - 2, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL);
        g.fill(x + 2, y + 2, x + w - 3, y + 4, 0xFFFFFFFF);
        g.fill(x + 2, y + 4, x + 4, y + h - 3, 0xFFFFFFFF);
        g.fill(x + 4, y + h - 4, x + w - 2, y + h - 2, 0xFF555555);
        g.fill(x + w - 4, y + 4, x + w - 2, y + h - 4, 0xFF555555);
    }

    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
        g.fill(x, y, x + w - 1, y + 1, 0xFF373737);
        g.fill(x, y + 1, x + 1, y + h - 1, 0xFF373737);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y, x + w, y + h - 1, 0xFFFFFFFF);
    }

    public static void slot(GuiGraphics g, int x, int y) { inset(g, x - 1, y - 1, 18, 18); }

    /** Same 22-pixel empty recipe arrow silhouette as vanilla crafting. */
    public static void arrow(GuiGraphics g, int x, int y) {
        for (int row = 0; row < 15; row++) {
            int half = 7 - Math.abs(7 - row);
            g.fill(x + 14, y + row, x + 15 + half, y + row + 1, 0xFF8B8B8B);
        }
        g.fill(x, y + 4, x + 15, y + 11, 0xFF8B8B8B);
    }

    public static String ellipsize(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    public static void centered(GuiGraphics g, Font font, Component text, int center, int y, int color) {
        g.drawString(font, text, center - font.width(text) / 2, y, color, false);
    }

    public static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) {
            g.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 2;
        }
        return y;
    }
}
