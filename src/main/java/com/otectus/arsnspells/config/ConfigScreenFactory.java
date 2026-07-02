package com.otectus.arsnspells.config;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.arsnspells.bridge.BridgeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
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
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                .build()
            );

            // Add Reset Toggles button — disabled in multiplayer
            // ANS-OPT-018: renamed from "Reset to Defaults" because resetToDefaults()
            // only resets the 8 boolean toggles visible in the UI, not the 90+ other
            // config keys. "Reset Toggles" matches what the button actually does.
            Button resetButton = Button.builder(
                Component.literal("Reset Toggles"),
                button -> resetToDefaults())
                .bounds(this.width / 2 - 205, this.height - 28, 100, 20)
                .build();
            resetButton.active = canMutate;
            this.addRenderableWidget(resetButton);
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
                "Debug Mode",
                "Enable debug logging",
                () -> AnsConfig.DEBUG_MODE.get(),
                value -> AnsConfig.DEBUG_MODE.set(value)
            ));
        }
        
        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);
            
            // Draw title
            graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
            
            // Draw subtitle
            graphics.drawCenteredString(this.font, 
                Component.literal("Configure Ars 'n' Spells Integration"), 
                this.width / 2, 28, 0xAAAAAA);
            
            // Draw options
            int y = 50;
            int x = this.width / 2 - 150;
            
            for (int i = scrollOffset; i < options.size() && y < this.height - 50; i++) {
                ConfigOption option = options.get(i);
                
                // Draw option name
                graphics.drawString(this.font, option.name, x, y, 0xFFFFFF);
                
                // Draw option description
                graphics.drawString(this.font, option.description, x, y + 12, 0x888888);
                
                // Draw the control: cycling rows (e.g. Mana Mode) show their current
                // value; boolean rows show ON/OFF. A cycling row greys out when it
                // cannot be mutated (multiplayer).
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

                // Cycling rows render wider text (a mode name), so give them a wider hit-box.
                int rightEdge = option.isCycle() ? x + 380 : x + 310;
                if (mouseX >= x + 270 && mouseX <= rightEdge &&
                    mouseY >= y && mouseY <= y + 20) {
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
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            scrollOffset = Math.max(0, Math.min(options.size() - 10, scrollOffset - (int)delta));
            return true;
        }
        
        private void saveConfig() {
            // ANS-HIGH-017: safeSave() only SCHEDULES an async write and always
            // returns true — the real outcome lands in the log. The message below
            // is worded accordingly instead of claiming the file was written.
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
            // Reset all values to defaults
            AnsConfig.ENABLE_MANA_UNIFICATION.set(true);
            AnsConfig.ENABLE_RESONANCE_SYSTEM.set(true);
            AnsConfig.ENABLE_COOLDOWN_SYSTEM.set(true);
            AnsConfig.ENABLE_PROGRESSION_SYSTEM.set(true);
            AnsConfig.ENABLE_AFFINITY_SYSTEM.set(true);
            AnsConfig.respectArmorBonuses.set(true);
            AnsConfig.respectEnchantments.set(true);
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
