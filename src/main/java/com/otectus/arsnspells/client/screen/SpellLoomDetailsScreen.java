package com.otectus.arsnspells.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read-only inspection of synced Loom slots plus explicit local cosmetic preset controls. */
final class SpellLoomDetailsScreen extends VanillaPanelScreen {
    private final SpellLoomScreen parent;
    private final LoomCosmeticPresets presets = new LoomCosmeticPresets();
    private int selected, left, top;
    private Component status = Component.empty();
    private Button applyButton;
    private ScrollableTextPanel inspection;
    SpellLoomDetailsScreen(SpellLoomScreen parent) {
        super(Component.translatable("ars_n_spells.spell_loom.details.title")); this.parent = parent;
    }
    @Override protected void init() {
        left = (width - 300) / 2; top = (height - 222) / 2;
        inspection = addRenderableWidget(new ScrollableTextPanel(font, left + 8, top + 22, 284, 98, parent.inspectionLines()));
        addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.spell_loom.preset.slot", selected + 1), button -> {
            selected = (selected + 1) % LoomCosmeticPresets.COUNT;
            button.setMessage(Component.translatable("ars_n_spells.spell_loom.preset.slot", selected + 1));
            status = presetDescription();
            applyButton.active = presets.get(selected) != null;
        }).bounds(left + 8, top + 126, 78, 20).build());
        applyButton = addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.spell_loom.preset.apply"), button -> {
            var preset = presets.get(selected);
            if (preset != null) parent.applyPreset(preset);
            status = Component.translatable(preset == null ? "ars_n_spells.spell_loom.preset.empty" : "ars_n_spells.spell_loom.preset.applied");
        }).bounds(left + 92, top + 126, 94, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.spell_loom.preset.save"), button -> {
            status = Component.translatable(presets.save(selected, parent.cosmeticPreset())
                ? "ars_n_spells.spell_loom.preset.saved" : "ars_n_spells.spell_loom.preset.failed");
            applyButton.active = presets.get(selected) != null;
        }).bounds(left + 192, top + 126, 100, 20)
            .tooltip(Tooltip.create(Component.translatable("ars_n_spells.spell_loom.preset.save_help"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(left + 100, top + 194, 100, 20).build());
        status = presetDescription();
        applyButton.active = presets.get(selected) != null;
    }
    private Component presetDescription() {
        var preset = presets.get(selected);
        return preset == null ? Component.translatable("ars_n_spells.spell_loom.preset.empty")
            : Component.translatable("ars_n_spells.spell_loom.preset.summary", preset.name().isEmpty() ? "-" : preset.name(),
                Component.translatable(com.otectus.arsnspells.icons.IconCatalog.label(preset.icon())));
    }
    @Override public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != parent.getMenu()) minecraft.setScreen(null);
        else inspection.setParagraphs(parent.inspectionLines());
    }
    @Override public void onClose() { parent.returnFromPicker(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public Component getNarrationMessage() {
        var narration = title.copy();
        for (Component line : parent.inspectionLines()) narration.append(". ").append(line);
        return narration.append(". ").append(status);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanel(graphics, mouseX, mouseY, partialTick, left, top, 300, 222);
        VanillaGui.centered(graphics, font, title, left + 150, top + 8, VanillaGui.TEXT);
        graphics.drawString(font, VanillaGui.ellipsize(font, status.getString(), 284), left + 8, top + 151, VanillaGui.TEXT, false);
        VanillaGui.wrapped(graphics, font, Component.translatable("ars_n_spells.spell_loom.preset.local"),
            left + 8, top + 166, 284, VanillaGui.MUTED);
        if (mouseX >= left + 8 && mouseX < left + 292 && mouseY >= top + 150 && mouseY < top + 162)
            setTooltipForNextRenderPass(font.split(status, 280));
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
