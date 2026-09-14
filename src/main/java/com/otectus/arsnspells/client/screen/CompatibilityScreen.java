package com.otectus.arsnspells.client.screen;

import com.otectus.arsnspells.client.icons.SpellIconRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.List;

/** Observed installation state, explicitly separate from gameplay verification. */
public final class CompatibilityScreen extends VanillaPanelScreen {
    private static final List<String> MODS = List.of("ars_nouveau", "irons_spellbooks", "ars_elemental", "ars_zero", "toomanyglyphs", "covenant_of_the_seven", "curios", "jei", "emi");
    private final Screen parent;
    private int page;
    private int left, top;
    public CompatibilityScreen(Screen parent) {
        super(Component.translatable("ars_n_spells.compatibility.title")); this.parent = parent;
    }
    @Override protected void init() {
        left = (width - 300) / 2; top = (height - 216) / 2;
        rebuild();
    }
    private void rebuild() {
        clearWidgets();
        addRenderableWidget(Button.builder(Component.translatable(page == 0 ? "ars_n_spells.icon_picker.next" : "gui.back"), b -> { page = 1 - page; rebuild(); })
            .bounds(left + 8, top + 186, 92, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(left + 200, top + 186, 92, 20).build());
        int end = Math.min(MODS.size(), page * 5 + 5);
        for (int i = page * 5; i < end; i++) addRenderableWidget(new StatusRow(MODS.get(i), top + 53 + (i - page * 5) * 25));
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderPanel(g, mouseX, mouseY, partialTick, left, top, 300, 216);
        g.drawString(font, title, left + 8, top + 8, VanillaGui.TEXT, false);
        VanillaGui.wrapped(g, font, Component.translatable("ars_n_spells.compatibility.evidence"), left + 8, top + 24, 284, VanillaGui.MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }
    /** A focusable row narrates the same installation evidence that sighted players see. */
    private final class StatusRow extends Button {
        private final boolean present;
        private final String name;
        private final Component status;
        StatusRow(String id, int y) {
            super(left + 8, y, 284, 23, Component.empty(), b -> {}, DEFAULT_NARRATION);
            var container = ModList.get().getModContainerById(id);
            present = container.isPresent();
            String version = container.map(c -> c.getModInfo().getVersion().toString()).orElse("");
            name = container.map(c -> c.getModInfo().getDisplayName()).orElse(id) + " " + version;
            status = Component.translatable("ars_n_spells.compatibility." + (present ? "present" : "absent"));
            setMessage(Component.literal(name).append(". ").append(status));
            setTooltip(Tooltip.create(getMessage()));
        }
        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (isHoveredOrFocused()) g.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, 0xFFB0B0B0);
            if (isFocused()) g.renderOutline(getX(), getY(), width, height, 0xFFFFFFFF);
            g.blit(SpellIconRegistry.INSTANCE.resolve("compat/" + (present ? "untested" : "absent"), "none"), getX(), getY() + 2, 0, 0, 16, 16, 16, 16);
            g.drawString(font, VanillaGui.ellipsize(font, name, 260), getX() + 22, getY(), VanillaGui.TEXT, false);
            g.drawString(font, VanillaGui.ellipsize(font, status.getString(), 260), getX() + 22, getY() + 11, VanillaGui.MUTED, false);
        }
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
