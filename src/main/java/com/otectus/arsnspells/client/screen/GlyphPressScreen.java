package com.otectus.arsnspells.client.screen;

import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.otectus.arsnspells.block.GlyphPressBlockEntity;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.VariantBehavior;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.recipe.GlyphPressPlan;
import com.otectus.arsnspells.glyph.recipe.ScrollReading;
import com.otectus.arsnspells.menu.GlyphPressMenu;
import com.otectus.arsnspells.network.GlyphPressActionPayload;
import com.otectus.arsnspells.network.GlyphPressRequestHandler;
import com.otectus.arsnspells.network.LoomRequestContext;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Glyph Press screen. Every decision shown here is the server's: the status line, the "already
 * known" flag, progress and Source arrive through the menu's synchronized data, computed for this
 * viewer. The screen only describes the scroll in the slot (name, level, school, icon, the glyph it
 * becomes, its target restriction and augments) from the same catalog and behaviour the server uses.
 *
 * <p>The result preview is drawn ghosted in its own dashed frame above the real output slot and is
 * never an item in any slot. One action, Inscribe Glyph, starts a job; Cancel ends a running one and
 * returns its inputs. The {@code ?} button opens a short help panel.
 */
public class GlyphPressScreen extends AbstractContainerScreen<GlyphPressMenu> {
    private static final int MARGIN = 8, CONTENT_W = 216;
    private static final int ICON_X = 30, ICON_Y = 22, NAME_X = 50, NAME_Y = 22, DETAIL_Y = 32;
    private static final int PREVIEW_X = 208, PREVIEW_Y = 22;
    private static final int ARROW_X = 52, ARROW_Y = 49;
    private static final int GAUGE_X = 8, GAUGE_Y = 70, GAUGE_W = 216, GAUGE_H = 5;
    private static final int PANEL_X = 8, PANEL_Y = 92, PANEL_W = 216, PANEL_H = 41;
    private static final int BUTTON_X = 112, BUTTON_Y = 46, BUTTON_W = 112, BUTTON_H = 20;
    private static final int SOURCE_COLOR = 0xFFB57EDC, RESERVED_COLOR = 0xFF6A3D9A;
    private final Map<ResourceLocation, Boolean> icons = new HashMap<>();
    private static final ResourceLocation FALLBACK_ICON =
        ResourceLocation.fromNamespaceAndPath("ars_n_spells", "textures/item/iron_glyph/fallback_icon.png");

    private Button inscribeButton;
    private Button cancelButton;
    private Button helpButton;
    private boolean help;
    /** Both details and help wrap and scroll inside the same panel, without covering slots. */
    private ScrollableTextPanel details;
    private ItemStack lastScroll = ItemStack.EMPTY;
    @Nullable private LoomRequestContext latestRequest;
    @Nullable private Component lastResult;
    private long lastResultAt;
    private Component status = Component.empty();

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
        icons.clear();
        int x = leftPos, y = topPos;
        inscribeButton = addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.glyph_press.inscribe"),
                b -> send(GlyphPressActionPayload.ACTION_INSCRIBE))
            .bounds(x + BUTTON_X, y + BUTTON_Y, BUTTON_W, BUTTON_H)
            .tooltip(Tooltip.create(Component.translatable("ars_n_spells.glyph_press.inscribe.tooltip")))
            .build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("ars_n_spells.glyph_press.cancel"),
                b -> send(GlyphPressActionPayload.ACTION_CANCEL))
            .bounds(x + BUTTON_X, y + BUTTON_Y, BUTTON_W, BUTTON_H)
            .tooltip(Tooltip.create(Component.translatable("ars_n_spells.glyph_press.cancel.tooltip")))
            .build());
        helpButton = addRenderableWidget(Button.builder(Component.literal("?"), b -> toggleHelp())
            .bounds(x + imageWidth - 22, y + 4, 14, 12)
            .tooltip(Tooltip.create(Component.translatable("ars_n_spells.glyph_press.help.tooltip")))
            .build());
        details = addRenderableWidget(new ScrollableTextPanel(font, x + PANEL_X, y + PANEL_Y,
            PANEL_W, PANEL_H, List.of()).compact());
        refresh();
    }

    private void toggleHelp() {
        help = !help;
        details.scrollToTop();
        refresh();
        setFocused(details);
    }

    private List<Component> helpParagraphs() {
        return List.of(Component.translatable("ars_n_spells.glyph_press.help.what"),
            Component.translatable("ars_n_spells.glyph_press.help.learn"),
            Component.translatable("ars_n_spells.glyph_press.help.difference"),
            Component.translatable("ars_n_spells.glyph_press.help.example"));
    }

    private void send(int action) {
        LoomRequestContext context = new LoomRequestContext(menu.containerId, menu.sessionId(), UUID.randomUUID(),
            GlyphPressRequestHandler.revision(menu), GlyphBehavior.current().digest());
        latestRequest = context;
        PacketDistributor.sendToServer(new GlyphPressActionPayload(action, context));
    }

    /** A server reply; only the latest request of the open screen is shown. */
    public static void acceptResult(String outcomeKey, LoomRequestContext context) {
        if (Minecraft.getInstance().screen instanceof GlyphPressScreen screen && context.equals(screen.latestRequest)) {
            screen.lastResult = Component.translatable(outcomeKey);
            screen.lastResultAt = Util.getMillis();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private boolean processing() {
        return menu.state() == GlyphPressBlockEntity.State.PROCESSING;
    }

    private void refresh() {
        boolean running = processing();
        inscribeButton.active = !running && menu.reason() == GlyphPressPlan.Reason.OK;
        inscribeButton.visible = !running;
        cancelButton.active = running;
        cancelButton.visible = running;
        status = statusLine();
        inscribeButton.setTooltip(Tooltip.create(inscribeButton.active
            ? Component.translatable("ars_n_spells.glyph_press.inscribe.tooltip") : status));
        helpButton.setTooltip(Tooltip.create(Component.translatable(help
            ? "ars_n_spells.glyph_press.help.back" : "ars_n_spells.glyph_press.help.tooltip")));
        ItemStack scroll = menu.getSlot(GlyphPressBlockEntity.SLOT_SCROLL).getItem();
        if (!ItemStack.matches(lastScroll, scroll)) {
            lastScroll = scroll.copy();
            details.scrollToTop();
            lastResult = null;
        }
        details.setParagraphs(help ? helpParagraphs() : detailLines(view()));
    }

    // ---- What the scroll would become ----

    private record View(@Nullable CatalogEntry entry, @Nullable ImportedGlyphKey key, @Nullable VariantBehavior behavior,
                        @Nullable ImportedIronEffect effect, ScrollReading reading) {
    }

    private View view() {
        ScrollReading reading = IronGlyphBridges.get().readScroll(menu.getSlot(GlyphPressBlockEntity.SLOT_SCROLL).getItem());
        if (reading.kind() != ScrollReading.Kind.SPELL) return new View(null, null, null, null, reading);
        CatalogEntry entry = ImportedGlyphCatalog.get().entry(reading.spellId());
        if (entry == null || reading.level() < entry.minLevel() || reading.level() > entry.maxLevel()) {
            return new View(entry, null, null, null, reading);
        }
        ImportedGlyphKey key = new ImportedGlyphKey(entry.spellId(), reading.level());
        return new View(entry, key, GlyphBehavior.current().variant(key), ImportedGlyphRegistration.effect(key), reading);
    }

    // ---- Rendering ----

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        VanillaGui.panel(g, x, y, imageWidth, imageHeight);
        for (var slot : menu.slots) VanillaGui.slot(g, x + slot.x, y + slot.y);
        if (processing()) {
            for (int i = 0; i < GlyphPressBlockEntity.SLOT_OUTPUT; i++) {
                var slot = menu.getSlot(i);
                g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0x408B5CF6);
            }
        }
        VanillaGui.progressArrow(g, x + ARROW_X, y + ARROW_Y,
            processing() ? menu.wide(GlyphPressMenu.D_PROGRESS) : 0, menu.wide(GlyphPressMenu.D_TICKS));
        drawGauge(g, x + GAUGE_X, y + GAUGE_Y);
        View view = view();
        drawSpell(g, view, x, y);
        drawPreview(g, view, x + PREVIEW_X, y + PREVIEW_Y);
    }

    private void drawGauge(GuiGraphics g, int x, int y) {
        VanillaGui.inset(g, x, y, GAUGE_W, GAUGE_H);
        int capacity = Math.max(1, menu.wide(GlyphPressMenu.D_CAPACITY));
        int inner = GAUGE_W - 2;
        int free = Math.min(inner, (int) ((long) menu.wide(GlyphPressMenu.D_FREE) * inner / capacity));
        int reserved = Math.min(inner - free, (int) ((long) menu.wide(GlyphPressMenu.D_RESERVED) * inner / capacity));
        if (free > 0) g.fill(x + 1, y + 1, x + 1 + free, y + GAUGE_H - 1, SOURCE_COLOR);
        if (reserved > 0) g.fill(x + 1 + free, y + 1, x + 1 + free + reserved, y + GAUGE_H - 1, RESERVED_COLOR);
        int need = menu.wide(GlyphPressMenu.D_NEED);
        if (need > 0 && need <= capacity) {
            int mark = x + 1 + (int) ((long) need * inner / capacity);
            g.fill(mark, y, mark + 1, y + GAUGE_H, 0xFF000000);
        }
    }

    private static Component spellName(CatalogEntry entry) {
        ResourceLocation id = entry.spellId();
        return Component.translatableWithFallback("spell." + id.getNamespace() + "." + id.getPath(), entry.name());
    }

    private static Component schoolName(CatalogEntry entry) {
        return Component.translatableWithFallback("school." + entry.school().getNamespace() + "." + entry.school().getPath(),
            entry.school().getPath());
    }

    private void drawSpell(GuiGraphics g, View view, int x, int y) {
        if (view.entry() == null) {
            ItemStack scroll = menu.getSlot(GlyphPressBlockEntity.SLOT_SCROLL).getItem();
            Component prompt = scroll.isEmpty() ? Component.translatable("ars_n_spells.glyph_press.reason.insert_scroll") : scroll.getHoverName();
            g.drawString(font, VanillaGui.ellipsize(font, prompt.getString(), PREVIEW_X - ICON_X - 4),
                x + ICON_X, y + NAME_Y + 4, VanillaGui.MUTED, false);
            return;
        }
        ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(view.entry().spellId().getNamespace(),
            "textures/gui/spell_icons/" + view.entry().spellId().getPath() + ".png");
        g.blit(iconExists(icon) ? icon : FALLBACK_ICON, x + ICON_X, y + ICON_Y, 0, 0, 16, 16, 16, 16);
        int width = PREVIEW_X - NAME_X - 4;
        g.drawString(font, VanillaGui.ellipsize(font, spellName(view.entry()).getString(), width), x + NAME_X, y + NAME_Y, VanillaGui.TEXT, false);
        Component detail = Component.translatable("ars_n_spells.glyph_press.scroll_detail", schoolName(view.entry()), view.reading().level());
        g.drawString(font, VanillaGui.ellipsize(font, detail.getString(), width), x + NAME_X, y + DETAIL_Y, VanillaGui.MUTED, false);
    }

    private void drawPreview(GuiGraphics g, View view, int x, int y) {
        for (int i = 0; i < 18; i += 2) {
            g.fill(x - 1 + i, y - 1, x + i, y, 0xFF8B8B8B);
            g.fill(x - 1 + i, y + 16, x + i, y + 17, 0xFF8B8B8B);
            g.fill(x - 1, y - 1 + i, x, y + i, 0xFF8B8B8B);
            g.fill(x + 16, y - 1 + i, x + 17, y + i, 0xFF8B8B8B);
        }
        if (view.effect() == null) return;
        g.renderFakeItem(new ItemStack(view.effect().getGlyph()), x, y);
        g.fill(RenderType.guiGhostRecipeOverlay(), x, y, x + 16, y + 16, 0x80C6C6C6);
    }

    private Component shownStatus() {
        return lastResult != null && Util.getMillis() - lastResultAt < 4000 ? lastResult : status;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, VanillaGui.ellipsize(font, title.getString(), imageWidth - 36), titleLabelX, titleLabelY, VanillaGui.TEXT, false);
        g.drawString(font, VanillaGui.ellipsize(font, playerInventoryTitle.getString(), 162), inventoryLabelX, inventoryLabelY, VanillaGui.TEXT, false);
        int color = processing() ? 0x276778 : menu.reason() == GlyphPressPlan.Reason.OK ? 0x2E6C32 : 0xA02020;
        g.drawString(font, VanillaGui.ellipsize(font, shownStatus().getString(), CONTENT_W), MARGIN, 80, color, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        renderExtraTooltips(g, mouseX, mouseY);
    }

    private void renderExtraTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        if (inside(mouseX, mouseY, x + GAUGE_X, y + GAUGE_Y - 1, GAUGE_W, GAUGE_H + 2)) {
            g.renderTooltip(font, Component.translatable("ars_n_spells.glyph_press.source",
                number(menu.wide(GlyphPressMenu.D_FREE)), number(menu.wide(GlyphPressMenu.D_CAPACITY)),
                number(menu.wide(GlyphPressMenu.D_RESERVED)), number(menu.wide(GlyphPressMenu.D_NEED))), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, x + PREVIEW_X - 1, y + PREVIEW_Y - 1, 18, 18)) {
            View view = view();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("ars_n_spells.glyph_press.preview").withStyle(ChatFormatting.GRAY));
            if (view.effect() != null) lines.add(new ItemStack(view.effect().getGlyph()).getHoverName());
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (inside(mouseX, mouseY, x + MARGIN, y + 79, CONTENT_W, 11)
                || inside(mouseX, mouseY, x + ARROW_X, y + ARROW_Y, 22, 15)) {
            g.renderTooltip(font, shownStatus(), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, x + ICON_X, y + ICON_Y, PREVIEW_X - ICON_X - 4, 18)) {
            View view = view();
            Component full = view.entry() != null ? spellLine(view.entry(), view.reading().level())
                : menu.getSlot(0).hasItem() ? menu.getSlot(0).getItem().getHoverName()
                : Component.translatable("ars_n_spells.glyph_press.reason.insert_scroll");
            g.renderTooltip(font, full, mouseX, mouseY);
        }
        String[] names = {"scroll", "plate", "catalyst", "output"};
        for (int i = 0; i < names.length; i++) {
            var slot = menu.getSlot(i);
            if (!slot.hasItem() && inside(mouseX, mouseY, x + slot.x, y + slot.y, 16, 16))
                g.renderTooltip(font, Component.translatable("ars_n_spells.glyph_press.slot." + names[i]), mouseX, mouseY);
        }
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Five short lines: spell, glyph, tier and knowledge, use and augments, and the conversion's terms. */
    private List<Component> infoLines(View view) {
        List<Component> lines = new ArrayList<>();
        if (view.entry() == null) {
            lines.add(Component.translatable("ars_n_spells.glyph_press.hint.scroll"));
            lines.add(Component.translatable("ars_n_spells.glyph_press.hint.plate"));
            lines.add(Component.translatable("ars_n_spells.glyph_press.hint.catalyst"));
            lines.add(Component.translatable("ars_n_spells.glyph_press.hint.source"));
            return lines;
        }
        CatalogEntry entry = view.entry();
        lines.add(spellLine(entry, view.reading().level()));
        if (view.effect() == null) {
            lines.add(Component.translatable("ars_n_spells.glyph_press.line.no_glyph"));
            return lines;
        }
        VariantBehavior behavior = view.behavior();
        boolean known = menu.value(GlyphPressMenu.D_KNOWN) == 1;
        lines.add(Component.translatable("ars_n_spells.glyph_press.line.glyph", new ItemStack(view.effect().getGlyph()).getHoverName()));
        lines.add(Component.translatable("ars_n_spells.glyph_press.line.tier", roman(behavior.tier()),
            Component.translatable(known ? "ars_n_spells.glyph_press.known" : "ars_n_spells.glyph_press.unknown")));
        lines.add(Component.translatable("ars_n_spells.glyph_press.line.use", restriction(entry, view.effect(), true), augments(view.effect())));
        lines.add(Component.translatable("ars_n_spells.glyph_press.line.terms", behavior.pressGems(), catalystWord(behavior),
            number(behavior.pressSource()), seconds(behavior.pressTicks())));
        return lines;
    }

    /** Conversion terms stay first; longer restrictions and augments wrap and scroll below them. */
    private List<Component> detailLines(View view) {
        if (view.entry() == null || view.effect() == null) return infoLines(view);
        VariantBehavior behavior = view.behavior();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("ars_n_spells.glyph_press.line.terms", behavior.pressGems(), catalystWord(behavior),
            number(behavior.pressSource()), seconds(behavior.pressTicks())));
        lines.add(Component.translatable("ars_n_spells.glyph_press.line.tier", roman(behavior.tier()),
            Component.translatable(menu.value(GlyphPressMenu.D_KNOWN) == 1
                ? "ars_n_spells.glyph_press.known" : "ars_n_spells.glyph_press.unknown")));
        lines.add(new ItemStack(view.effect().getGlyph()).getHoverName());
        lines.add(restriction(view.entry(), view.effect(), false));
        lines.add(Component.translatable("ars_n_spells.glyph_press.augments", augments(view.effect())));
        lines.add(Component.translatable("ars_n_spells.glyph_press.terms.tooltip", behavior.pressGems(), behavior.catalyst(),
            number(behavior.pressSource()), seconds(behavior.pressTicks())));
        return lines;
    }

    private static Component spellLine(CatalogEntry entry, int level) {
        ResourceLocation id = entry.spellId();
        return Component.translatable("ars_n_spells.glyph_press.line.spell",
            Component.translatableWithFallback("spell." + id.getNamespace() + "." + id.getPath(), entry.name()), roman(level),
            Component.translatableWithFallback("school." + entry.school().getNamespace() + "." + entry.school().getPath(),
                entry.school().getPath()));
    }

    private static Component augments(ImportedIronEffect effect) {
        List<AbstractAugment> list = effect.compatibleAugmentList();
        if (list.isEmpty()) return Component.translatable("ars_n_spells.glyph_press.no_augments");
        MutableComponent text = Component.empty();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) text.append(", ");
            text.append(Component.translatable(list.get(i).getLocalizationKey()));
        }
        return text;
    }

    private static Component catalystWord(VariantBehavior behavior) {
        if (!VariantBehavior.DEFAULT_CATALYST.equals(behavior.catalyst())) return Component.translatable("ars_n_spells.glyph_press.catalyst.items");
        return Component.translatable(behavior.pressGems() == 1 ? "ars_n_spells.glyph_press.catalyst.gem" : "ars_n_spells.glyph_press.catalyst.gems");
    }

    private static String seconds(int ticks) {
        return ticks % 20 == 0 ? Integer.toString(ticks / 20) : String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    /** The glyph's target restriction, plus how it casts when it charges or channels; {@code compact} for the screen line. */
    private static Component restriction(CatalogEntry entry, ImportedIronEffect effect, boolean compact) {
        String key = GlyphBehavior.current().selfOnly(entry.spellId()) ? "ars_n_spells.glyph.restriction.self_only" : effect.restrictionKey();
        if (compact) key = key.replace(".restriction.", ".restriction_short.");
        MutableComponent text = Component.translatable(key).copy();
        String mode = entry.channelled() ? "channel" : entry.charged() ? "charged" : null;
        if (mode != null) {
            text.append(" · ").append(Component.translatable((compact ? "ars_n_spells.glyph.restriction_short." : "ars_n_spells.glyph.restriction.") + mode));
        }
        return text;
    }

    private Component statusLine() {
        GlyphPressPlan.Reason reason = menu.reason();
        if (menu.state() == GlyphPressBlockEntity.State.PROCESSING) {
            return switch (menu.phase()) {
                case WAITING_SOURCE -> Component.translatable("ars_n_spells.glyph_press.status.waiting_source",
                    number(menu.wide(GlyphPressMenu.D_FREE)), number(menu.wide(GlyphPressMenu.D_NEED)));
                case OUTPUT_BLOCKED -> Component.translatable(GlyphPressPlan.Reason.OUTPUT_FULL.translationKey());
                default -> Component.translatable("ars_n_spells.glyph_press.status.inscribing",
                    percent(menu.wide(GlyphPressMenu.D_PROGRESS), menu.wide(GlyphPressMenu.D_TICKS)));
            };
        }
        return switch (reason) {
            case OK -> Component.translatable("ars_n_spells.glyph_press.status.ready");
            case NEEDS_CATALYST -> Component.translatable(reason.translationKey(), roman(menu.value(GlyphPressMenu.D_TIER)),
                menu.value(GlyphPressMenu.D_GEMS));
            case SOURCE_OVER_CAPACITY -> Component.translatable(reason.translationKey(), number(menu.wide(GlyphPressMenu.D_NEED)),
                number(menu.wide(GlyphPressMenu.D_CAPACITY)));
            default -> Component.translatable(reason.translationKey());
        };
    }

    @Override
    protected void updateNarrationState(NarrationElementOutput output) {
        super.updateNarrationState(output);
        output.add(NarratedElementType.HINT, status);
    }

    private boolean iconExists(ResourceLocation icon) {
        return icons.computeIfAbsent(icon, id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
    }

    private static String number(int value) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(value);
    }

    private static String percent(int progress, int ticks) {
        return ticks <= 0 ? "0%" : Math.min(100, Math.round(100f * progress / ticks)) + "%";
    }

    static String roman(int n) {
        return com.otectus.arsnspells.glyph.ImportedIronEffect.roman(n);
    }
}
