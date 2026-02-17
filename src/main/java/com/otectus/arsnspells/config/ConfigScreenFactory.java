package com.otectus.arsnspells.config;

import com.mojang.blaze3d.vertex.PoseStack;
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
            
            // Add Done button
            this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                button -> {
                    saveConfig();
                    minecraft.setScreen(parent);
                })
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                .build()
            );
            
            // Add Reset to Defaults button
            this.addRenderableWidget(Button.builder(
                Component.literal("Reset to Defaults"),
                button -> resetToDefaults())
                .bounds(this.width / 2 - 205, this.height - 28, 100, 20)
                .build()
            );
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
            options.add(new ConfigOption(
                "Mana Mode",
                "Current: " + AnsConfig.MANA_UNIFICATION_MODE.get(),
                () -> true,
                value -> {} // Mode cycling handled separately
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
                
                // Draw toggle button
                String buttonText = option.getValue() ? "ON" : "OFF";
                int buttonColor = option.getValue() ? 0x00FF00 : 0xFF0000;
                
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
                
                // Check if click is on toggle button area
                if (mouseX >= x + 270 && mouseX <= x + 310 && 
                    mouseY >= y && mouseY <= y + 20) {
                    option.toggle();
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
            // Use safe save method with retry logic
            boolean success = AnsConfig.safeSave();
            
            // Invalidate config cache to force reload
            ConfigCache.invalidateAll();
            
            // Show message to player
            if (minecraft != null && minecraft.player != null) {
                if (success) {
                    minecraft.player.sendSystemMessage(
                        Component.literal("Ars 'n' Spells config saved!").withStyle(ChatFormatting.GREEN)
                    );
                } else {
                    minecraft.player.sendSystemMessage(
                        Component.literal("Failed to save config. Check logs for details.").withStyle(ChatFormatting.RED)
                    );
                }
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
        
        ConfigOption(String name, String description, 
                    java.util.function.Supplier<Boolean> getter,
                    java.util.function.Consumer<Boolean> setter) {
            this.name = name;
            this.description = description;
            this.getter = getter;
            this.setter = setter;
        }
        
        boolean getValue() {
            return getter.get();
        }
        
        void toggle() {
            setter.accept(!getValue());
        }
    }
}
