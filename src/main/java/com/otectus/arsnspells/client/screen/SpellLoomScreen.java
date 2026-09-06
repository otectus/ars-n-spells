package com.otectus.arsnspells.client.screen;

import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.inscription.LoomInscription;
import com.otectus.arsnspells.menu.SpellLoomMenu;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.SpellLoomExportPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Spell Loom GUI: name the spell, cycle a nature and a rudimentary icon symbol,
 * then inscribe onto the blank Iron's scroll. Drawn with flat fills (no shipped
 * GUI texture) so the workstation needs only JSON + vanilla block textures.
 *
 * <p>Layout is derived from named constants (regions top to bottom: title, name
 * input, recipe row, option buttons, action button, player inventory). The menu
 * ({@link SpellLoomMenu}) owns all slot coordinates; this screen only adds the
 * chrome around them. The GUI is 176x208 so the Inscribe button, "Inventory"
 * label, and player inventory never overlap — on 1.20.1,
 * {@code AbstractContainerScreen} draws widgets <em>before</em> slot items and
 * the slot hover highlight, so any geometric overlap paints items and a pale
 * highlight square over the button.
 *
 * <p>Readability follows the 3.0.1 config-screen approach: an owned, opaque
 * panel, vanilla-style slot bevels, and high-contrast shadowed labels
 * (the vanilla 0x404040 label default is unreadable on the purple panel).
 */
public class SpellLoomScreen extends AbstractContainerScreen<SpellLoomMenu> {
    // Canonical nature keys live in CrossCastNbt (the export packet whitelists
    // against them); each maps to an ARGB tint used for the preview swatch.
    private static final String[] NATURES =
        com.otectus.arsnspells.spell.CrossCastNbt.NATURE_KEYS.toArray(new String[0]);
    private static final int[] NATURE_COLORS = {
        0xFFB060FF, 0xFFFF6030, 0xFF60D0FF, 0xFFFFE040, 0xFF40C040,
        0xFFFFF0A0, 0xFFD03030, 0xFF9040E0
    };
    // Canonical symbol list lives in CrossCastNbt so the wheel-icon mixin, the
    // shipped icon_<key>.png textures, and this cycle button can never drift.
    private static final String[] ICONS =
        com.otectus.arsnspells.spell.CrossCastNbt.ICON_SYMBOLS.toArray(new String[0]);
    /** Shipped 16x16 wheel-icon textures, index-aligned with {@link #ICONS}. */
    private static final ResourceLocation[] ICON_TEXTURES = buildIconTextures();

    private static ResourceLocation[] buildIconTextures() {
        ResourceLocation[] out = new ResourceLocation[ICONS.length];
        for (int i = 0; i < ICONS.length; i++) {
            out[i] = new ResourceLocation("ars_n_spells",
                "textures/gui/icons/spell/icon_" + ICONS[i] + ".png");
        }
        return out;
    }

    // ---- Layout constants (container-relative; slot geometry is owned by SpellLoomMenu) ----
    private static final int MARGIN = 8;
    private static final int CONTENT_W = SpellLoomMenu.GUI_WIDTH - 2 * MARGIN; // 160
    private static final int NAME_Y = 18;
    private static final int NAME_H = 16;
    private static final int OPTIONS_Y = 62;
    private static final int BTN_H = 20;
    private static final int OPTION_BTN_W = 78;
    private static final int ACTION_Y = 86;
    /** The action row carries Inscribe plus the separate, explicit Convert action. */
    private static final int INSCRIBE_BTN_W = 108;
    private static final int CONVERT_BTN_W = CONTENT_W - INSCRIBE_BTN_W - 4;
    /** Wheel-icon preview: slot-sized, at the right end of the recipe row. */
    private static final int PREVIEW_X = 152;
    private static final int PREVIEW_Y = SpellLoomMenu.RECIPE_ROW_Y;
    /** Vertical centering for the +/→ glyphs within the 16px slot row. */
    private static final int GLYPH_Y = SpellLoomMenu.RECIPE_ROW_Y + 4;

    // ---- Colors (full ARGB; same palette family as the 3.0.1 config screen) ----
    private static final int PANEL_BORDER = 0xFF2B2233;
    private static final int PANEL_BG = 0xFF3C3147;
    private static final int TEXT_PRIMARY = 0xFFFFFF;
    // Vanilla slot bevel: dark top/left, light bottom/right, gray inner.
    private static final int SLOT_INNER = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;

    private EditBox nameField;
    private Button natureButton;
    private Button iconButton;
    private Button inscribeButton;
    private Button convertButton;
    private int natureIndex = 0;
    private int iconIndex = 0;
    /**
     * The last reason code the server sent back (audit V18). The client mirrors the plan
     * locally to keep the button responsive, but this is the authoritative answer and it is
     * what the status line under the buttons shows.
     */
    private String serverReason = InscriptionPlan.REASON_OK;
    /** Whether {@link #serverReason} answered a preview or a real attempt. */
    private boolean serverReasonIsPreview = true;
    /** Fingerprint of the two input slots, so a preview is requested on change, not per tick. */
    private String previewedInputs = null;

    public SpellLoomScreen(SpellLoomMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = SpellLoomMenu.GUI_WIDTH;
        this.imageHeight = SpellLoomMenu.GUI_HEIGHT;
        // Vanilla formula: label sits 12px above the first inventory row.
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        nameField = new EditBox(this.font, x + MARGIN, y + NAME_Y, CONTENT_W, NAME_H,
            Component.translatable("ars_n_spells.spell_loom.name_hint"));
        nameField.setMaxLength(40);
        nameField.setHint(Component.translatable("ars_n_spells.spell_loom.name_hint"));
        addRenderableWidget(nameField);

        natureButton = Button.builder(natureLabel(), b -> {
                natureIndex = (natureIndex + 1) % NATURES.length;
                b.setMessage(natureLabel());
            })
            .bounds(x + MARGIN, y + OPTIONS_Y, OPTION_BTN_W, BTN_H)
            .tooltip(Tooltip.create(Component.translatable("ars_n_spells.spell_loom.tooltip.nature")))
            .build();
        addRenderableWidget(natureButton);

        iconButton = Button.builder(iconLabel(), b -> {
                iconIndex = (iconIndex + 1) % ICONS.length;
                b.setMessage(iconLabel());
            })
            .bounds(x + MARGIN + OPTION_BTN_W + 4, y + OPTIONS_Y, OPTION_BTN_W, BTN_H)
            .tooltip(Tooltip.create(Component.translatable("ars_n_spells.spell_loom.tooltip.icon")))
            .build();
        addRenderableWidget(iconButton);

        inscribeButton = Button.builder(
                Component.translatable("ars_n_spells.spell_loom.export"),
                b -> sendAction(SpellLoomExportPacket.ACTION_INSCRIBE))
            .bounds(x + MARGIN, y + ACTION_Y, INSCRIBE_BTN_W, BTN_H)
            .build();
        addRenderableWidget(inscribeButton);

        // Audit V18: converting a scroll that already holds a spell is its own action, never a
        // side effect of Inscribe. It only lights up once the preview has said the target is
        // not blank, and its tooltip says outright that the existing spell is destroyed.
        convertButton = Button.builder(
                Component.translatable("ars_n_spells.spell_loom.convert"),
                b -> sendAction(SpellLoomExportPacket.ACTION_CONVERT))
            .bounds(x + MARGIN + INSCRIBE_BTN_W + 4, y + ACTION_Y, CONVERT_BTN_W, BTN_H)
            .tooltip(Tooltip.create(Component.translatable("ars_n_spells.spell_loom.tooltip.convert")))
            .build();
        addRenderableWidget(convertButton);
        updateInscribeState();
    }

    private Component natureLabel() {
        return Component.translatable("ars_n_spells.spell_loom.nature",
            Component.translatable("ars_n_spells.nature." + NATURES[natureIndex]));
    }

    private Component iconLabel() {
        return Component.translatable("ars_n_spells.spell_loom.icon",
            Component.translatable("ars_n_spells.icon." + ICONS[iconIndex]));
    }

    private void sendAction(int action) {
        String name = nameField.getValue() == null ? "" : nameField.getValue().trim();
        PacketHandler.sendToServer(new SpellLoomExportPacket(
            name, NATURES[natureIndex], ICONS[iconIndex], action));
    }

    /**
     * Receive a {@code SpellLoomResultPacket}. Static because the packet has no handle on the
     * open screen; it resolves the current one and drops the result if the player has since
     * closed the loom.
     */
    public static void acceptResult(String reasonCode, boolean preview) {
        Screen open = Minecraft.getInstance().screen;
        if (open instanceof SpellLoomScreen loom) {
            loom.serverReason = reasonCode == null ? InscriptionPlan.REASON_OK : reasonCode;
            loom.serverReasonIsPreview = preview;
            loom.updateInscribeState();
        }
    }

    // ---- Inscribe enablement (client-side mirror of the packet's validation) ----

    @Override
    protected void containerTick() {
        super.containerTick();
        // AbstractContainerScreen does not tick widgets; without this the
        // name field's caret never blinks.
        nameField.tick();
        requestPreviewOnInputChange();
        updateInscribeState();
    }

    /**
     * Ask the server what it would do, once per change of the input slots.
     *
     * <p>A preview is a pure read on both sides -- the server plans and replies, moving
     * nothing -- so the only cost worth avoiding is sending one every tick.
     */
    private void requestPreviewOnInputChange() {
        if (this.menu.slots.size() < SpellLoomBlockEntity.SLOT_COUNT) {
            return;
        }
        ItemStack source = this.menu.getSlot(SpellLoomBlockEntity.SLOT_SOURCE).getItem();
        ItemStack scroll = this.menu.getSlot(SpellLoomBlockEntity.SLOT_SCROLL).getItem();
        String fingerprint = source.getCount() + ":" + source.getDescriptionId() + ":"
            + source.getTag() + "|" + scroll.getCount() + ":" + scroll.getDescriptionId() + ":"
            + scroll.getTag();
        if (fingerprint.equals(previewedInputs)) {
            return;
        }
        previewedInputs = fingerprint;
        sendAction(SpellLoomExportPacket.ACTION_PREVIEW);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        // resize() re-runs init(), which rebuilds the EditBox — preserve the
        // typed name across window resizes (same pattern as AnvilScreen).
        String typed = nameField.getValue();
        super.resize(minecraft, width, height);
        nameField.setValue(typed);
    }

    /**
     * Enables Inscribe only when the inscription can succeed, and puts the reason in its
     * tooltip. Mirrors -- never replaces -- the server's own decision: the same
     * {@link LoomInscription} planner runs here against the synced slots, and what the server
     * sends back is the authority. There is no second copy of the blankness rule anywhere.
     */
    private void updateInscribeState() {
        if (inscribeButton == null || convertButton == null) {
            return;
        }
        String reasonCode = currentReasonCode();
        boolean ok = InscriptionPlan.REASON_OK.equals(reasonCode);
        inscribeButton.active = ok;
        inscribeButton.setTooltip(Tooltip.create(ok
            ? Component.translatable("ars_n_spells.spell_loom.tooltip.inscribe")
            : reasonMessage(reasonCode)));
        // Convert only offers itself for the one refusal it can actually fix, and it is the
        // only route by which a filled scroll is ever overwritten.
        convertButton.active = InscriptionPlan.REASON_NOT_BLANK.equals(reasonCode)
            || InscriptionPlan.REASON_TARGET_NOT_EMPTY.equals(reasonCode);
    }

    /**
     * The reason code to show: the plan for the slots as they stand, falling back to the
     * server's last word when there is no client-side block entity to plan against.
     */
    private String currentReasonCode() {
        SpellLoomBlockEntity be = this.menu.getBlockEntity();
        if (be == null || this.menu.slots.size() < SpellLoomBlockEntity.SLOT_COUNT) {
            // Desynced menu: nothing local to plan against. A reply to a finished attempt says
            // nothing about what the slots hold now, so only a preview answer is reusable and
            // anything else keeps the button disabled, as it was before.
            return serverReasonIsPreview ? serverReason : LoomInscription.REASON_CARRIER_FAILED;
        }
        if (!this.menu.getSlot(SpellLoomBlockEntity.SLOT_OUTPUT).getItem().isEmpty()) {
            return LoomInscription.REASON_OUTPUT_OCCUPIED;
        }
        if (!IronsCompat.isLoaded()) {
            return LoomInscription.REASON_IRONS_MISSING;
        }
        if (this.menu.getSlot(SpellLoomBlockEntity.SLOT_SOURCE).getItem().isEmpty()) {
            return LoomInscription.REASON_NO_ARS_SPELL;
        }
        return LoomInscription.plan(be).reasonCode();
    }

    /** Reason code to player-facing text. Codes are stable strings; the keys are shipped lang. */
    private static Component reasonMessage(String reasonCode) {
        String key = switch (reasonCode) {
            case InscriptionPlan.REASON_NOT_BLANK -> "ars_n_spells.spell_loom.error.not_blank";
            case InscriptionPlan.REASON_TARGET_NOT_EMPTY ->
                "ars_n_spells.spell_loom.error.target_not_empty";
            case InscriptionPlan.REASON_INSUFFICIENT_STACK ->
                "ars_n_spells.spell_loom.error.insufficient_stack";
            case LoomInscription.REASON_OUTPUT_OCCUPIED ->
                "ars_n_spells.spell_loom.error.output_full";
            case LoomInscription.REASON_IRONS_MISSING ->
                "ars_n_spells.spell_loom.error.irons_missing";
            case LoomInscription.REASON_NO_ARS_SPELL -> "ars_n_spells.spell_loom.error.no_source";
            default -> "ars_n_spells.spell_loom.error.failed";
        };
        return Component.translatable(key);
    }

    // ---- Rendering ----

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        // Opaque panel + border (blur/shader mods can't frost an owned fill).
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BORDER);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, PANEL_BG);

        // Vanilla-style bevel chrome under EVERY slot the menu defines — the
        // three working slots and all 36 player inventory slots alike.
        for (Slot slot : this.menu.slots) {
            drawSlotChrome(g, x + slot.x, y + slot.y);
        }

        // Recipe-flow glyphs, centered between the slot edges they connect:
        // source inner box ends at SLOT_SOURCE_X+16, scroll starts at SLOT_SCROLL_X, etc.
        int plusCx = x + (SpellLoomMenu.SLOT_SOURCE_X + 16 + SpellLoomMenu.SLOT_SCROLL_X) / 2;
        int arrowCx = x + (SpellLoomMenu.SLOT_SCROLL_X + 16 + SpellLoomMenu.SLOT_OUTPUT_X) / 2;
        g.drawCenteredString(this.font, "+", plusCx, y + GLYPH_Y, TEXT_PRIMARY);
        g.drawCenteredString(this.font, "→", arrowCx, y + GLYPH_Y, TEXT_PRIMARY);

        // Wheel-icon preview: slot chrome, nature tint, then the actual shipped
        // 16x16 icon texture the wheel will show. (9-arg blit: the 7-arg
        // overload assumes a 256x256 texture and would sample garbage.)
        drawSlotChrome(g, x + PREVIEW_X, y + PREVIEW_Y);
        g.fill(x + PREVIEW_X, y + PREVIEW_Y, x + PREVIEW_X + 16, y + PREVIEW_Y + 16,
            NATURE_COLORS[natureIndex]);
        g.blit(ICON_TEXTURES[iconIndex], x + PREVIEW_X, y + PREVIEW_Y, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    /** Classic vanilla inset slot: gray inner, dark top/left, light bottom/right. */
    private static void drawSlotChrome(GuiGraphics g, int sx, int sy) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_INNER);
        g.fill(sx - 1, sy - 1, sx + 16, sy, SLOT_DARK);      // top
        g.fill(sx - 1, sy, sx, sy + 16, SLOT_DARK);          // left
        g.fill(sx - 1, sy + 16, sx + 17, sy + 17, SLOT_LIGHT); // bottom
        g.fill(sx + 16, sy - 1, sx + 17, sy + 16, SLOT_LIGHT); // right
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // High-contrast shadowed labels: the vanilla 0x404040 default is
        // unreadable on the purple panel.
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT_PRIMARY, true);
        g.drawString(this.font, this.playerInventoryTitle,
            this.inventoryLabelX, this.inventoryLabelY, TEXT_PRIMARY, true);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
        renderRegionTooltips(g, mouseX, mouseY);
    }

    /** Tooltips for the preview swatch and the three working slots while empty. */
    private void renderRegionTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        if (isOver(mouseX, mouseY, x + PREVIEW_X, y + PREVIEW_Y)) {
            g.renderTooltip(this.font,
                Component.translatable("ars_n_spells.spell_loom.tooltip.preview",
                    Component.translatable("ars_n_spells.nature." + NATURES[natureIndex]),
                    Component.translatable("ars_n_spells.icon." + ICONS[iconIndex])),
                mouseX, mouseY);
            return;
        }
        // When the block entity is missing client-side the menu holds only the
        // 36 player slots, so indices 0..2 would be player slots — skip.
        if (this.hoveredSlot == null || this.hoveredSlot.hasItem()
            || this.menu.getBlockEntity() == null || this.menu.slots.size() < 3) {
            return;
        }
        String key = null;
        if (this.hoveredSlot == this.menu.getSlot(0)) {
            key = "ars_n_spells.spell_loom.tooltip.source_slot";
        } else if (this.hoveredSlot == this.menu.getSlot(1)) {
            key = "ars_n_spells.spell_loom.tooltip.scroll_slot";
        } else if (this.hoveredSlot == this.menu.getSlot(2)) {
            key = "ars_n_spells.spell_loom.tooltip.output_slot";
        }
        if (key != null) {
            g.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
        }
    }

    private static boolean isOver(int mouseX, int mouseY, int boxX, int boxY) {
        return mouseX >= boxX - 1 && mouseX < boxX + 17 && mouseY >= boxY - 1 && mouseY < boxY + 17;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Let the name field consume typing (incl. space/E) unless ESC is pressed.
        if (nameField != null && nameField.isFocused() && keyCode != 256) {
            return nameField.keyPressed(keyCode, scanCode, modifiers)
                || nameField.canConsumeInput()
                || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
