package com.otectus.arsnspells.client.icons;

import com.otectus.arsnspells.icons.IconCatalog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Development-only capture surface for every rendered state/background combination. */
final class IconReviewScreen extends com.otectus.arsnspells.client.screen.VanillaPanelScreen {
    private static final String[] STATES = {"normal", "selected", "disabled", "high_contrast", "monochrome"};
    IconReviewScreen() { super(Component.literal("Icon states and backgrounds")); }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = (width - 300) / 2, y = (height - 218) / 2;
        renderPanel(g, mouseX, mouseY, partialTick, x, y, 300, 218);
        g.drawString(font, title, x + 8, y + 6, 0x404040, false);
        String[] labels = {"Normal", "Select", "Disable", "Hi-C", "Mono"};
        for (int col = 0; col < 5; col++) g.drawString(font, labels[col], x + 80 + 43 * col, y + 20, 0x404040, false);
        for (int row = 0; row < IconCatalog.BACKGROUNDS.size(); row++) {
            String bg = IconCatalog.BACKGROUNDS.get(row);
            g.drawString(font, bg, x + 8, y + 36 + row * 16, 0x404040, false);
            for (int col = 0; col < 5; col++) {
                int px = x + 87 + col * 43, py = y + 32 + row * 16;
                g.blit(SpellIconRegistry.INSTANCE.resolve("spell/fireball", bg, STATES[col]), px, py, 0, 0, 16, 16, 16, 16);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
