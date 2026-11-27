package com.otectus.arsnspells.client.screen;

import com.otectus.arsnspells.client.ClientSettings;
import com.otectus.arsnspells.config.AnsSettings;
import com.otectus.arsnspells.config.InscribedCooldownPresets;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.SettingsApplyPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The in-game Ars 'n' Spells settings screen.
 *
 * <p><b>3.4.1 review CONFIG-01.</b> The screen edits a <em>draft</em> of the world settings the
 * server last sent ({@link ClientSettings}); nothing changes until Apply sends the draft to the
 * server, which checks who is asking and applies it through the same service as {@code /ans mode
 * set}. Cancel and Escape discard the draft; Reset only resets the draft. The owner of a single-player
 * world may edit with cheats off; on a server or an opened-to-LAN world, operators may. Everyone else
 * sees the settings read-only, with the reason. The mode row shows both the mode asked for and the
 * mode in effect, which differ when Iron's Spellbooks is not installed or unification is off.
 */
public class ConfigScreenFactory {

    public static Screen createConfigScreen(Screen parent) {
        return new ArsNSpellsConfigScreen(parent);
    }

    public static class ArsNSpellsConfigScreen extends Screen {
        private static final int ROW_STRIDE = 34;
        private static final int ROW_WIDTH = 340;
        private static final int PANEL_PAD = 8;
        private static final int BTN_H = 20;
        private static final int BTN_W_BOOL = 44;
        private static final int BTN_W_CYCLE = 110;
        private static final int FOOTER_H = 84;
        /** Defaults the Reset button restores in the draft; must match AnsConfig's .define defaults. */
        private static final Map<String, String> TOGGLE_DEFAULTS = Map.of(
            "enable_mana_unification", "true", "enable_resonance_system", "true", "enable_cooldown_system", "false",
            "enable_progression_system", "true", "enable_affinity_system", "true", "respect_armor_bonuses", "true",
            "respect_enchantments", "true", "enable_source_jar_synergy", "true", "debug_mode", "false");

        private final Screen parent;
        private final List<Button> optionButtons = new ArrayList<>();
        private final Map<String, String> draft = new LinkedHashMap<>();
        private AnsSettings.Snapshot settings;
        private int scrollOffset;
        private boolean draggingScrollbar;

        protected ArsNSpellsConfigScreen(Screen parent) {
            super(Component.translatable("ars_n_spells.settings.title"));
            this.parent = parent;
            this.settings = ClientSettings.current();
            if (settings != null) draft.putAll(settings.values());
        }

        private boolean connected() {
            return minecraft != null && minecraft.level != null && settings != null;
        }

        private boolean canEdit() {
            return connected() && settings.canEdit();
        }

        /** The server sent new settings while the screen is open: keep the player's edits, refresh the rest. */
        public void onSettings(AnsSettings.Snapshot next) {
            Map<String, String> before = settings == null ? Map.of() : settings.values();
            settings = next;
            for (Map.Entry<String, String> entry : next.values().entrySet()) {
                String edited = draft.get(entry.getKey());
                if (edited == null || edited.equals(before.get(entry.getKey()))) draft.put(entry.getKey(), entry.getValue());
            }
            if (minecraft != null) init(minecraft, width, height);
        }

        @Override
        protected void init() {
            super.init();
            clearWidgets();
            optionButtons.clear();
            this.addRenderableWidget(Button.builder(Component.translatable(canEdit() ? "ars_n_spells.settings.apply" : "gui.done"),
                    button -> {
                        if (canEdit()) apply();
                        minecraft.setScreen(parent);
                    })
                .bounds(this.width / 2 + 2, this.height - 28, 148, 20).build());
            Button cancel = Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(this.width / 2 - 150, this.height - 28, 148, 20).build();
            this.addRenderableWidget(cancel);
            Button reset = Button.builder(Component.translatable("ars_n_spells.settings.reset_toggles"), button -> {
                    draft.putAll(TOGGLE_DEFAULTS);
                    rebuildOptionButtons();
                })
                .bounds(this.width / 2 - 150, this.height - 52, 98, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("ars_n_spells.settings.reset_toggles.tooltip")))
                .build();
            reset.active = canEdit();
            this.addRenderableWidget(reset);
            this.addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.icon_picker.library"),
                    b -> minecraft.setScreen(new SpellIconPickerScreen(this, "school/generic", "none", (icon, frame) -> {})))
                .bounds(this.width / 2 - 48, this.height - 52, 98, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.compatibility.title"),
                    b -> minecraft.setScreen(new CompatibilityScreen(this)))
                .bounds(this.width / 2 + 52, this.height - 52, 98, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.browser.personal"),
                    b -> com.otectus.arsnspells.client.glyph.BrowserPlatform.push(new com.otectus.arsnspells.client.glyph.PersonalDisplayScreen()))
                .bounds(this.width / 2 - 150, this.height - 74, 300, 20).build());
            rebuildOptionButtons();
        }

        /** Send only what the draft changed; the server's reply carries the outcome. */
        private void apply() {
            Map<String, String> changes = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : draft.entrySet())
                if (!entry.getValue().equals(settings.values().get(entry.getKey()))) changes.put(entry.getKey(), entry.getValue());
            if (!changes.isEmpty()) PacketHandler.sendToServer(new SettingsApplyPacket(UUID.randomUUID(), changes));
        }

        // ---- Rows ------------------------------------------------------------------------------

        private int rowX() { return this.width / 2 - rowWidth() / 2; }

        private int rowWidth() { return Math.min(ROW_WIDTH, this.width - 32); }

        private int listTop() { return 66; }

        private int visibleRowCount() { return Math.max(1, (this.height - FOOTER_H - listTop()) / ROW_STRIDE); }

        private static boolean isCycle(AnsSettings.Setting setting) {
            return setting.kind() != AnsSettings.Kind.BOOLEAN;
        }

        private int[] buttonRect(AnsSettings.Setting setting, int rowY) {
            int w = isCycle(setting) ? BTN_W_CYCLE : BTN_W_BOOL;
            return new int[]{rowX() + rowWidth() - w - 6, rowY + (ROW_STRIDE - 2 - BTN_H) / 2, w, BTN_H};
        }

        private void rebuildOptionButtons() {
            if (optionButtons.contains(getFocused())) setFocused(null);
            for (Button button : optionButtons) removeWidget(button);
            optionButtons.clear();
            List<AnsSettings.Setting> rows = AnsSettings.SETTINGS;
            scrollOffset = Math.max(0, Math.min(scrollOffset, rows.size() - visibleRowCount()));
            for (int i = scrollOffset; i < Math.min(rows.size(), scrollOffset + visibleRowCount()); i++) {
                AnsSettings.Setting setting = rows.get(i);
                int[] rect = buttonRect(setting, listTop() + (i - scrollOffset) * ROW_STRIDE);
                Button control = Button.builder(valueLabel(setting), button -> {
                        if (!canEdit()) return;
                        step(setting);
                        button.setMessage(valueLabel(setting));
                    }).bounds(rect[0], rect[1], rect[2], rect[3])
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("ars_n_spells.settings." + setting.key())
                        .append("\n").append(Component.translatable("ars_n_spells.settings." + setting.key() + ".desc"))))
                    .createNarration(supplier -> Component.translatable("ars_n_spells.settings." + setting.key()).append(". ").append(supplier.get()))
                    .build();
                control.active = canEdit();
                optionButtons.add(addRenderableWidget(control));
            }
        }

        /** Advance one row in the draft: flip a toggle, cycle the mode or the cooldown preset. */
        private void step(AnsSettings.Setting setting) {
            String value = draft.getOrDefault(setting.key(), "");
            switch (setting.kind()) {
                case BOOLEAN -> draft.put(setting.key(), String.valueOf(!Boolean.parseBoolean(value)));
                case MODE -> {
                    ManaUnificationMode[] modes = ManaUnificationMode.values();
                    int index = 0;
                    for (int i = 0; i < modes.length; i++) if (modes[i].getConfigName().equalsIgnoreCase(value)) index = i;
                    draft.put(setting.key(), modes[(index + 1) % modes.length].getConfigName());
                }
                case COOLDOWN_TICKS -> {
                    int ticks;
                    try {
                        ticks = Integer.parseInt(value);
                    } catch (NumberFormatException invalid) {
                        ticks = 0;
                    }
                    draft.put(setting.key(), String.valueOf(InscribedCooldownPresets.next(ticks)));
                }
            }
        }

        private Component valueLabel(AnsSettings.Setting setting) {
            if (!connected()) return Component.literal("-");
            String value = draft.getOrDefault(setting.key(), "");
            return switch (setting.kind()) {
                case BOOLEAN -> Component.translatable(Boolean.parseBoolean(value) ? "options.on" : "options.off");
                case MODE -> Component.translatable("ars_n_spells.settings.mode." + value);
                case COOLDOWN_TICKS -> {
                    try {
                        yield Component.literal(InscribedCooldownPresets.describe(Integer.parseInt(value)));
                    } catch (NumberFormatException invalid) {
                        yield Component.literal(value);
                    }
                }
            };
        }

        /** The second line of a row: its description, or for the mode the mode in effect and why it differs. */
        private Component description(AnsSettings.Setting setting) {
            if (setting.kind() == AnsSettings.Kind.MODE && connected()) {
                Component effective = Component.translatable("ars_n_spells.settings.mode." + settings.effectiveMode());
                if (!settings.ironsPresent()) return Component.translatable("ars_n_spells.settings.mode.effective_no_irons", effective);
                return Component.translatable("ars_n_spells.settings.mode.effective", effective);
            }
            return Component.translatable("ars_n_spells.settings." + setting.key() + ".desc");
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, this.width, this.height, 0xFF202020);
            VanillaGui.panel(graphics, rowX() - PANEL_PAD, 8, rowWidth() + PANEL_PAD * 2, this.height - FOOTER_H - 8);
            VanillaGui.centered(graphics, font, title, width / 2, 16, VanillaGui.TEXT);
            VanillaGui.centered(graphics, font, Component.translatable("ars_n_spells.settings.scope"), width / 2, 30, VanillaGui.MUTED);
            Component access = !connected() ? Component.translatable("ars_n_spells.settings.not_connected")
                : canEdit() ? Component.translatable("ars_n_spells.settings.editable")
                : Component.translatable("ars_n_spells.settings.read_only");
            VanillaGui.centered(graphics, font, access, width / 2, 44, VanillaGui.TEXT);
            List<AnsSettings.Setting> rows = AnsSettings.SETTINGS;
            for (int i = scrollOffset; i < Math.min(rows.size(), scrollOffset + visibleRowCount()); i++) {
                AnsSettings.Setting setting = rows.get(i);
                int y = listTop() + (i - scrollOffset) * ROW_STRIDE;
                int textWidth = buttonRect(setting, y)[0] - rowX() - 14;
                String name = Component.translatable("ars_n_spells.settings." + setting.key()).getString();
                String description = description(setting).getString();
                graphics.drawString(font, VanillaGui.ellipsize(font, name, textWidth), rowX() + 4, y + 5, VanillaGui.TEXT, false);
                graphics.drawString(font, VanillaGui.ellipsize(font, description, textWidth), rowX() + 4, y + 17, VanillaGui.MUTED, false);
                if (mouseX >= rowX() && mouseX < rowX() + textWidth && mouseY >= y && mouseY < y + ROW_STRIDE)
                    setTooltipForNextRenderPass(font.split(Component.literal(name + "\n" + description), 260));
            }
            int visible = visibleRowCount();
            if (rows.size() > visible) {
                int x = rowX() + rowWidth() - 3;
                int track = visible * ROW_STRIDE - 2;
                int thumb = Math.max(12, track * visible / rows.size());
                int y = listTop() + (track - thumb) * scrollOffset / (rows.size() - visible);
                graphics.fill(x, listTop(), x + 6, listTop() + track, 0xFF000000);
                graphics.fill(x, y, x + 6, y + thumb, 0xFF808080);
                graphics.fill(x, y, x + 5, y + thumb - 1, VanillaGui.PANEL);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        private void scrollRows(int delta) {
            scrollOffset = Math.max(0, Math.min(Math.max(0, AnsSettings.SETTINGS.size() - visibleRowCount()), scrollOffset + delta));
            rebuildOptionButtons();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int rows = AnsSettings.SETTINGS.size();
            draggingScrollbar = button == 0 && rows > visibleRowCount()
                && mouseX >= rowX() + rowWidth() - 3 && mouseX < rowX() + rowWidth() + 3
                && mouseY >= listTop() && mouseY < listTop() + visibleRowCount() * ROW_STRIDE - 2;
            if (draggingScrollbar) { dragScrollbar(mouseY); return true; }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private void dragScrollbar(double y) {
            int rows = AnsSettings.SETTINGS.size();
            int track = visibleRowCount() * ROW_STRIDE - 2;
            int thumb = Math.max(12, track * visibleRowCount() / rows);
            int target = (int) Math.round((y - listTop() - thumb / 2.0) * (rows - visibleRowCount()) / Math.max(1, track - thumb));
            scrollRows(target - scrollOffset);
        }

        @Override
        public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            if (draggingScrollbar && button == 0) { dragScrollbar(y); return true; }
            return super.mouseDragged(x, y, button, dx, dy);
        }

        @Override
        public boolean mouseReleased(double x, double y, int button) {
            draggingScrollbar = false;
            return super.mouseReleased(x, y, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (mouseX < rowX() - PANEL_PAD || mouseX >= rowX() + rowWidth() + PANEL_PAD
                || mouseY < listTop() || mouseY >= height - FOOTER_H) return false;
            scrollRows(delta > 0 ? -1 : delta < 0 ? 1 : 0);
            return true;
        }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == 266 || key == 267) {
                scrollRows(key == 266 ? -visibleRowCount() : visibleRowCount());
                if (!optionButtons.isEmpty()) setFocused(optionButtons.get(0));
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }

        /** Escape and Cancel discard the draft: nothing was sent, nothing changes. */
        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
    }
}
