package com.otectus.arsnspells.client.screen;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple in-game configuration screen for Ars 'n' Spells.
 * Provides access to key configuration options without requiring manual file editing.
 */
public class ConfigScreenFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigScreenFactory.class);

    /**
     * Create the config screen
     */
    public static Screen createConfigScreen(Screen parent) {
        return new ArsNSpellsConfigScreen(parent);
    }

    /**
     * Main configuration screen
     */
    public static class ArsNSpellsConfigScreen extends Screen {
        private static final int ROW_STRIDE = 34;
        private static final int ROW_WIDTH = 340;
        private static final int PANEL_PAD = 8;
        private static final int BTN_H = 20;
        private static final int BTN_W_BOOL = 44;
        private static final int BTN_W_CYCLE = 110;
        private static final int FOOTER_H = 64;
        private final List<Button> optionButtons = new ArrayList<>();
        private boolean draggingScrollbar;

        private final Screen parent;
        private final List<ConfigOption> options = new ArrayList<>();
        private int scrollOffset = 0;
        // ANS 2.0.1: true only in singleplayer (integrated server). Gates the new
        // Mana Mode cycle row and the Save/Reset buttons — SERVER-config writes from a
        // client mirror are no-ops on a dedicated server.
        private boolean canMutate = false;

        protected ArsNSpellsConfigScreen(Screen parent) {
            super(Component.literal("Ars 'n' Spells Configuration"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            super.init();
            clearWidgets();
            optionButtons.clear();

            // Clear existing options
            options.clear();

            // Add configuration options
            addMasterToggles();
            addManaSettings();
            addSystemSettings();

            // ANS-HIGH-016 part 2: gate mutation buttons on singleplayer.
            // AnsConfig is now a SERVER-type config (registered server-side in ArsNSpells.java).
            // On a dedicated server, AnsConfig.<KEY>.set(...) on the CLIENT side mutates only
            // the client's mirror — it never propagates to the server, so toggles in this
            // screen would be silent no-ops. Disable Save/Reset when not in singleplayer so
            // the user is not misled. Operators on dedicated servers should edit the
            // server-side toml directly or use the /ans command.
            canMutate = minecraft != null && minecraft.hasSingleplayerServer();

            // Add Done button (always present; in multiplayer it's read-only "Close")
            this.addRenderableWidget(Button.builder(
                Component.literal(canMutate ? "Done" : "Close"),
                button -> {
                    if (canMutate) {
                        saveConfig();
                    }
                    minecraft.setScreen(parent);
                })
                .bounds(this.width / 2 + 2, this.height - 28, 148, 20)
                .build()
            );

            // Add Reset Toggles button — disabled in multiplayer
            // ANS-OPT-018: renamed from "Reset to Defaults" because resetToDefaults()
            // only resets the 9 boolean toggles visible in the UI, not the 90+ other
            // config keys. "Reset Toggles" matches what the button actually does.
            Button resetButton = Button.builder(
                Component.literal("Reset Toggles"),
                button -> resetToDefaults())
                .bounds(this.width / 2 - 150, this.height - 52, 148, 20)
                .build();
            resetButton.active = canMutate;
            this.addRenderableWidget(resetButton);
            this.addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.icon_picker.library"),
                b -> minecraft.setScreen(new SpellIconPickerScreen(this, "school/generic", "none", (icon, frame) -> {})))
                .bounds(this.width / 2 + 2, this.height - 52, 148, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.compatibility.title"),
                b -> minecraft.setScreen(new CompatibilityScreen(this)))
                .bounds(this.width / 2 - 150, this.height - 28, 148, 20).build());
            rebuildOptionButtons();
        }

        private void addMasterToggles() {
            options.add(new ConfigOption(
                "Mana Unification",
                "Enable unified mana system",
                () -> AnsConfig.ENABLE_MANA_UNIFICATION.get(),
                value -> AnsConfig.ENABLE_MANA_UNIFICATION.set(value)
            ));

            options.add(new ConfigOption(
                "Resonance System",
                "Enable full-mana bonuses",
                () -> AnsConfig.ENABLE_RESONANCE_SYSTEM.get(),
                value -> AnsConfig.ENABLE_RESONANCE_SYSTEM.set(value)
            ));

            options.add(new ConfigOption(
                "Cooldown System",
                "Enable unified cooldowns",
                () -> AnsConfig.ENABLE_COOLDOWN_SYSTEM.get(),
                value -> AnsConfig.ENABLE_COOLDOWN_SYSTEM.set(value)
            ));

            options.add(new ConfigOption(
                "Progression System",
                "Enable cross-mod progression",
                () -> AnsConfig.ENABLE_PROGRESSION_SYSTEM.get(),
                value -> AnsConfig.ENABLE_PROGRESSION_SYSTEM.set(value)
            ));

            options.add(new ConfigOption(
                "Affinity System",
                "Enable spell affinity tracking",
                () -> AnsConfig.ENABLE_AFFINITY_SYSTEM.get(),
                value -> AnsConfig.ENABLE_AFFINITY_SYSTEM.set(value)
            ));
        }

        private void addManaSettings() {
            // ANS 2.0.1: a real cycling row (was a dead stub: getter () -> true, setter
            // value -> {}). Click advances mana_unification_mode; saveConfig() applies it live.
            options.add(new ConfigOption(
                "Mana Mode",
                "Click to cycle the mana unification mode",
                () -> AnsConfig.MANA_UNIFICATION_MODE.get(),
                this::cycleManaMode
            ));

            options.add(new ConfigOption(
                "Respect Armor Bonuses",
                "Include armor in mana calculations",
                () -> AnsConfig.respectArmorBonuses.get(),
                value -> AnsConfig.respectArmorBonuses.set(value)
            ));

            options.add(new ConfigOption(
                "Respect Enchantments",
                "Include enchantments in calculations",
                () -> AnsConfig.respectEnchantments.get(),
                value -> AnsConfig.respectEnchantments.set(value)
            ));
        }

        /** Advance mana_unification_mode to the next value in enum order (wraps). */
        private void cycleManaMode() {
            String current = AnsConfig.MANA_UNIFICATION_MODE.get();
            ManaUnificationMode[] modes = ManaUnificationMode.values();
            int idx = 0;
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].getConfigName().equalsIgnoreCase(current)) {
                    idx = i;
                    break;
                }
            }
            String next = modes[(idx + 1) % modes.length].getConfigName();
            AnsConfig.MANA_UNIFICATION_MODE.set(next);
        }

        private void addSystemSettings() {
            options.add(new ConfigOption(
                "Source Jar Synergy",
                "Passive mana regen near Ars Nouveau Source Jars",
                () -> AnsConfig.ENABLE_SOURCE_JAR_SYNERGY.get(),
                value -> AnsConfig.ENABLE_SOURCE_JAR_SYNERGY.set(value)
            ));

            // 3.3.5: the native cooldown an Ars spell bound into an Iron's spellbook starts after
            // a successful wheel cast. Cycles through common values; any 0..12000 tick value can
            // still be set in the server TOML, and an off-preset value advances to the next preset.
            options.add(new ConfigOption(
                "Inscribed Ars Cooldown",
                "Iron's cooldown after an Ars spell cast from an Iron's spellbook (20 ticks = 1s)",
                () -> com.otectus.arsnspells.config.InscribedCooldownPresets.describe(
                    AnsConfig.INSCRIBED_ARS_DEFAULT_COOLDOWN_TICKS.get()),
                this::cycleInscribedCooldown
            ));

            options.add(new ConfigOption(
                "Debug Mode",
                "Enable debug logging",
                () -> AnsConfig.DEBUG_MODE.get(),
                value -> AnsConfig.DEBUG_MODE.set(value)
            ));
        }

        private void cycleInscribedCooldown() {
            AnsConfig.INSCRIBED_ARS_DEFAULT_COOLDOWN_TICKS.set(com.otectus.arsnspells.config.InscribedCooldownPresets.next(
                AnsConfig.INSCRIBED_ARS_DEFAULT_COOLDOWN_TICKS.get()));
        }

        // ---- Shared geometry: single source of truth for render AND click ----

        private int rowX() {
            return this.width / 2 - rowWidth() / 2;
        }

        private int rowWidth() { return Math.min(ROW_WIDTH, this.width - 32); }

        /** First row y; the read-only note reserves an extra strip in multiplayer. */
        private int listTop() {
            return canMutate ? 46 : 66;
        }

        private int visibleRowCount() {
            return Math.max(1, (this.height - FOOTER_H - listTop()) / ROW_STRIDE);
        }

        /** Screen-space rect {x, y, w, h} of a row's control. */
        private int[] buttonRect(ConfigOption option, int rowY) {
            int w = option.isCycle() ? BTN_W_CYCLE : BTN_W_BOOL;
            int x = rowX() + rowWidth() - w - 6;
            int y = rowY + (ROW_STRIDE - 2 - BTN_H) / 2;
            return new int[]{x, y, w, BTN_H};
        }

        private void rebuildOptionButtons() {
            // A control scrolled out of view must no longer receive Space/Enter.
            if (optionButtons.contains(getFocused())) setFocused(null);
            for (Button button : optionButtons) removeWidget(button);
            optionButtons.clear();
            scrollOffset = Math.max(0, Math.min(scrollOffset, options.size() - visibleRowCount()));
            for (int i = scrollOffset; i < Math.min(options.size(), scrollOffset + visibleRowCount()); i++) {
                ConfigOption option = options.get(i);
                int[] rect = buttonRect(option, listTop() + (i - scrollOffset) * ROW_STRIDE);
                Button control = Button.builder(optionLabel(option), button -> {
                    if (!canMutate) return;
                    if (option.isCycle()) option.onCycle.run();
                    else option.toggle();
                    button.setMessage(optionLabel(option));
                }).bounds(rect[0], rect[1], rect[2], rect[3])
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(option.name + ": " + option.description)))
                    .createNarration(supplier -> Component.literal(option.name + ". ").append(supplier.get()))
                    .build();
                control.active = canMutate;
                optionButtons.add(addRenderableWidget(control));
            }
        }

        private Component optionLabel(ConfigOption option) {
            // SERVER config is unavailable on the title screen. Do not present
            // defaults or a previous world's values as a connected server's state.
            if (minecraft == null || minecraft.level == null) return Component.literal("-");
            return option.isCycle() ? Component.literal(option.displaySupplier.get())
                : Component.translatable(option.getValue() ? "options.on" : "options.off");
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Preserve the owned backdrop: third-party blur must not blur settings or labels.
            graphics.fill(0, 0, this.width, this.height, 0xFF202020);
            VanillaGui.panel(graphics, rowX() - PANEL_PAD, 8, rowWidth() + PANEL_PAD * 2, this.height - FOOTER_H - 8);
            VanillaGui.centered(graphics, font, title, width / 2, 16, VanillaGui.TEXT);
            VanillaGui.centered(graphics, font, Component.literal("Configure Ars 'n' Spells Integration"), width / 2, 28, VanillaGui.MUTED);
            if (!canMutate) {
                boolean connected = minecraft != null && minecraft.level != null;
                VanillaGui.centered(graphics, font, Component.literal(connected ? "Read-only: server-managed config." : "Join a world to view its settings."), width / 2, 42, VanillaGui.TEXT);
                VanillaGui.centered(graphics, font, Component.literal(connected ? "Edit the server TOML or use /ans commands." : "These settings are managed by the server."), width / 2, 52, VanillaGui.MUTED);
            }
            for (int i = scrollOffset; i < Math.min(options.size(), scrollOffset + visibleRowCount()); i++) {
                ConfigOption option = options.get(i);
                int y = listTop() + (i - scrollOffset) * ROW_STRIDE;
                int textWidth = buttonRect(option, y)[0] - rowX() - 14;
                graphics.drawString(font, VanillaGui.ellipsize(font, option.name, textWidth), rowX() + 4, y + 5, VanillaGui.TEXT, false);
                graphics.drawString(font, VanillaGui.ellipsize(font, option.description, textWidth), rowX() + 4, y + 17, VanillaGui.MUTED, false);
                if (mouseX >= rowX() && mouseX < rowX() + textWidth && mouseY >= y && mouseY < y + ROW_STRIDE)
                    setTooltipForNextRenderPass(font.split(Component.literal(option.name + "\n" + option.description), 260));
            }
            int visible = visibleRowCount();
            if (options.size() > visible) {
                int x = rowX() + rowWidth() - 3;
                int track = visible * ROW_STRIDE - 2;
                int thumb = Math.max(12, track * visible / options.size());
                int y = listTop() + (track - thumb) * scrollOffset / (options.size() - visible);
                graphics.fill(x, listTop(), x + 6, listTop() + track, 0xFF000000);
                graphics.fill(x, y, x + 6, y + thumb, 0xFF808080);
                graphics.fill(x, y, x + 5, y + thumb - 1, VanillaGui.PANEL);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        private void scrollRows(int delta) {
            scrollOffset = Math.max(0, Math.min(Math.max(0, options.size() - visibleRowCount()), scrollOffset + delta));
            rebuildOptionButtons();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            draggingScrollbar = button == 0 && options.size() > visibleRowCount()
                && mouseX >= rowX() + rowWidth() - 3 && mouseX < rowX() + rowWidth() + 3
                && mouseY >= listTop() && mouseY < listTop() + visibleRowCount() * ROW_STRIDE - 2;
            if (draggingScrollbar) { dragScrollbar(mouseY); return true; }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private void dragScrollbar(double y) {
            int track = visibleRowCount() * ROW_STRIDE - 2;
            int thumb = Math.max(12, track * visibleRowCount() / options.size());
            int target = (int) Math.round((y - listTop() - thumb / 2.0)
                * (options.size() - visibleRowCount()) / Math.max(1, track - thumb));
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

        private void saveConfig() {
            // ANS-HIGH-017 / audit D5: safeSave() only SCHEDULES an async write —
            // the real outcome lands in the log. The message below is worded
            // accordingly instead of claiming the file was written.
            AnsConfig.safeSave();

            // ANS 2.0.1: apply config changes (notably a Mana Mode cycle) live. This
            // screen runs on the render thread; BridgeManager.refreshMode() mutates
            // state the server thread reads, so marshal it onto the integrated server.
            if (minecraft != null && minecraft.getSingleplayerServer() != null) {
                minecraft.getSingleplayerServer().execute(BridgeManager::refreshMode);
            }

            // Show message to player
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(
                    Component.literal("Ars 'n' Spells config applied (saving to disk in background).")
                        .withStyle(ChatFormatting.GREEN)
                );
            }
        }

        private void resetToDefaults() {
            // Reset every boolean row to its TOML default (must match the
            // .define(...) defaults in AnsConfig's static block).
            AnsConfig.ENABLE_MANA_UNIFICATION.set(true);
            AnsConfig.ENABLE_RESONANCE_SYSTEM.set(true);
            // ANS 3.0.1: was wrongly reset to true; the TOML default is false.
            AnsConfig.ENABLE_COOLDOWN_SYSTEM.set(false);
            AnsConfig.ENABLE_PROGRESSION_SYSTEM.set(true);
            AnsConfig.ENABLE_AFFINITY_SYSTEM.set(true);
            AnsConfig.respectArmorBonuses.set(true);
            AnsConfig.respectEnchantments.set(true);
            AnsConfig.ENABLE_SOURCE_JAR_SYNERGY.set(true);
            AnsConfig.DEBUG_MODE.set(false);

            saveConfig();

            // Reinitialize screen
            this.init();
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
    }

    /**
     * Represents a single configuration option
     */
    private static class ConfigOption {
        final String name;
        final String description;
        final java.util.function.Supplier<Boolean> getter;
        final java.util.function.Consumer<Boolean> setter;
        // ANS 2.0.1: a "cycle" row (e.g. Mana Mode) renders a string value and advances
        // it on click instead of toggling ON/OFF. Both are null for boolean rows.
        final java.util.function.Supplier<String> displaySupplier;
        final Runnable onCycle;

        /** Boolean toggle row. */
        ConfigOption(String name, String description,
                    java.util.function.Supplier<Boolean> getter,
                    java.util.function.Consumer<Boolean> setter) {
            this.name = name;
            this.description = description;
            this.getter = getter;
            this.setter = setter;
            this.displaySupplier = null;
            this.onCycle = null;
        }

        /** Cycling row: {@code displaySupplier} provides the current value text, {@code onCycle} advances it. */
        ConfigOption(String name, String description,
                    java.util.function.Supplier<String> displaySupplier,
                    Runnable onCycle) {
            this.name = name;
            this.description = description;
            this.getter = null;
            this.setter = null;
            this.displaySupplier = displaySupplier;
            this.onCycle = onCycle;
        }

        boolean isCycle() {
            return onCycle != null;
        }

        boolean getValue() {
            return getter.get();
        }

        void toggle() {
            setter.accept(!getValue());
        }
    }
}
