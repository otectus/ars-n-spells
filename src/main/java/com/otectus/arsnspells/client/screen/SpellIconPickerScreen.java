package com.otectus.arsnspells.client.screen;

import com.otectus.arsnspells.client.icons.SpellIconRegistry;
import com.otectus.arsnspells.icons.IconCatalog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/** Searchable cosmetic library. Vanilla buttons provide focus, keyboard activation and narration. */
public final class SpellIconPickerScreen extends VanillaPanelScreen {
    private static final int COLS = 10, ROWS = 4, PAGE_SIZE = COLS * ROWS;
    private static final List<String> FAMILIES = List.of("all", "spell", "school", "element", "resource", "status", "compat", "ui", "ritual", "carrier");
    private static final List<String> STATES = List.of("normal", "high_contrast", "monochrome");
    private final Screen parent;
    private final BiConsumer<String, String> accept;
    private String selected, background;
    private String query = "";
    private int page, family, state;
    private EditBox search;
    private List<String> filtered = List.of();
    private int left, top;

    public SpellIconPickerScreen(Screen parent, String selected, String background, BiConsumer<String,String> accept) {
        super(Component.translatable("ars_n_spells.icon_picker.title"));
        this.parent = parent;
        this.selected = IconCatalog.canonical(selected);
        if (this.selected == null) this.selected = IconCatalog.DEFAULT;
        this.background = IconCatalog.background(background);
        this.accept = accept;
    }

    @Override
    protected void init() {
        left = (width - 300) / 2; top = (height - 218) / 2;
        rebuild();
    }

    private void rebuild() {
        var previousFocus = getFocused();
        int cursor = search == null ? query.length() : search.getCursorPosition();
        clearWidgets();
        search = new EditBox(font, left + 8, top + 22, 184, 18,
            Component.translatable("ars_n_spells.icon_picker.search"));
        search.setMaxLength(64); search.setHint(Component.translatable("ars_n_spells.icon_picker.search"));
        search.setValue(query);
        search.setResponder(value -> { query = value; page = 0; rebuild(); setFocused(search); search.setFocused(true); });
        search.setCursorPosition(Math.min(cursor, query.length()));
        search.setHighlightPos(search.getCursorPosition());
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.icon_picker.family." + FAMILIES.get(family)), b -> {
            family = (family + 1) % FAMILIES.size(); page = 0; rebuild();
        }).bounds(left + 198, top + 21, 94, 20).build());
        String needle = query.toLowerCase(Locale.ROOT).strip();
        filtered = IconCatalog.IDS.stream().filter(id -> family == 0 || id.startsWith(FAMILIES.get(family) + "/"))
            .filter(id -> id.contains(needle) || Component.translatable(IconCatalog.label(id)).getString().toLowerCase(Locale.ROOT).contains(needle)).toList();
        page = Math.min(page, Math.max(0, (filtered.size() - 1) / PAGE_SIZE));
        int start = page * PAGE_SIZE;
        for (int i = start; i < Math.min(filtered.size(), start + PAGE_SIZE); i++) {
            String id = filtered.get(i); int cell = i - start;
            addRenderableWidget(new IconButton(left + 8 + (cell % COLS) * 28,
                top + 47 + (cell / COLS) * 26, id));
        }
        addRenderableWidget(Button.builder(Component.translatable("icon.ars_n_spells.background." + background), b -> {
            int index = IconCatalog.BACKGROUNDS.indexOf(background);
            background = IconCatalog.BACKGROUNDS.get((index + 1) % IconCatalog.BACKGROUNDS.size());
            rebuild();
        }).bounds(left + 8, top + 155, 150, 20).tooltip(Tooltip.create(Component.translatable("ars_n_spells.icon_picker.cosmetic"))).build());
        addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.icon_picker.state." + STATES.get(state)), b -> {
            state = (state + 1) % STATES.size(); rebuild();
        }).bounds(left + 164, top + 155, 128, 20).tooltip(Tooltip.create(Component.translatable("ars_n_spells.icon_picker.state_preview"))).build());
        Button previous = addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> { page--; rebuild(); })
            .bounds(left + 8, top + 190, 64, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.icon_picker.next"), b -> { page++; rebuild(); })
            .bounds(left + 78, top + 190, 64, 20).build());
        next.active = start + PAGE_SIZE < filtered.size();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 198, top + 190, 94, 20).build());
        if (previousFocus instanceof Button old) {
            children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.active && button.getX() == old.getX() && button.getY() == old.getY())
                .findFirst().ifPresent(this::setFocused);
        }
    }

    @Override
    public void tick() {
        search.tick();
        if (parent instanceof SpellLoomScreen loom && (minecraft.player == null || minecraft.player.containerMenu != loom.getMenu())) minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        accept.accept(selected, background);
        if (parent instanceof SpellLoomScreen loom) loom.returnFromPicker();
        else minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanel(graphics, mouseX, mouseY, partialTick, left, top, 300, 218);
        graphics.drawString(font, title, left + 8, top + 7, VanillaGui.TEXT, false);
        graphics.drawString(font, Component.translatable("ars_n_spells.icon_picker.page", page + 1,
            Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE), filtered.size()), left + 8, top + 179, VanillaGui.MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (filtered.isEmpty()) VanillaGui.centered(graphics, font, Component.translatable("ars_n_spells.icon_picker.no_results"), left + 150, top + 92, VanillaGui.TEXT);
    }

    private final class IconButton extends Button {
        private final String id;
        IconButton(int x, int y, String id) {
            super(x, y, 24, 24, Component.translatable(IconCatalog.label(id)), b -> { selected = id; }, DEFAULT_NARRATION);
            this.id = id;
            setTooltip(Tooltip.create(Component.translatable(IconCatalog.label(id))));
        }
        @Override
        public void renderString(GuiGraphics graphics, net.minecraft.client.gui.Font font, int color) {}
        @Override
        public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            super.updateWidgetNarration(output);
            if (id.equals(selected)) output.add(net.minecraft.client.gui.narration.NarratedElementType.HINT,
                Component.translatable("ars_n_spells.icon_picker.selected"));
        }
        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean chosen = id.equals(selected);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            if (chosen) {
                VanillaGui.inset(graphics, getX() + 1, getY() + 1, width - 2, height - 2);
                graphics.renderOutline(getX(), getY(), width, height, 0xFFFFFFFF);
            }
            var texture = SpellIconRegistry.INSTANCE.resolve(id, background, STATES.get(state));
            graphics.blit(texture, getX() + 4, getY() + 4, 0, 0, 16, 16, 16, 16);
            if (chosen) { // Shape distinction as well as selection color.
                graphics.fill(getX() + 2, getY() + 2, getX() + 5, getY() + 3, 0xFFFFFFFF);
                graphics.fill(getX() + 2, getY() + 2, getX() + 3, getY() + 5, 0xFFFFFFFF);
            }
        }
    }
}
