package com.otectus.arsnspells.client.glyph.forge;

import com.hollingsworth.arsnouveau.client.gui.book.GlyphUnlockMenu;
import com.otectus.arsnspells.client.glyph.BrowserPlatform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** A bounded two-step chooser for real imported GlyphRecipes, layered over the native menu. */
public final class ForgeGlyphAcquisitionScreen extends Screen {
    private static final int PER_PAGE = 8;

    private final GlyphUnlockMenu host;
    private final boolean showGroups;
    private final List<ForgeGlyphAcquisition.Group> groups;
    private ForgeGlyphAcquisition.Group selected;
    private int page;

    public ForgeGlyphAcquisitionScreen(GlyphUnlockMenu host, boolean showGroups) {
        super(Component.translatable("ars_n_spells.browser.acquisition.title"));
        this.host = host;
        this.showGroups = showGroups;
        this.groups = showGroups ? ForgeGlyphAcquisition.groups() : List.of();
    }

    @Override
    protected void init() {
        rebuildAcquisitionWidgets();
    }

    private void rebuildAcquisitionWidgets() {
        clearWidgets();
        List<?> entries = selected == null ? groups : selected.choices();
        page = Math.max(0, Math.min(page, Math.max(0, entries.size() - 1) / PER_PAGE));
        int panelWidth = Math.min(260, width - 24);
        int left = (width - panelWidth) / 2;
        int top = Math.max(18, (height - 210) / 2);
        int start = page * PER_PAGE;
        int end = Math.min(entries.size(), start + PER_PAGE);
        for (int i = start; i < end; i++) {
            int row = i - start;
            Component label;
            Button.OnPress action;
            if (selected == null) {
                ForgeGlyphAcquisition.Group group = groups.get(i);
                label = Component.literal(group.name()).append(Component.literal(" (" + group.choices().size() + ")"));
                action = ignored -> {
                    selected = group;
                    page = 0;
                    rebuildAcquisitionWidgets();
                };
            } else {
                ForgeGlyphAcquisition.Choice choice = selected.choices().get(i);
                label = choice.part().displayName();
                action = ignored -> {
                    ForgeGlyphAcquisition.select(host, choice);
                    BrowserPlatform.pop();
                };
            }
            addRenderableWidget(Button.builder(label, action).bounds(left + 18, top + 34 + row * 18, panelWidth - 36, 16).build());
        }
        if (page > 0)
            addRenderableWidget(Button.builder(Component.literal("<"), ignored -> { page--; rebuildAcquisitionWidgets(); })
                .bounds(left + 18, top + 181, 24, 16).build());
        if (end < entries.size())
            addRenderableWidget(Button.builder(Component.literal(">"), ignored -> { page++; rebuildAcquisitionWidgets(); })
                .bounds(left + panelWidth - 42, top + 181, 24, 16).build());
        Component back = selected == null ? Component.translatable("gui.close") : Component.translatable("gui.back");
        addRenderableWidget(Button.builder(back, ignored -> onClose()).bounds(left + panelWidth / 2 - 35, top + 181, 70, 16).build());
    }

    @Override
    public void onClose() {
        if (selected != null) {
            selected = null;
            page = 0;
            rebuildAcquisitionWidgets();
        } else {
            BrowserPlatform.pop();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = Math.min(260, width - 24);
        int left = (width - panelWidth) / 2;
        int top = Math.max(18, (height - 210) / 2);
        graphics.fill(left, top, left + panelWidth, top + 205, 0xE0101018);
        graphics.renderOutline(left, top, panelWidth, 205, 0xFFB9A4D8);
        Component heading = selected == null ? title : Component.literal(selected.name());
        graphics.drawCenteredString(font, heading, width / 2, top + 10, 0xFFFFFF);
        if (selected == null && groups.isEmpty()) {
            Component help = showGroups
                ? Component.translatable("ars_n_spells.browser.press_help")
                : Component.translatable("ars_n_spells.browser.acquisition.press_only");
            graphics.drawWordWrap(font, help, left + 18, top + 45, panelWidth - 36, 0xE0E0E0);
        } else if (selected == null) {
            graphics.drawCenteredString(font, Component.translatable("ars_n_spells.browser.acquisition.choose_spell"),
                width / 2, top + 22, 0xC8C8C8);
        } else {
            graphics.drawCenteredString(font, Component.translatable("ars_n_spells.browser.acquisition.choose_level"),
                width / 2, top + 22, 0xC8C8C8);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
