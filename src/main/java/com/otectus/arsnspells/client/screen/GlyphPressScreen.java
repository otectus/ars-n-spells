package com.otectus.arsnspells.client.screen;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridge;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.GlyphFamily;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistrar;
import com.otectus.arsnspells.glyph.ImportedGlyphTooltip;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import com.otectus.arsnspells.menu.GlyphPressMenu;
import com.otectus.arsnspells.network.GlyphPressActionPacket;
import com.otectus.arsnspells.network.GlyphPressRequestHandler;
import com.otectus.arsnspells.network.GlyphPressRevision;
import com.otectus.arsnspells.network.LoomRequestContext;
import com.otectus.arsnspells.network.PacketHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Glyph Press screen: a synchronized view of the press's server state plus one action.
 *
 * <p>Everything the press will do comes from the server (the reason, tier, costs and whether the
 * player knows the glyph); the client reads the scroll only to name and draw the spell. The result
 * preview is drawn in its own ghost frame, never in an item slot, so it cannot be mistaken for the
 * real output. The single action button reads <em>Inscribe Glyph</em>, or <em>Cancel</em> while a
 * job runs. The details panel is focusable, scrollable from the keyboard and narrated.
 */
public class GlyphPressScreen extends AbstractContainerScreen<GlyphPressMenu> {
    private static final int ICON_X = 30, ICON_Y = 22, NAME_X = 50, NAME_Y = 22, DETAIL_Y = 32;
    private static final int PREVIEW_X = 208, PREVIEW_Y = 22;
    private static final int ARROW_X = 52, ARROW_Y = 49;
    private static final int ACTION_X = 112, ACTION_Y = 46, ACTION_W = 112, ACTION_H = 20;
    private static final int GAUGE_X = 8, GAUGE_Y = 70, GAUGE_W = 216, GAUGE_H = 5;
    private static final int PANEL_X = 8, PANEL_Y = 92, PANEL_W = 216, PANEL_H = 41;
    private static final ResourceLocation FALLBACK_ICON = new ResourceLocation("ars_n_spells", "textures/item/imported_glyph/rune.png");
    private static final ResourceLocation FORM_SELF = GlyphFamily.Forms.SELF;
    private static final ResourceLocation AMPLIFY = new ResourceLocation("ars_nouveau", "glyph_amplify");
    private static final ResourceLocation DAMPEN = new ResourceLocation("ars_nouveau", "glyph_dampen");
    /** Whether a spell icon texture exists, remembered so a missing one is looked up once. */
    private final Map<ResourceLocation, Boolean> iconPresence = new HashMap<>();

    private Button actionButton;
    private Button helpButton;
    private ScrollableTextPanel details;
    private boolean help;
    private LoomRequestContext latestRequest;
    /** The server's answer to this screen's last request, shown until the inputs change. */
    private Component serverNote;
    private String serverNoteRevision;
    private ItemStack identifiedScroll = ItemStack.EMPTY;
    private ImportedGlyphDefinition identified;
    private Component spellName = Component.empty();
    private ResourceLocation spellIcon;

    public GlyphPressScreen(GlyphPressMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GlyphPressMenu.GUI_WIDTH;
        this.imageHeight = GlyphPressMenu.GUI_HEIGHT;
        this.inventoryLabelY = GlyphPressMenu.INV_TOP - 11;
        this.inventoryLabelX = GlyphPressMenu.INV_LEFT;
    }

    @Override
    protected void init() {
        super.init();
        iconPresence.clear();
        actionButton = addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.glyph_press.inscribe"), b -> act())
            .bounds(leftPos + ACTION_X, topPos + ACTION_Y, ACTION_W, ACTION_H).build());
        helpButton = addRenderableWidget(Button.builder(Component.literal("?"), b -> toggleHelp())
            .bounds(leftPos + imageWidth - 20, topPos + 4, 14, 12)
            .tooltip(Tooltip.create(Component.translatable("ars_n_spells.glyph_press.help.toggle"))).build());
        details = addRenderableWidget(new ScrollableTextPanel(font, leftPos + PANEL_X, topPos + PANEL_Y, PANEL_W, PANEL_H,
            List.of()).compact());
        refresh();
    }

    // ---- Requests ---------------------------------------------------------------------------

    private boolean processing() {
        return menu.state() == GlyphPressBlockEntity.State.PROCESSING;
    }

    private void act() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu != menu) return;
        String revision = GlyphPressRevision.of(menu);
        if (revision.isEmpty()) return;
        int action = processing() ? GlyphPressRequestHandler.ACTION_CANCEL : GlyphPressRequestHandler.ACTION_START;
        latestRequest = new LoomRequestContext(menu.containerId, menu.sessionId(), UUID.randomUUID(), revision,
            GlyphMappingSnapshot.current().digest());
        PacketHandler.sendToServer(new GlyphPressActionPacket(action, latestRequest));
    }

    /**
     * Receive a {@code GlyphPressResultPacket}. Static because the packet has no handle on the
     * open screen: it resolves the current one and drops an answer meant for another request.
     */
    public static void acceptResult(String reasonCode, LoomRequestContext request) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof GlyphPressScreen press) || mc.player == null || mc.player.containerMenu != press.menu
            || !request.equals(press.latestRequest)) return;
        press.serverNote = press.resultMessage(reasonCode);
        press.serverNoteRevision = GlyphPressRevision.of(press.menu);
        press.refresh();
    }

    /** What the server answered, or null when the synchronized state already says it. */
    private Component resultMessage(String code) {
        if (code == null || GlyphPressBlockEntity.STARTED.equals(code) || GlyphPressBlockEntity.CANCELLED.equals(code)) return null;
        for (GlyphPressPlan.Reason reason : GlyphPressPlan.Reason.values())
            if (reason.code().equals(code)) return reasonMessage(reason).copy().withStyle(ChatFormatting.DARK_RED);
        return Component.translatable("ars_n_spells.glyph_press.result." + code).withStyle(ChatFormatting.DARK_RED);
    }

    // ---- State ------------------------------------------------------------------------------

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private void refresh() {
        if (actionButton == null || details == null) return;
        identify();
        if (serverNote != null && !GlyphPressRevision.of(menu).equals(serverNoteRevision)) serverNote = null;
        boolean running = processing();
        actionButton.setMessage(Component.translatable(running ? "ars_n_spells.glyph_press.cancel" : "ars_n_spells.glyph_press.inscribe"));
        actionButton.active = running || menu.reason() == GlyphPressPlan.Reason.OK;
        actionButton.setTooltip(Tooltip.create(actionButton.active
            ? Component.translatable(running ? "ars_n_spells.glyph_press.cancel.tooltip" : "ars_n_spells.glyph_press.inscribe.tooltip")
            : reasonMessage()));
        helpButton.setTooltip(Tooltip.create(Component.translatable(help
            ? "ars_n_spells.glyph_press.help.back" : "ars_n_spells.glyph_press.help.toggle")));
        details.setParagraphs(help ? helpParagraphs() : detailParagraphs());
    }

    private void toggleHelp() {
        help = !help;
        details.scrollToTop();
        refresh();
        setFocused(details);
    }

    /** Name and draw the scroll's spell; the server alone decides what the press will do with it. */
    private void identify() {
        if (menu.slots.size() < GlyphPressBlockEntity.SLOT_COUNT) return;
        ItemStack scroll = menu.getSlot(GlyphPressBlockEntity.SLOT_SCROLL).getItem();
        if (ItemStack.matches(scroll, identifiedScroll)) return;
        identifiedScroll = scroll.copy();
        if (details != null) details.scrollToTop();
        identified = null;
        spellName = Component.empty();
        spellIcon = null;
        if (scroll.isEmpty()) return;
        IronGlyphBridge bridge = IronGlyphBridges.get();
        if (!bridge.present()) return;
        IronGlyphBridge.ScrollReading reading = bridge.readScroll(scroll);
        if (reading.kind() != IronGlyphBridge.ScrollReading.Kind.OK || reading.spell() == null) return;
        spellName = bridge.spellName(reading.spell()).orElse(Component.literal(reading.spell().toString()));
        spellIcon = bridge.icon(reading.spell());
        if (reading.level() >= 1 && reading.level() <= ImportedGlyphKey.MAX_LEVEL)
            identified = ImportedGlyphCatalog.get().byKey(new ImportedGlyphKey(reading.spell(), reading.level())).orElse(null);
    }

    private Component reasonMessage() {
        return reasonMessage(menu.reason());
    }

    private Component reasonMessage(GlyphPressPlan.Reason reason) {
        return switch (reason) {
            case NEEDS_CATALYST -> Component.translatable(reason.messageKey(), numeral(menu.tier()), menu.catalysts());
            case CAPACITY_TOO_SMALL -> Component.translatable(reason.messageKey(), number(menu.sourceCost()), number(menu.capacity()));
            default -> Component.translatable(reason.messageKey());
        };
    }

    private Component status() {
        if (menu.slots.size() < GlyphPressBlockEntity.SLOT_COUNT) return Component.translatable("ars_n_spells.glyph_press.status.unavailable");
        if (processing()) {
            return switch (menu.pause()) {
                case AWAITING_SOURCE -> Component.translatable("ars_n_spells.glyph_press.status.awaiting_source",
                    number(Math.max(0, menu.sourceCost() - menu.reserved()))).withStyle(ChatFormatting.GOLD);
                case OUTPUT_BLOCKED -> Component.translatable("ars_n_spells.glyph_press.status.output_blocked").withStyle(ChatFormatting.GOLD);
                default -> Component.translatable("ars_n_spells.glyph_press.status.engraving",
                    menu.duration() <= 0 ? 0 : menu.progress() * 100 / menu.duration()).withStyle(ChatFormatting.DARK_AQUA);
            };
        }
        if (menu.reason() == GlyphPressPlan.Reason.OK)
            return Component.translatable("ars_n_spells.glyph_press.status.ready").withStyle(ChatFormatting.DARK_GREEN);
        return reasonMessage().copy().withStyle(ChatFormatting.DARK_RED);
    }

    private List<Component> detailParagraphs() {
        List<Component> paragraphs = new ArrayList<>();

        if (identified != null) {
            paragraphs.add(Component.translatable("ars_n_spells.glyph_press.cost_summary", menu.catalysts(), number(menu.sourceCost())));
            ItemStack glyph = ImportedGlyphRegistrar.part(identified.key()).map(part -> new ItemStack(part.getGlyph())).orElse(ItemStack.EMPTY);
            Component glyphName = glyph.isEmpty() ? spellName : glyph.getHoverName();
            int tier = menu.tier() > 0 ? menu.tier() : identified.defaultTier();
            Component knowledge = Component.translatable(menu.known() == 1
                ? "ars_n_spells.glyph_press.known.short" : "ars_n_spells.glyph_press.unknown.short");
            paragraphs.add(menu.known() < 0
                ? Component.translatable("ars_n_spells.jei.glyph_press.tier", numeral(tier))
                : Component.translatable("ars_n_spells.glyph_press.line.tier", numeral(tier), knowledge));
            paragraphs.add(glyphName);
            paragraphs.add(restriction(identified));
            paragraphs.add(Component.translatable("ars_n_spells.glyph_press.forms", names(identified.family().forms().stream()
                .sorted(java.util.Comparator.comparing(id -> id.equals(FORM_SELF) ? "" : id.getPath())).toList())));
            List<ResourceLocation> augments = new ArrayList<>();
            if (identified.canAmplify()) augments.add(AMPLIFY);
            if (identified.canDampen()) augments.add(DAMPEN);
            paragraphs.add(augments.isEmpty() ? Component.translatable("ars_n_spells.glyph_press.augments.none")
                : Component.translatable("ars_n_spells.glyph_press.augments", names(augments)));
            paragraphs.add(costs());
            double mana = IronGlyphBridges.get().manaCost(identified.spell(), identified.level());
            if (Double.isFinite(mana) && mana > 0)
                paragraphs.add(Component.translatable("ars_n_spells.glyph_press.cast_estimate", number((int) Math.round(mana)))
                    .withStyle(ChatFormatting.DARK_GRAY));
            if (identified.requiresLearning())
                paragraphs.add(Component.translatable("tooltip.ars_n_spells.glyph.requires_learning").withStyle(ChatFormatting.DARK_PURPLE));
        } else if (menu.sourceCost() > 0 || menu.catalysts() > 0) {
            paragraphs.add(costs());
        } else {
            paragraphs.add(Component.translatable("ars_n_spells.glyph_press.slot.plate"));
            paragraphs.add(Component.translatable("ars_n_spells.glyph_press.slot.catalyst"));
        }
        return paragraphs;
    }

    private Component restriction(ImportedGlyphDefinition definition) {
        MutableComponent line = Component.translatable("tooltip.ars_n_spells.glyph.restriction."
            + definition.family().name().toLowerCase(Locale.ROOT)).copy();
        if (!definition.instant()) {
            int ticks = IronGlyphBridges.get().castTimeTicks(definition.spell(), definition.level());
            String seconds = ticks > 0 ? String.format(Locale.ROOT, "%.1f", ticks / 20.0) : "?";
            line.append(". ").append(Component.translatable(definition.continuous()
                ? "ars_n_spells.glyph_press.channels" : "ars_n_spells.glyph_press.charges", seconds));
        }
        return line;
    }

    private Component costs() {
        int held = menu.slots.size() > GlyphPressBlockEntity.SLOT_CATALYST
            ? menu.getSlot(GlyphPressBlockEntity.SLOT_CATALYST).getItem().getCount() : 0;
        return Component.translatable("ars_n_spells.glyph_press.costs", menu.catalysts(), held,
            number(menu.sourceCost()), number(menu.source() + menu.reserved()));
    }

    private List<Component> helpParagraphs() {
        List<Component> paragraphs = new ArrayList<>();
        for (int i = 1; i <= 4; i++) paragraphs.add(Component.translatable("ars_n_spells.glyph_press.help." + i));
        return paragraphs;
    }

    private static Component names(List<ResourceLocation> parts) {
        MutableComponent joined = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) joined.append(", ");
            AbstractSpellPart part = GlyphRegistry.getSpellPart(parts.get(i));
            joined.append(part == null ? Component.literal(parts.get(i).getPath()) : Component.literal(part.getLocaleName()));
        }
        return joined;
    }

    private static String numeral(int tier) {
        return ImportedGlyphDefinition.Numerals.roman(Math.max(1, tier));
    }

    private static String number(int value) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(value);
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(title, status());
    }

    // ---- Drawing ----------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int px = leftPos + PREVIEW_X, py = topPos + PREVIEW_Y;
        if (mouseX >= px && mouseX < px + 16 && mouseY >= py && mouseY < py + 16 && !preview().isEmpty()) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("ars_n_spells.glyph_press.preview").withStyle(ChatFormatting.GRAY));
            lines.add(preview().getHoverName());
            if (identified != null) ImportedGlyphTooltip.append(identified, lines);
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        int gx = leftPos + GAUGE_X, gy = topPos + GAUGE_Y;
        if (inside(mouseX, mouseY, gx, gy - 1, GAUGE_W, GAUGE_H + 2))
            g.renderTooltip(font, Component.translatable("ars_n_spells.glyph_press.source_gauge", number(menu.source()),
                number(menu.capacity()), number(menu.reserved())), mouseX, mouseY);
        else if (inside(mouseX, mouseY, leftPos + PANEL_X, topPos + 79, PANEL_W, 11)
                || inside(mouseX, mouseY, leftPos + ARROW_X, topPos + ARROW_Y, 22, 15))
            g.renderTooltip(font, serverNote != null ? serverNote : status(), mouseX, mouseY);
        else if (inside(mouseX, mouseY, leftPos + ICON_X, topPos + ICON_Y, PREVIEW_X - ICON_X - 4, 18)) {
            List<Component> lines = new ArrayList<>();
            if (!spellName.getString().isEmpty()) lines.add(spellName);
            if (identified != null) lines.add(Component.translatable("ars_n_spells.glyph_press.scroll_detail",
                ImportedGlyphTooltip.schoolName(identified), identified.level()));
            if (lines.isEmpty()) lines.add(menu.getSlot(0).hasItem() ? menu.getSlot(0).getItem().getHoverName()
                : Component.translatable("ars_n_spells.glyph_press.reason.no_scroll"));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        renderSlotHints(g, mouseX, mouseY);
    }

    private void renderSlotHints(GuiGraphics g, int mouseX, int mouseY) {
        String[] names = {"scroll", "plate", "catalyst", "output"};
        for (int i = 0; i < names.length; i++) {
            var slot = menu.getSlot(i);
            if (!slot.hasItem() && inside(mouseX, mouseY, leftPos + slot.x, topPos + slot.y, 16, 16))
                g.renderTooltip(font, Component.translatable("ars_n_spells.glyph_press.slot." + names[i]), mouseX, mouseY);
        }
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private ItemStack preview() {
        if (identified == null) return ItemStack.EMPTY;
        return ImportedGlyphRegistrar.part(identified.key()).map(part -> new ItemStack(part.getGlyph())).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        VanillaGui.panel(g, x, y, imageWidth, imageHeight);
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            VanillaGui.slot(g, x + slot.x, y + slot.y);
        }
        // Locked inputs: a faint wash over the three held slots while a job runs.
        if (processing()) {
            for (int i = GlyphPressBlockEntity.SLOT_SCROLL; i <= GlyphPressBlockEntity.SLOT_CATALYST && i < menu.slots.size(); i++) {
                var slot = menu.slots.get(i);
                g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0x408B5CF6);
            }
        }
        VanillaGui.progressArrow(g, x + ARROW_X, y + ARROW_Y, processing() ? menu.progress() : 0, menu.duration());
        drawSpell(g, x, y);
        drawPreview(g, x + PREVIEW_X, y + PREVIEW_Y);
        drawGauge(g, x + GAUGE_X, y + GAUGE_Y);
    }

    private void drawSpell(GuiGraphics g, int x, int y) {
        int textWidth = PREVIEW_X - NAME_X - 4;
        if (identified == null && spellIcon == null) {
            Component prompt = menu.getSlot(GlyphPressBlockEntity.SLOT_SCROLL).getItem().isEmpty()
                ? Component.translatable("ars_n_spells.glyph_press.reason.no_scroll")
                : menu.getSlot(GlyphPressBlockEntity.SLOT_SCROLL).getItem().getHoverName();
            g.drawString(font, VanillaGui.ellipsize(font, prompt.getString(), PREVIEW_X - ICON_X - 4), x + ICON_X, y + NAME_Y + 4,
                VanillaGui.MUTED, false);
            return;
        }
        ResourceLocation icon = spellIcon != null && iconPresent(spellIcon) ? spellIcon : FALLBACK_ICON;
        g.blit(icon, x + ICON_X, y + ICON_Y, 0, 0, 16, 16, 16, 16);
        g.drawString(font, VanillaGui.ellipsize(font, spellName.getString(), textWidth), x + NAME_X, y + NAME_Y, VanillaGui.TEXT, false);
        if (identified != null) {
            Component detail = Component.translatable("ars_n_spells.glyph_press.scroll_detail",
                ImportedGlyphTooltip.schoolName(identified), identified.level());
            g.drawString(font, VanillaGui.ellipsize(font, detail.getString(), textWidth), x + NAME_X, y + DETAIL_Y, VanillaGui.MUTED, false);
        }
    }

    /** The would-be result, in its own dashed frame and washed out, so it never reads as a real output slot. */
    private void drawPreview(GuiGraphics g, int x, int y) {
        for (int i = 0; i < 18; i += 2) {
            g.fill(x - 1 + i, y - 1, x + i, y, 0xFF8B8B8B);
            g.fill(x - 1 + i, y + 16, x + i, y + 17, 0xFF8B8B8B);
            g.fill(x - 1, y - 1 + i, x, y + i, 0xFF8B8B8B);
            g.fill(x + 16, y - 1 + i, x + 17, y + i, 0xFF8B8B8B);
        }
        ItemStack preview = preview();
        if (preview.isEmpty()) return;
        g.renderFakeItem(preview, x, y);
        g.fill(RenderType.guiGhostRecipeOverlay(), x, y, x + 16, y + 16, 0x80C6C6C6);
    }

    private void drawGauge(GuiGraphics g, int x, int y) {
        VanillaGui.inset(g, x, y, GAUGE_W, GAUGE_H);
        int capacity = Math.max(1, menu.capacity());
        int inner = GAUGE_W - 2;
        int stored = Math.min(inner, (int) ((long) menu.source() * inner / capacity));
        int reserved = Math.min(inner - stored, (int) ((long) menu.reserved() * inner / capacity));
        if (stored > 0) g.fill(x + 1, y + 1, x + 1 + stored, y + GAUGE_H - 1, 0xFFB57EDC);
        if (reserved > 0) g.fill(x + 1 + stored, y + 1, x + 1 + stored + reserved, y + GAUGE_H - 1, 0xFF6A3D9A);
        int needed = menu.sourceCost();
        if (needed > 0 && needed <= capacity) {
            int mark = x + 1 + (int) ((long) needed * inner / capacity);
            g.fill(mark, y, mark + 1, y + GAUGE_H, 0xFF000000);
        }
    }

    private boolean iconPresent(ResourceLocation icon) {
        return iconPresence.computeIfAbsent(icon, id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, VanillaGui.ellipsize(font, title.getString(), imageWidth - 36), titleLabelX, titleLabelY, VanillaGui.TEXT, false);
        Component shown = serverNote != null ? serverNote : status();
        int color = serverNote != null ? 0xA02020 : processing() ? 0x276778
            : menu.reason() == GlyphPressPlan.Reason.OK ? 0x2E6C32 : 0xA02020;
        g.drawString(font, VanillaGui.ellipsize(font, shown.getString(), PANEL_W), PANEL_X, 80, color, false);
        g.drawString(font, VanillaGui.ellipsize(font, playerInventoryTitle.getString(), 162), inventoryLabelX, inventoryLabelY, VanillaGui.TEXT, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        latestRequest = null;
        super.onClose();
    }
}
