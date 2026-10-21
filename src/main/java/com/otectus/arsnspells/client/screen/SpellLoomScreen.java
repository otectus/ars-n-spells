package com.otectus.arsnspells.client.screen;

import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.contract.InscriptionPlan;
import com.otectus.arsnspells.inscription.LoomInscription;
import com.otectus.arsnspells.menu.SpellLoomMenu;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.SpellLoomExportPayload;
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
import com.otectus.arsnspells.icons.IconCatalog;
import com.otectus.arsnspells.client.icons.SpellIconRegistry;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.rituals.InscriptionInputs;
import com.otectus.arsnspells.spell.CrossCastingHandler;
import com.otectus.arsnspells.spell.CrossModSpell;
import com.otectus.arsnspells.spell.IronsBookBindingUtil;
import com.otectus.arsnspells.spell.ScrollKind;
import com.otectus.arsnspells.spell.SpellSchoolNames;
import com.otectus.arsnspells.util.SpellAnalysis;

/**
 * Vanilla-style 3.3.0 workstation. The menu owns the slot coordinates; the screen
 * adds native widgets, a compact status hint and access to full inspection text.
 * The 176x240 panel fits Minecraft's minimum 320x240 scaled viewport.
 *
 * <p>The School row is the spell's mechanical school and sits apart from the cosmetic
 * controls below it: the icon library's background is a frame, the school is what the spell
 * scales with and trains. Apply changes the school of a carrier scroll already in the target
 * slot, in place.
 */
public class SpellLoomScreen extends AbstractContainerScreen<SpellLoomMenu> {
    // Canonical nature keys live in CrossCastNbt (the export packet whitelists
    // against them); each maps to an ARGB tint used for the preview swatch.
    private static final String[] NATURES =
        com.otectus.arsnspells.spell.CrossModSpellComponents.NATURE_KEYS.toArray(new String[0]);
    // Canonical symbol list lives in CrossCastNbt so the wheel-icon mixin, the
    // shipped icon_<key>.png textures, and this cycle button can never drift.
    private static final String[] ICONS =
        IconCatalog.IDS.toArray(new String[0]);

    // ---- Layout constants (container-relative; slot geometry is owned by SpellLoomMenu) ----
    private static final int MARGIN = 8;
    private static final int CONTENT_W = SpellLoomMenu.GUI_WIDTH - 2 * MARGIN; // 160
    private static final int NAME_Y = 18;
    private static final int NAME_H = 16;
    /** The mechanical school row, directly under the recipe; cosmetics follow below it. */
    private static final int SCHOOL_Y = 59;
    private static final int SCHOOL_APPLY_W = 48;
    private static final int SCHOOL_BTN_W = CONTENT_W - SCHOOL_APPLY_W - 4;
    private static final int OPTIONS_Y = 82;
    private static final int BTN_H = 20;
    private static final int OPTION_BTN_W = 78;
    private static final int ACTION_Y = 105;
    /** Two reserved status lines between the action row and the inventory label. */
    private static final int STATUS_Y = 128;
    /** The action row carries Inscribe plus the separate, explicit Convert action. */
    private static final int INSCRIBE_BTN_W = 100;
    private static final int CONVERT_BTN_W = CONTENT_W - INSCRIBE_BTN_W - 4;
    /** Wheel-icon preview: slot-sized, at the right end of the recipe row. */
    private static final int PREVIEW_X = 152;
    private static final int PREVIEW_Y = SpellLoomMenu.RECIPE_ROW_Y;
    /** Vertical centering for the +/→ glyphs within the 16px slot row. */
    private static final int GLYPH_Y = SpellLoomMenu.RECIPE_ROW_Y + 4;

    private EditBox nameField;
    private Button schoolButton;
    private Button applySchoolButton;
    private Button natureButton;
    private Button iconButton;
    private Button inscribeButton;
    private Button convertButton;
    private int natureIndex = 0;
    private int iconIndex = 0;
    /** The requested mechanical school: {@code ""} is Automatic. Never derived from the background. */
    private String schoolChoice = "";
    private boolean pickerOpen;
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
    private com.otectus.arsnspells.network.LoomRequestContext latestRequest;

    public SpellLoomScreen(SpellLoomMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = SpellLoomMenu.GUI_WIDTH;
        this.imageHeight = SpellLoomMenu.GUI_HEIGHT;
        // Vanilla formula: label sits 12px above the first inventory row.
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        String typedName = nameField == null ? "" : nameField.getValue();
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        nameField = new EditBox(this.font, x + MARGIN, y + NAME_Y, CONTENT_W, NAME_H,
            Component.translatable("ars_n_spells.spell_loom.name_hint"));
        nameField.setMaxLength(40);
        nameField.setValue(typedName);
        nameField.setHint(Component.translatable("ars_n_spells.spell_loom.name_hint"));
        addRenderableWidget(nameField);

        schoolButton = Button.builder(schoolButtonLabel(), b -> {
                pickerOpen = true;
                Minecraft.getInstance().setScreen(new SpellSchoolPickerScreen(this, schoolChoice,
                    automaticLabel(), chosen -> schoolChoice = chosen));
            })
            .bounds(x + MARGIN, y + SCHOOL_Y, SCHOOL_BTN_W, BTN_H)
            .build();
        addRenderableWidget(schoolButton);

        // Apply never touches the source or output: it rewrites the school of the carrier
        // scroll in the target slot, keeping its spell, name, background and icon.
        applySchoolButton = Button.builder(
                Component.translatable("ars_n_spells.spell_loom.school.apply"),
                b -> sendAction(SpellLoomExportPayload.ACTION_SET_SCHOOL))
            .bounds(x + MARGIN + SCHOOL_BTN_W + 4, y + SCHOOL_Y, SCHOOL_APPLY_W, BTN_H)
            .build();
        addRenderableWidget(applySchoolButton);

        natureButton = Button.builder(Component.translatable("ars_n_spells.spell_loom.details.open"), b -> {
                pickerOpen = true;
                Minecraft.getInstance().setScreen(new SpellLoomDetailsScreen(this));
            })
            .bounds(x + MARGIN, y + OPTIONS_Y, OPTION_BTN_W, BTN_H)
            .tooltip(Tooltip.create(Component.translatable("ars_n_spells.spell_loom.details.help")))
            .build();
        addRenderableWidget(natureButton);

        iconButton = Button.builder(iconLabel(), b -> {
                pickerOpen = true;
                Minecraft.getInstance().setScreen(new SpellIconPickerScreen(this, ICONS[iconIndex], NATURES[natureIndex], (icon, frame) -> {
                    iconIndex = IconCatalog.IDS.indexOf(icon);
                    natureIndex = IconCatalog.BACKGROUNDS.indexOf(frame);
                }));
            })
            .bounds(x + MARGIN + OPTION_BTN_W + 4, y + OPTIONS_Y, OPTION_BTN_W, BTN_H)
            .tooltip(Tooltip.create(Component.translatable("ars_n_spells.spell_loom.tooltip.icon")))
            .build();
        addRenderableWidget(iconButton);

        inscribeButton = Button.builder(
                Component.translatable("ars_n_spells.spell_loom.export"),
                b -> sendAction(SpellLoomExportPayload.ACTION_INSCRIBE))
            .bounds(x + MARGIN, y + ACTION_Y, INSCRIBE_BTN_W, BTN_H)
            .build();
        addRenderableWidget(inscribeButton);

        // Audit V18: converting a scroll that already holds a spell is its own action, never a
        // side effect of Inscribe. It only lights up once the preview has said the target is
        // not blank, and its tooltip says outright that the existing spell is destroyed.
        convertButton = Button.builder(
                Component.translatable("ars_n_spells.spell_loom.convert"),
                b -> sendAction(SpellLoomExportPayload.ACTION_CONVERT))
            .bounds(x + MARGIN + INSCRIBE_BTN_W + 4, y + ACTION_Y, CONVERT_BTN_W, BTN_H)
            .tooltip(Tooltip.create(Component.translatable("ars_n_spells.spell_loom.tooltip.convert")))
            .build();
        addRenderableWidget(convertButton);
        updateInscribeState();
    }

    LoomCosmeticPresets.Preset cosmeticPreset() {
        return new LoomCosmeticPresets.Preset(nameField.getValue(), NATURES[natureIndex], ICONS[iconIndex]);
    }
    void applyPreset(LoomCosmeticPresets.Preset preset) {
        nameField.setValue(preset.name());
        natureIndex = Math.max(0, IconCatalog.BACKGROUNDS.indexOf(preset.background()));
        iconIndex = Math.max(0, IconCatalog.IDS.indexOf(preset.icon()));
    }
    private InscriptionPlan displayedPlan() {
        var be = menu.getBlockEntity();
        if (be == null) return null;
        InscriptionPlan plan = LoomInscription.plan(be);
        if (InscriptionPlan.REASON_NOT_BLANK.equals(plan.reasonCode()) || InscriptionPlan.REASON_TARGET_NOT_EMPTY.equals(plan.reasonCode()))
            return LoomInscription.planConversion(be);
        return plan;
    }
    private Component consumptionLine() {
        InscriptionPlan plan = displayedPlan();
        if (plan == null || !plan.isPermitted()) return Component.translatable("ars_n_spells.spell_loom.details.no_movement");
        return Component.translatable("ars_n_spells.spell_loom.details.consumption", plan.consumedUnits(), 1, plan.outputCount());
    }
    private Component capacityLine() {
        boolean occupied = menu.getBlockEntity() != null && menu.slots.size() >= 3 && menu.getSlot(2).hasItem();
        return Component.translatable(occupied ? "ars_n_spells.spell_loom.details.output_full" : "ars_n_spells.spell_loom.details.output_free");
    }
    private Component statusLine() {
        if (reschoolMode()) {
            String reason = schoolChangeReason();
            return InscriptionPlan.REASON_OK.equals(reason)
                ? Component.translatable("ars_n_spells.spell_loom.details.reschool_ready", SpellSchoolNames.name(schoolChoice))
                : reasonMessage(reason);
        }
        String reason = currentReasonCode();
        return InscriptionPlan.REASON_OK.equals(reason) ? Component.translatable("ars_n_spells.spell_loom.details.ready") : reasonMessage(reason);
    }
    private Component compactStatus() {
        String reason = reschoolMode() ? schoolChangeReason() : currentReasonCode();
        if (InscriptionPlan.REASON_OK.equals(reason)) return statusLine();
        Component full = reasonMessage(reason);
        if (full.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text)
            return Component.translatable(text.getKey().replace(".error.", ".status."));
        return full;
    }
    java.util.List<Component> inspectionLines() {
        java.util.List<Component> result = new java.util.ArrayList<>();
        result.add(Component.translatable("ars_n_spells.spell_loom.details.synced"));
        result.add(consumptionLine()); result.add(capacityLine()); result.add(statusLine());
        SpellAnalysis.Result glyphs = subjectAnalysis();
        result.add(Component.translatable("ars_n_spells.spell_loom.details.school_choice",
            SpellSchoolNames.describe(schoolChoice, glyphs)));
        result.add(Component.translatable("ars_n_spells.spell_loom.details.school", SpellSchoolNames.glyphSchools(glyphs)));
        CrossModSpell carrierEntry = targetCarrierEntry();
        if (carrierEntry != null) {
            result.add(Component.translatable("ars_n_spells.spell_loom.details.target_school",
                SpellSchoolNames.describe(carrierEntry.school().orElse(""), carrierAnalysis(carrierEntry))));
        }
        result.add(Component.translatable("ars_n_spells.spell_loom.details.school_effect"));
        result.add(Component.translatable("ars_n_spells.spell_loom.details.mapping", com.otectus.arsnspells.util.SchoolMappings.get().digest()));
        result.add(Component.translatable("ars_n_spells.spell_loom.details.server_cost"));
        return result;
    }

    // ---- Mechanical school ----

    /** The single Ars entry of a carrier scroll in the target slot, or null. Read-only. */
    private CrossModSpell targetCarrierEntry() {
        if (menu.getBlockEntity() == null || menu.slots.size() < SpellLoomBlockEntity.SLOT_COUNT) return null;
        ItemStack target = menu.getSlot(SpellLoomBlockEntity.SLOT_SCROLL).getItem();
        if (ScrollKind.classify(target) != ScrollKind.ANS_CARRIER) return null;
        return IronsBookBindingUtil.extractSingleEntry(target).orElse(null);
    }

    private static SpellAnalysis.Result carrierAnalysis(CrossModSpell entry) {
        try {
            return SpellAnalysis.analyze(CrossCastingHandler.decodeArsSpell(entry.arsSpellTag().orElse(null)));
        } catch (RuntimeException unreadable) {
            return SpellAnalysis.analyze((Spell) null);
        }
    }

    /**
     * The spell a school choice would apply to, as its glyphs resolve it: the source spell when
     * there is one, otherwise the carrier waiting in the target slot for Apply.
     */
    private SpellAnalysis.Result subjectAnalysis() {
        if (menu.getBlockEntity() != null && menu.slots.size() >= SpellLoomBlockEntity.SLOT_COUNT) {
            var source = InscriptionInputs.readSource(menu.getSlot(SpellLoomBlockEntity.SLOT_SOURCE).getItem());
            if (source != null && source.arsSpell != null) return SpellAnalysis.analyze(source.arsSpell);
            CrossModSpell carrier = targetCarrierEntry();
            if (carrier != null) return carrierAnalysis(carrier);
        }
        return SpellAnalysis.analyze((Spell) null);
    }

    /** "Automatic (from glyphs: ...)" for the current subject. */
    private Component automaticLabel() {
        return Component.translatable("ars_n_spells.school.describe.automatic",
            SpellSchoolNames.glyphSchools(subjectAnalysis()));
    }

    /** The School button's label: the selection, then what it resolves to, shortened to fit. */
    private Component schoolButtonLabel() {
        Component value = schoolChoice.isEmpty()
            ? Component.translatable("ars_n_spells.spell_loom.school.auto_short", SpellSchoolNames.glyphSchools(subjectAnalysis()))
            : SpellSchoolNames.name(schoolChoice);
        Component full = Component.translatable("ars_n_spells.spell_loom.school.button", value);
        return Component.literal(VanillaGui.ellipsize(this.font, full.getString(), SCHOOL_BTN_W - 8));
    }

    /**
     * Apply mode: a carrier scroll waits in the target slot and nothing is in the source slot,
     * so the only thing the Loom can do with it is change its school.
     */
    private boolean reschoolMode() {
        return targetCarrierEntry() != null
            && menu.getSlot(SpellLoomBlockEntity.SLOT_SOURCE).getItem().isEmpty();
    }

    /** Client mirror of the server's set-school decision; the server answer is authoritative. */
    private String schoolChangeReason() {
        SpellLoomBlockEntity be = menu.getBlockEntity();
        if (be == null || menu.slots.size() < SpellLoomBlockEntity.SLOT_COUNT) return LoomInscription.REASON_NOT_A_CARRIER;
        return LoomInscription.planSchoolChange(be, schoolChoice);
    }

    private Component natureLabel() {
        return Component.translatable("icon.ars_n_spells.background." + NATURES[natureIndex]);
    }

    private Component iconLabel() {
        return Component.translatable("ars_n_spells.icon_picker.open");
    }

    @Override
    public void removed() {
        // The picker keeps this server menu open. It never transfers slot ownership.
        if (!pickerOpen) super.removed();
    }

    void returnFromPicker() {
        pickerOpen = false;
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.containerMenu == menu) {
            Minecraft.getInstance().setScreen(this);
        } else Minecraft.getInstance().setScreen(null);
    }

    private void sendAction(int action) {
        String name = nameField.getValue() == null ? "" : nameField.getValue().trim();
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.containerMenu != menu) return;
        String revision = com.otectus.arsnspells.network.LoomInventoryRevision.of(menu);
        if (revision.isEmpty()) return;
        latestRequest = new com.otectus.arsnspells.network.LoomRequestContext(menu.containerId, menu.sessionId(),
            java.util.UUID.randomUUID(), revision, com.otectus.arsnspells.util.SchoolMappings.get().digest());
        PacketHandler.sendToServer(new SpellLoomExportPayload(
            name, NATURES[natureIndex], ICONS[iconIndex], schoolChoice, action, latestRequest));
    }

    /**
     * Receive a {@code SpellLoomResultPayload}. Static because the packet has no handle on the
     * open screen; it resolves the current one and drops the result if the player has since
     * closed the loom.
     */
    public static void acceptResult(String reasonCode, boolean preview, com.otectus.arsnspells.network.LoomRequestContext request) {
        Screen open = Minecraft.getInstance().screen;
        if (open instanceof SpellLoomScreen loom && Minecraft.getInstance().player != null
            && Minecraft.getInstance().player.containerMenu == loom.menu && request.equals(loom.latestRequest)
            && request.matches(loom.menu.containerId, loom.menu.sessionId(),
                com.otectus.arsnspells.network.LoomInventoryRevision.of(loom.menu), com.otectus.arsnspells.util.SchoolMappings.get().digest())) {
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
        // The requested school is part of what the server judges, so a new choice asks again.
        String fingerprint = com.otectus.arsnspells.network.LoomInventoryRevision.of(menu)
            + ":" + com.otectus.arsnspells.util.SchoolMappings.get().digest() + ":" + schoolChoice;
        if (fingerprint.equals(previewedInputs)) {
            return;
        }
        previewedInputs = fingerprint;
        sendAction(SpellLoomExportPayload.ACTION_PREVIEW);
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
        if (schoolButton != null && applySchoolButton != null) {
            schoolButton.setMessage(schoolButtonLabel());
            schoolButton.setTooltip(Tooltip.create(Component.translatable("ars_n_spells.spell_loom.school.tooltip",
                SpellSchoolNames.describe(schoolChoice, subjectAnalysis()))));
            String schoolReason = schoolChangeReason();
            boolean applicable = InscriptionPlan.REASON_OK.equals(schoolReason);
            applySchoolButton.active = applicable;
            applySchoolButton.setTooltip(Tooltip.create(applicable
                ? Component.translatable("ars_n_spells.spell_loom.tooltip.apply_school", SpellSchoolNames.name(schoolChoice))
                : reasonMessage(schoolReason)));
        }
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
        String plan = LoomInscription.plan(be).reasonCode();
        // A school this server cannot use stops Inscribe and Convert alike, as the server will.
        if (InscriptionPlan.REASON_OK.equals(plan) || InscriptionPlan.REASON_NOT_BLANK.equals(plan)
                || InscriptionPlan.REASON_TARGET_NOT_EMPTY.equals(plan)) {
            String schoolReason = LoomInscription.schoolReason(schoolChoice);
            if (schoolReason != null) return schoolReason;
        }
        return plan;
    }

    /** Reason code to player-facing text. Codes are stable strings; the keys are shipped lang. */
    private static Component reasonMessage(String reasonCode) {
        String key = switch (reasonCode) {
            case com.otectus.arsnspells.network.LoomRequestHandler.STALE -> "ars_n_spells.spell_loom.error.stale_request";
            case com.otectus.arsnspells.network.LoomRequestHandler.REJECTED -> "ars_n_spells.spell_loom.error.request_rejected";
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
            case LoomInscription.REASON_INVALID_TARGET -> "ars_n_spells.spell_loom.error.invalid_target";
            case LoomInscription.REASON_SCHOOL_UNKNOWN -> "ars_n_spells.spell_loom.error.school_unknown";
            case LoomInscription.REASON_SCHOOL_DISABLED -> "ars_n_spells.spell_loom.error.school_disabled";
            case LoomInscription.REASON_NOT_A_CARRIER -> "ars_n_spells.spell_loom.error.not_a_carrier";
            case LoomInscription.REASON_SCHOOL_UNCHANGED -> "ars_n_spells.spell_loom.error.school_unchanged";
            default -> "ars_n_spells.spell_loom.error.failed";
        };
        return Component.translatable(key);
    }

    // ---- Rendering ----

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        VanillaGui.panel(g, x, y, imageWidth, imageHeight);

        // Vanilla-style bevel chrome under EVERY slot the menu defines — the
        // three working slots and all 36 player inventory slots alike.
        for (Slot slot : this.menu.slots) {
            VanillaGui.slot(g, x + slot.x, y + slot.y);
        }

        // Recipe-flow glyphs, centered between the slot edges they connect:
        // source inner box ends at SLOT_SOURCE_X+16, scroll starts at SLOT_SCROLL_X, etc.
        int plusCx = x + (SpellLoomMenu.SLOT_SOURCE_X + 16 + SpellLoomMenu.SLOT_SCROLL_X) / 2;
        int arrowCx = x + (SpellLoomMenu.SLOT_SCROLL_X + 16 + SpellLoomMenu.SLOT_OUTPUT_X) / 2;
        VanillaGui.centered(g, font, Component.literal("+"), plusCx, y + GLYPH_Y, VanillaGui.TEXT);
        VanillaGui.arrow(g, arrowCx - 11, y + SpellLoomMenu.RECIPE_ROW_Y);

        // Wheel-icon preview: slot chrome, nature tint, then the actual shipped
        // 16x16 icon texture the wheel will show. (9-arg blit: the 7-arg
        // overload assumes a 256x256 texture and would sample garbage.)
        VanillaGui.slot(g, x + PREVIEW_X, y + PREVIEW_Y);
        g.blit(SpellIconRegistry.INSTANCE.resolve(ICONS[iconIndex], NATURES[natureIndex]),
            x + PREVIEW_X, y + PREVIEW_Y, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, VanillaGui.TEXT, false);
        g.drawString(this.font, this.playerInventoryTitle,
            this.inventoryLabelX, this.inventoryLabelY, VanillaGui.TEXT, false);
        var lines = font.split(compactStatus(), CONTENT_W);
        for (int i = 0; i < Math.min(2, lines.size()); i++)
            g.drawString(font, lines.get(i), MARGIN, STATUS_Y + i * font.lineHeight, VanillaGui.MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1.21 AbstractContainerScreen renders the backdrop and container once.
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
        renderRegionTooltips(g, mouseX, mouseY);
    }

    /** Tooltips for the preview swatch and the three working slots while empty. */
    private void renderRegionTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        if (mouseX >= x + MARGIN && mouseX < x + MARGIN + CONTENT_W && mouseY >= y + STATUS_Y && mouseY < y + STATUS_Y + 18) {
            Component line = statusLine().copy().append("\n").append(consumptionLine()).append("\n").append(capacityLine());
            g.renderTooltip(font, font.split(line, 280), mouseX, mouseY);
            return;
        }
        if (isOver(mouseX, mouseY, x + PREVIEW_X, y + PREVIEW_Y)) {
            g.renderTooltip(this.font,
                Component.translatable("ars_n_spells.spell_loom.tooltip.preview",
                    Component.translatable("icon.ars_n_spells.background." + NATURES[natureIndex]),
                    Component.translatable(IconCatalog.label(ICONS[iconIndex]))),
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
        // Keep inventory hotkeys in the name, but let Escape close and Tab move
        // focus through the native controls (including Shift+Tab).
        if (nameField != null && nameField.isFocused() && keyCode != 256 && keyCode != 258) {
            return nameField.keyPressed(keyCode, scanCode, modifiers)
                || nameField.canConsumeInput()
                || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
