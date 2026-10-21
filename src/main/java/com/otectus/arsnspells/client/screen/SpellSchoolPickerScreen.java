package com.otectus.arsnspells.client.screen;

import com.otectus.arsnspells.spell.SpellSchoolChoice;
import com.otectus.arsnspells.spell.SpellSchoolNames;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Spell Loom's mechanical school selector. Separate from the cosmetic icon and background
 * library on purpose: a school changes what the spell scales with and trains, a background only
 * changes how it looks. Lists Automatic plus every school {@link SpellSchoolChoice} reports as
 * installed here; the server re-checks whatever is chosen.
 */
public final class SpellSchoolPickerScreen extends VanillaPanelScreen {
    private static final int WIDTH = 300, HEIGHT = 218, COLS = 2, ROWS = 4, PAGE_SIZE = COLS * ROWS;
    private final SpellLoomScreen parent;
    private final Consumer<String> accept;
    private final Component automaticLabel;
    private final List<String> schools = SpellSchoolChoice.supportedSchools();
    private String selected;
    private int page, left, top;

    /**
     * @param selected       the current selection, {@code ""} for Automatic
     * @param automaticLabel what Automatic resolves to for the spell in the source slot
     */
    SpellSchoolPickerScreen(SpellLoomScreen parent, String selected, Component automaticLabel, Consumer<String> accept) {
        super(Component.translatable("ars_n_spells.school_picker.title"));
        this.parent = parent;
        this.selected = SpellSchoolChoice.normalize(selected);
        this.automaticLabel = automaticLabel;
        this.accept = accept;
        int index = schools.indexOf(this.selected);
        this.page = index < 0 ? 0 : index / PAGE_SIZE;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        boolean allowed = SpellSchoolChoice.overridesAllowed();
        addRenderableWidget(new SchoolButton(left + 8, top + 52, 284, "", automaticLabel, true));
        int start = page * PAGE_SIZE;
        for (int i = start; i < Math.min(schools.size(), start + PAGE_SIZE); i++) {
            int cell = i - start;
            String key = schools.get(i);
            addRenderableWidget(new SchoolButton(left + 8 + (cell % COLS) * 144, top + 76 + (cell / COLS) * 22, 140,
                key, SpellSchoolNames.name(key), allowed));
        }
        Button previous = addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> { page--; rebuild(); })
            .bounds(left + 8, top + 190, 64, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.icon_picker.next"), b -> { page++; rebuild(); })
            .bounds(left + 78, top + 190, 64, 20).build());
        next.active = start + PAGE_SIZE < schools.size();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(left + 198, top + 190, 94, 20).build());
    }

    @Override
    public void tick() {
        if (minecraft.player == null || minecraft.player.containerMenu != parent.getMenu()) minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        accept.accept(selected);
        parent.returnFromPicker();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanel(graphics, mouseX, mouseY, partialTick, left, top, WIDTH, HEIGHT);
        graphics.drawString(font, title, left + 8, top + 7, VanillaGui.TEXT, false);
        VanillaGui.wrapped(graphics, font, Component.translatable("ars_n_spells.school_picker.help"),
            left + 8, top + 19, 284, VanillaGui.MUTED);
        Component footer = !SpellSchoolChoice.overridesAllowed()
            ? Component.translatable("ars_n_spells.school_picker.disabled")
            : schools.isEmpty() ? Component.translatable("ars_n_spells.school_picker.none_installed")
            : Component.translatable("ars_n_spells.school_picker.page", page + 1,
                Math.max(1, (schools.size() + PAGE_SIZE - 1) / PAGE_SIZE), schools.size());
        graphics.drawString(font, VanillaGui.ellipsize(font, footer.getString(), 284), left + 8, top + 167,
            VanillaGui.MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** One choice. The selected one is inset, outlined and announced, like the icon library's cells. */
    private final class SchoolButton extends Button {
        private final String key;

        SchoolButton(int x, int y, int width, String key, Component label, boolean enabled) {
            super(x, y, width, 20, label, b -> selected = key, DEFAULT_NARRATION);
            this.key = key;
            this.active = enabled;
            setTooltip(Tooltip.create(key.isEmpty()
                ? Component.translatable("ars_n_spells.school_picker.automatic_help")
                : Component.translatable("ars_n_spells.school_picker.school_help", label, key)));
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            if (key.equals(selected)) {
                graphics.renderOutline(getX(), getY(), width, height, 0xFFFFFFFF);
                // Shape as well as colour marks the selection.
                graphics.fill(getX() + 2, getY() + 2, getX() + 5, getY() + 3, 0xFFFFFFFF);
                graphics.fill(getX() + 2, getY() + 2, getX() + 3, getY() + 5, 0xFFFFFFFF);
            }
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            super.updateWidgetNarration(output);
            if (key.equals(selected)) {
                output.add(NarratedElementType.HINT, Component.translatable("ars_n_spells.icon_picker.selected"));
            }
        }
    }
}
