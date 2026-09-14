package com.otectus.arsnspells.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;

/** Wrapped inspection text with mouse wheel, draggable scrollbar and keyboard access. */
final class ScrollableTextPanel extends AbstractWidget {
    private final Font font;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private List<Component> paragraphs = List.of();
    private int scroll;
    private boolean draggingScrollbar;

    ScrollableTextPanel(Font font, int x, int y, int width, int height, List<Component> paragraphs) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        setParagraphs(paragraphs);
    }

    void setParagraphs(List<Component> paragraphs) {
        if (this.paragraphs.equals(paragraphs)) return;
        this.paragraphs = List.copyOf(paragraphs);
        lines.clear();
        var narration = Component.empty();
        for (Component paragraph : paragraphs) {
            narration.append(paragraph).append(". ");
            lines.addAll(font.split(paragraph, width - 20));
            lines.add(FormattedCharSequence.EMPTY);
        }
        setMessage(narration);
        scrollTo(scroll);
    }

    private int lineHeight() { return font.lineHeight + 2; }
    private int maxScroll() { return Math.max(0, lines.size() * lineHeight() - (height - 8)); }
    private void scrollTo(int value) { scroll = Math.max(0, Math.min(maxScroll(), value)); }

    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float tick) {
        VanillaGui.inset(g, getX(), getY(), width, height);
        g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, VanillaGui.PANEL);
        if (isFocused()) g.renderOutline(getX(), getY(), width, height, 0xFFFFFFFF);
        g.enableScissor(getX() + 4, getY() + 4, getX() + width - 10, getY() + height - 4);
        for (int i = 0; i < lines.size(); i++) {
            int y = getY() + 4 + i * lineHeight() - scroll;
            if (y + font.lineHeight >= getY() + 4 && y < getY() + height - 4)
                g.drawString(font, lines.get(i), getX() + 4, y, VanillaGui.TEXT, false);
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int track = height - 4;
            int thumb = Math.max(12, track * (height - 8) / (lines.size() * lineHeight()));
            int y = getY() + 2 + (track - thumb) * scroll / maxScroll();
            g.fill(getX() + width - 8, getY() + 2, getX() + width - 2, getY() + height - 2, 0xFF000000);
            g.fill(getX() + width - 8, y, getX() + width - 2, y + thumb, 0xFF808080);
            g.fill(getX() + width - 8, y, getX() + width - 3, y + thumb - 1, VanillaGui.PANEL);
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (!isMouseOver(x, y)) return false;
        scrollTo(scroll - (int) (delta * lineHeight() * 3));
        return true;
    }
    @Override public void onClick(double x, double y) {
        draggingScrollbar = x >= getX() + width - 9;
        if (draggingScrollbar) dragTo(y);
    }
    private void dragTo(double y) {
        int track = height - 4;
        int thumb = Math.max(12, track * (height - 8) / Math.max(1, lines.size() * lineHeight()));
        scrollTo((int) ((y - getY() - 2 - thumb / 2.0) * maxScroll() / Math.max(1, track - thumb)));
    }
    @Override protected void onDrag(double x, double y, double dx, double dy) {
        if (draggingScrollbar) dragTo(y);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case 264 -> scrollTo(scroll + lineHeight());
            case 265 -> scrollTo(scroll - lineHeight());
            case 267 -> scrollTo(scroll + height - 8);
            case 266 -> scrollTo(scroll - height + 8);
            case 268 -> scrollTo(0);
            case 269 -> scrollTo(maxScroll());
            default -> { return super.keyPressed(key, scan, modifiers); }
        }
        return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
