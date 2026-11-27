package com.otectus.arsnspells.client.glyph;

import com.otectus.arsnspells.config.AnsClientConfig;
import com.otectus.arsnspells.config.DisplayMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static com.otectus.arsnspells.client.glyph.BrowserController.tr;

/** A separate local draft, also usable before the player joins a world. */
public final class PersonalDisplayScreen extends Screen {
    private DisplayMode original = AnsClientConfig.mode();
    private DisplayMode draft = original;
    private Button mode;
    public PersonalDisplayScreen() { super(tr("personal")); }

    @Override protected void init() {
        int x = (width - panelWidth()) / 2 + 10, y = height / 2, w = panelWidth() - 20;
        mode = addRenderableWidget(Button.builder(label(), b -> {
            draft = DisplayMode.values()[(draft.ordinal() + 1) % DisplayMode.values().length];
            b.setMessage(label());
        }).bounds(x, y - 38, w, 20).tooltip(Tooltip.create(tr("exception"))).build());
        int third = (w - 4) / 3;
        addRenderableWidget(Button.builder(tr("reset"), b -> { draft = DisplayMode.GROUPED; mode.setMessage(label()); })
            .bounds(x, y + 51, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(x + third + 2, y + 51, third, 20).build());
        addRenderableWidget(Button.builder(tr("apply"), b -> { AnsClientConfig.setMode(draft); onClose(); })
            .bounds(x + third * 2 + 4, y + 51, w - third * 2 - 4, 20).build());
    }

    @Override public void tick() {
        DisplayMode current = AnsClientConfig.mode();
        if (current != original) {
            if (draft == original) { draft = current; mode.setMessage(label()); }
            original = current;
        }
    }
    private Component label() { return tr("mode_button", tr("mode." + draft.id())); }
    private int panelWidth() { return Math.min(330, width - 12); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { BrowserPlatform.pop(); }

    /** The underlying editor is already rendered by NeoForge's GUI layer stack. */
    @Override public void renderBackground(GuiGraphics graphics, int mx, int my, float frame) {}

    @Override public void render(GuiGraphics g, int mx, int my, float frame) {
        int w = panelWidth(), x = (width - w) / 2, y = height / 2;
        g.fill(0, 0, width, height, 0x77000000);
        GlyphLibraryScreen.paper(g, x, y - 74, w, 154);
        g.drawCenteredString(font, title, width / 2, y - 60, 0xFFF4E7BB);
        int lineY = y - 12;
        for (var line : font.split(tr("description"), w - 24)) {
            g.drawString(font, line, x + 12, lineY, 0xFF352819, false);
            lineY += font.lineHeight + 1;
        }
        for (var line : font.split(tr("exception"), w - 24)) {
            g.drawString(font, line, x + 12, lineY + 3, 0xFF4E3922, false);
            lineY += font.lineHeight + 1;
        }
        super.render(g, mx, my, frame);
    }
}
