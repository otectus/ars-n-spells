package com.otectus.arsnspells.client.screen;

import com.otectus.arsnspells.menu.SpellLoomMenu;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.SpellLoomExportPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Spell Loom GUI: name the spell, cycle a nature and a rudimentary icon symbol,
 * then inscribe onto the blank Iron's scroll. Drawn with flat fills (no shipped
 * GUI texture) so the workstation needs only JSON + vanilla block textures.
 */
public class SpellLoomScreen extends AbstractContainerScreen<SpellLoomMenu> {
    // Canonical nature keys live in CrossModSpellComponents (the export payload
    // whitelists against them); each maps to an ARGB tint used for the preview swatch.
    private static final String[] NATURES =
        com.otectus.arsnspells.spell.CrossModSpellComponents.NATURE_KEYS.toArray(new String[0]);
    private static final int[] NATURE_COLORS = {
        0xFFB060FF, 0xFFFF6030, 0xFF60D0FF, 0xFFFFE040, 0xFF40C040,
        0xFFFFF0A0, 0xFFD03030, 0xFF9040E0
    };
    // Canonical symbol list lives in CrossModSpellComponents so the wheel-icon
    // mixin, the shipped icon_<key>.png textures, and this cycle button can never drift.
    private static final String[] ICONS =
        com.otectus.arsnspells.spell.CrossModSpellComponents.ICON_SYMBOLS.toArray(new String[0]);

    private EditBox nameField;
    private int natureIndex = 0;
    private int iconIndex = 0;

    public SpellLoomScreen(SpellLoomMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        nameField = new EditBox(this.font, x + 8, y + 18, 160, 14,
            Component.translatable("ars_n_spells.spell_loom.name_hint"));
        nameField.setMaxLength(SpellLoomExportPayload.MAX_NAME);
        nameField.setHint(Component.translatable("ars_n_spells.spell_loom.name_hint"));
        addRenderableWidget(nameField);

        addRenderableWidget(Button.builder(natureLabel(), b -> {
            natureIndex = (natureIndex + 1) % NATURES.length;
            b.setMessage(natureLabel());
        }).bounds(x + 8, y + 54, 78, 18).build());

        addRenderableWidget(Button.builder(iconLabel(), b -> {
            iconIndex = (iconIndex + 1) % ICONS.length;
            b.setMessage(iconLabel());
        }).bounds(x + 90, y + 54, 78, 18).build());

        // Inscribe button spanning the panel below the nature/icon row.
        addRenderableWidget(Button.builder(
            Component.translatable("ars_n_spells.spell_loom.export"), b -> sendExport())
            .bounds(x + 8, y + 74, 160, 18).build());
    }

    private Component natureLabel() {
        return Component.translatable("ars_n_spells.spell_loom.nature",
            Component.translatable("ars_n_spells.nature." + NATURES[natureIndex]));
    }

    private Component iconLabel() {
        return Component.translatable("ars_n_spells.spell_loom.icon",
            Component.translatable("ars_n_spells.icon." + ICONS[iconIndex]));
    }

    private void sendExport() {
        String name = nameField.getValue() == null ? "" : nameField.getValue().trim();
        PacketHandler.sendToServer(new SpellLoomExportPayload(
            name, NATURES[natureIndex], ICONS[iconIndex]));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        // Panel + a subtle border.
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B2233);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFF3C3147);
        // Working-slot backgrounds (match SlotItemHandler positions in the menu).
        drawSlot(g, x + 44, y + 35);
        drawSlot(g, x + 80, y + 35);
        drawSlot(g, x + 134, y + 35);
        g.drawString(this.font, "+", x + 66, y + 39, 0xFFFFFF, false);
        g.drawString(this.font, "→", x + 116, y + 39, 0xFFFFFF, false);
        // Icon preview swatch next to the output.
        g.fill(x + 152, y + 35, x + 168, y + 51, NATURE_COLORS[natureIndex]);
    }

    private void drawSlot(GuiGraphics g, int sx, int sy) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF14101A);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1.21.1: AbstractContainerScreen.render draws the blurred background itself.
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Let the name field consume typing (incl. space/E) unless ESC is pressed.
        if (nameField != null && nameField.isFocused() && keyCode != 256) {
            return nameField.keyPressed(keyCode, scanCode, modifiers)
                || nameField.canConsumeInput()
                || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
