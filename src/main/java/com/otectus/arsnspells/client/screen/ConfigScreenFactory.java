package com.otectus.arsnspells.client.screen;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
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
        // ANS 3.0.1 legibility pass: the screen paints its own near-opaque
        // background and a bounded panel instead of the vanilla translucent dim
        // (which client blur mods hook and turn into unreadable frosted glass),
        // and rows/controls get real chrome with shared render/click geometry.

        // Layout
        private static final int ROW_STRIDE = 34;
        private static final int ROW_WIDTH = 340;
        private static final int PANEL_PAD = 8;
        private static final int BTN_H = 20;
        private static final int BTN_W_BOOL = 44;
        private static final int BTN_W_CYCLE = 110;
        private static final int FOOTER_H = 40;

        // Colors (full ARGB so blending over the panel fills stays consistent)
        private static final int BG_OVERLAY = 0xF2101014;
        private static final int PANEL_BG = 0xFF1A1A21;
        private static final int PANEL_BORDER = 0xFF5A5A6E;
        private static final int ROW_BG = 0x22FFFFFF;
        private static final int ROW_BG_HOVER = 0x33FFFFFF;
        private static final int TEXT_PRIMARY = 0xFFFFFFFF;
        private static final int TEXT_SECONDARY = 0xFFB8B8C0;
        private static final int TEXT_DISABLED = 0xFF707078;
        private static final int TEXT_NOTE = 0xFFF0C060;
        private static final int BTN_BG = 0xFF2E2E38;
        private static final int BTN_BG_HOVER = 0xFF3E3E4C;
        private static final int BTN_BG_DISABLED = 0xFF232329;
        private static final int BTN_BORDER = 0xFF8B8B9E;
        private static final int BTN_BORDER_HOVER = 0xFFE0E0F0;
        private static final int VALUE_ON = 0xFF55FF55;
        private static final int VALUE_OFF = 0xFFFF5555;

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
            // only resets the 9 boolean toggles visible in the UI, not the 90+ other
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
                "Source Jar Synergy",
                "Passive mana regen near Ars Nouveau Source Jars",
                () -> AnsConfig.ENABLE_SOURCE_JAR_SYNERGY.get(),
                value -> AnsConfig.ENABLE_SOURCE_JAR_SYNERGY.set(value)
            ));

            options.add(new ConfigOption(
                "Debug Mode",
                "Enable debug logging",
                () -> AnsConfig.DEBUG_MODE.get(),
                value -> AnsConfig.DEBUG_MODE.set(value)
            ));
        }
        
        // ---- Shared geometry: single source of truth for render AND click ----

        private int rowX() {
            return this.width / 2 - ROW_WIDTH / 2;
        }

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
            int x = rowX() + ROW_WIDTH - w - 6;
            int y = rowY + (ROW_STRIDE - 2 - BTN_H) / 2;
            return new int[]{x, y, w, BTN_H};
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Deliberately not calling the vanilla renderBackground: client blur
            // mods/shaders hook it and composite a frosted-glass effect under
            // translucent screens. An owned near-opaque fill keeps the text
            // legible regardless of what is behind (world, menu, blur).
            graphics.fill(0, 0, this.width, this.height, BG_OVERLAY);

            // Bounded content panel behind title and rows
            int panelX0 = rowX() - PANEL_PAD;
            int panelX1 = rowX() + ROW_WIDTH + PANEL_PAD;
            int panelY0 = 8;
            int panelY1 = this.height - FOOTER_H;
            graphics.fill(panelX0, panelY0, panelX1, panelY1, PANEL_BG);
            graphics.renderOutline(panelX0, panelY0, panelX1 - panelX0, panelY1 - panelY0, PANEL_BORDER);

            graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, TEXT_PRIMARY);
            graphics.drawCenteredString(this.font,
                Component.literal("Configure Ars 'n' Spells Integration"),
                this.width / 2, 28, TEXT_SECONDARY);
            if (!canMutate) {
                graphics.drawCenteredString(this.font,
                    Component.literal("Read-only: server-managed config."),
                    this.width / 2, 42, TEXT_NOTE);
                graphics.drawCenteredString(this.font,
                    Component.literal("Edit the server TOML or use /ans commands."),
                    this.width / 2, 52, TEXT_NOTE);
            }

            int visible = visibleRowCount();
            int y = listTop();
            for (int i = scrollOffset; i < options.size() && i < scrollOffset + visible; i++) {
                renderRow(graphics, options.get(i), y, mouseX, mouseY);
                y += ROW_STRIDE;
            }

            // Slim scrollbar when the list is clipped
            if (options.size() > visible) {
                int trackX0 = panelX1 - 6;
                int trackX1 = panelX1 - 3;
                int trackY0 = listTop();
                int trackY1 = listTop() + visible * ROW_STRIDE - 2;
                graphics.fill(trackX0, trackY0, trackX1, trackY1, ROW_BG);
                int trackH = trackY1 - trackY0;
                int maxOffset = options.size() - visible;
                int thumbH = Math.max(8, trackH * visible / options.size());
                int thumbY = trackY0 + (trackH - thumbH) * Math.min(scrollOffset, maxOffset) / maxOffset;
                graphics.fill(trackX0, thumbY, trackX1, thumbY + thumbH, BTN_BORDER);
            }

            // Last: widgets (Done/Reset) and any queued tooltip draw on top.
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        private void renderRow(GuiGraphics graphics, ConfigOption option, int rowY, int mouseX, int mouseY) {
            int x = rowX();
            boolean rowHovered = mouseX >= x && mouseX < x + ROW_WIDTH
                && mouseY >= rowY && mouseY < rowY + ROW_STRIDE - 2;
            graphics.fill(x, rowY, x + ROW_WIDTH, rowY + ROW_STRIDE - 2,
                rowHovered && canMutate ? ROW_BG_HOVER : ROW_BG);

            graphics.drawString(this.font, option.name, x + 6, rowY + 6,
                canMutate ? TEXT_PRIMARY : TEXT_SECONDARY, true);

            int[] rect = buttonRect(option, rowY);

            // Description: truncate to the space left of the control; full text
            // shows as a tooltip on hover instead of smearing under the button.
            int maxDescWidth = rect[0] - (x + 6) - 8;
            String desc = option.description;
            boolean truncated = false;
            if (this.font.width(desc) > maxDescWidth) {
                desc = this.font.plainSubstrByWidth(desc, maxDescWidth - this.font.width("...")) + "...";
                truncated = true;
            }
            graphics.drawString(this.font, desc, x + 6, rowY + 18,
                canMutate ? TEXT_SECONDARY : TEXT_DISABLED, true);
            if (truncated && rowHovered) {
                setTooltipForNextRenderPass(this.font.split(Component.literal(option.description), 200));
            }

            String label;
            int labelColor;
            if (option.isCycle()) {
                label = option.displaySupplier.get();
                labelColor = canMutate ? TEXT_PRIMARY : TEXT_DISABLED;
            } else {
                boolean on = option.getValue();
                label = on ? "ON" : "OFF";
                labelColor = canMutate ? (on ? VALUE_ON : VALUE_OFF) : TEXT_DISABLED;
            }
            boolean btnHovered = canMutate
                && mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
            drawButtonChrome(graphics, rect[0], rect[1], rect[2], rect[3],
                label, labelColor, btnHovered, canMutate);
        }

        private void drawButtonChrome(GuiGraphics graphics, int x, int y, int w, int h,
                                      String label, int labelColor, boolean hovered, boolean enabled) {
            graphics.fill(x, y, x + w, y + h,
                !enabled ? BTN_BG_DISABLED : hovered ? BTN_BG_HOVER : BTN_BG);
            graphics.renderOutline(x, y, w, h, hovered ? BTN_BORDER_HOVER : BTN_BORDER);
            String clipped = label;
            if (this.font.width(clipped) > w - 8) {
                clipped = this.font.plainSubstrByWidth(clipped, w - 8 - this.font.width("...")) + "...";
            }
            graphics.drawCenteredString(this.font, clipped, x + w / 2, y + (h - 8) / 2, labelColor);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // ANS 3.0.1: in multiplayer the whole list is read-only — SERVER-config
            // writes from the client mirror are silent no-ops on a dedicated server.
            // Gating here fixes the old bug where boolean rows toggled ungated
            // (cycle rows were already gated).
            if (canMutate && button == 0) {
                int visible = visibleRowCount();
                int y = listTop();
                for (int i = scrollOffset; i < options.size() && i < scrollOffset + visible; i++) {
                    ConfigOption option = options.get(i);
                    // Hit-test the exact rect the control is drawn at (shared geometry).
                    int[] rect = buttonRect(option, y);
                    if (mouseX >= rect[0] && mouseX < rect[0] + rect[2] &&
                        mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                        if (option.isCycle()) {
                            option.onCycle.run();
                        } else {
                            option.toggle();
                        }
                        if (minecraft != null) {
                            minecraft.getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        }
                        return true;
                    }
                    y += ROW_STRIDE;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            int maxOffset = Math.max(0, options.size() - visibleRowCount());
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) delta));
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
