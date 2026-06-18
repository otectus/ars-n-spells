package com.otectus.arsnspells.config;

import com.otectus.arsnspells.bridge.BridgeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple in-game configuration screen for Ars 'n' Spells, registered as the
 * mod's {@code IConfigScreenFactory} extension point (see
 * {@code ArsNSpellsClient}). Provides quick access to the master toggles, the
 * mana-unification mode, and a few system settings without editing the toml.
 *
 * <p>Ported from the Forge 1.20.1 screen. NeoForge 1.21.1 deltas: {@code Screen}
 * now takes {@code renderBackground(GuiGraphics, int, int, float)} and
 * {@code mouseScrolled(double, double, double, double)}.
 *
 * <p><b>Server-config caveat:</b> {@code AnsConfig} is a SERVER config, so the
 * mutate buttons are gated on singleplayer — writes from a client mirror on a
 * dedicated server are silent no-ops. Operators should edit the server toml or
 * use {@code /ans}.
 */
public class ConfigScreenFactory {

    /** Build the screen shown from the mod list. */
    public static Screen createConfigScreen(Screen parent) {
        return new ArsNSpellsConfigScreen(parent);
    }

    /** Main configuration screen. */
    public static class ArsNSpellsConfigScreen extends Screen {
        private final Screen parent;
        private final List<ConfigOption> options = new ArrayList<>();
        private int scrollOffset = 0;
        // True only in singleplayer (integrated server). Gates the Mana Mode cycle
        // row and the Save/Reset buttons — SERVER-config writes from a client mirror
        // are no-ops on a dedicated server.
        private boolean canMutate = false;

        protected ArsNSpellsConfigScreen(Screen parent) {
            super(Component.literal("Ars 'n' Spells Configuration"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            super.init();

            options.clear();
            addMasterToggles();
            addManaSettings();
            addSystemSettings();

            canMutate = minecraft != null && minecraft.hasSingleplayerServer();

            // Done/Close button (always present; read-only "Close" in multiplayer).
            this.addRenderableWidget(Button.builder(
                    Component.literal(canMutate ? "Done" : "Close"),
                    button -> {
                        if (canMutate) {
                            saveConfig();
                        }
                        if (minecraft != null) {
                            minecraft.setScreen(parent);
                        }
                    })
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());

            // Reset Toggles — only resets the visible boolean toggles; disabled in MP.
            Button resetButton = Button.builder(
                    Component.literal("Reset Toggles"),
                    button -> resetToDefaults())
                .bounds(this.width / 2 - 205, this.height - 28, 100, 20)
                .build();
            resetButton.active = canMutate;
            this.addRenderableWidget(resetButton);
        }

        private void addMasterToggles() {
            options.add(new ConfigOption("Mana Unification", "Enable unified mana system",
                AnsConfig.ENABLE_MANA_UNIFICATION::get, AnsConfig.ENABLE_MANA_UNIFICATION::set));
            options.add(new ConfigOption("Resonance System", "Enable full-mana bonuses",
                AnsConfig.ENABLE_RESONANCE_SYSTEM::get, AnsConfig.ENABLE_RESONANCE_SYSTEM::set));
            options.add(new ConfigOption("Cooldown System", "Enable unified cooldowns",
                AnsConfig.ENABLE_COOLDOWN_SYSTEM::get, AnsConfig.ENABLE_COOLDOWN_SYSTEM::set));
            options.add(new ConfigOption("Progression System", "Enable cross-mod progression",
                AnsConfig.ENABLE_PROGRESSION_SYSTEM::get, AnsConfig.ENABLE_PROGRESSION_SYSTEM::set));
            options.add(new ConfigOption("Affinity System", "Enable spell affinity tracking",
                AnsConfig.ENABLE_AFFINITY_SYSTEM::get, AnsConfig.ENABLE_AFFINITY_SYSTEM::set));
        }

        private void addManaSettings() {
            // Cycling row: click advances mana_unification_mode; saveConfig() applies it live.
            options.add(new ConfigOption("Mana Mode", "Click to cycle the mana unification mode",
                AnsConfig.MANA_UNIFICATION_MODE::get, this::cycleManaMode));
            options.add(new ConfigOption("Respect Armor Bonuses", "Include armor in mana calculations",
                AnsConfig.respectArmorBonuses::get, AnsConfig.respectArmorBonuses::set));
            options.add(new ConfigOption("Respect Enchantments", "Include enchantments in calculations",
                AnsConfig.respectEnchantments::get, AnsConfig.respectEnchantments::set));
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
            options.add(new ConfigOption("Debug Mode", "Enable debug logging",
                AnsConfig.DEBUG_MODE::get, AnsConfig.DEBUG_MODE::set));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics, mouseX, mouseY, partialTick);

            graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
            graphics.drawCenteredString(this.font,
                Component.literal("Configure Ars 'n' Spells Integration"),
                this.width / 2, 28, 0xAAAAAA);

            int y = 50;
            int x = this.width / 2 - 150;

            for (int i = scrollOffset; i < options.size() && y < this.height - 50; i++) {
                ConfigOption option = options.get(i);

                graphics.drawString(this.font, option.name, x, y, 0xFFFFFF);
                graphics.drawString(this.font, option.description, x, y + 12, 0x888888);

                String buttonText;
                int buttonColor;
                if (option.isCycle()) {
                    buttonText = option.displaySupplier.get();
                    buttonColor = canMutate ? 0xFFFFFF : 0x888888;
                } else {
                    buttonText = option.getValue() ? "ON" : "OFF";
                    buttonColor = option.getValue() ? 0x00FF00 : 0xFF0000;
                }
                graphics.drawString(this.font, buttonText, x + 280, y + 5, buttonColor);

                y += 35;
            }

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int y = 50;
            int x = this.width / 2 - 150;

            for (int i = scrollOffset; i < options.size() && y < this.height - 50; i++) {
                ConfigOption option = options.get(i);

                int rightEdge = option.isCycle() ? x + 380 : x + 310;
                if (mouseX >= x + 270 && mouseX <= rightEdge && mouseY >= y && mouseY <= y + 20) {
                    if (option.isCycle()) {
                        if (canMutate) {
                            option.onCycle.run();
                        }
                    } else {
                        option.toggle();
                    }
                    return true;
                }
                y += 35;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            scrollOffset = Math.max(0, Math.min(Math.max(0, options.size() - 10),
                scrollOffset - (int) scrollY));
            return true;
        }

        private void saveConfig() {
            boolean success = AnsConfig.safeSave();

            // Apply changes (notably a Mana Mode cycle) live. This screen runs on the
            // render thread; BridgeManager.refreshMode() mutates state the server thread
            // reads, so marshal it onto the integrated server.
            if (minecraft != null && minecraft.getSingleplayerServer() != null) {
                minecraft.getSingleplayerServer().execute(BridgeManager::refreshMode);
            }

            if (minecraft != null && minecraft.player != null) {
                if (success) {
                    minecraft.player.sendSystemMessage(
                        Component.literal("Ars 'n' Spells config saved!").withStyle(ChatFormatting.GREEN));
                } else {
                    minecraft.player.sendSystemMessage(
                        Component.literal("Failed to save config. Check logs for details.")
                            .withStyle(ChatFormatting.RED));
                }
            }
        }

        private void resetToDefaults() {
            AnsConfig.ENABLE_MANA_UNIFICATION.set(true);
            AnsConfig.ENABLE_RESONANCE_SYSTEM.set(true);
            AnsConfig.ENABLE_COOLDOWN_SYSTEM.set(true);
            AnsConfig.ENABLE_PROGRESSION_SYSTEM.set(true);
            AnsConfig.ENABLE_AFFINITY_SYSTEM.set(true);
            AnsConfig.respectArmorBonuses.set(true);
            AnsConfig.respectEnchantments.set(true);
            AnsConfig.DEBUG_MODE.set(false);

            saveConfig();
            this.init();
        }

        @Override
        public void onClose() {
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
        }
    }

    /** A single configuration row — either a boolean toggle or a cycling value. */
    private static class ConfigOption {
        final String name;
        final String description;
        final java.util.function.Supplier<Boolean> getter;
        final java.util.function.Consumer<Boolean> setter;
        // A "cycle" row (e.g. Mana Mode) renders a string value and advances it on
        // click instead of toggling ON/OFF. Both are null for boolean rows.
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

        /** Cycling row: {@code displaySupplier} provides the current value, {@code onCycle} advances it. */
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
