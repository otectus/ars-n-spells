package com.otectus.arsnspells.client.glyph;

import com.otectus.arsnspells.client.glyph.BrowserModel.Filters;
import com.otectus.arsnspells.client.glyph.BrowserModel.Row;
import com.otectus.arsnspells.client.glyph.BrowserModel.Variant;
import com.otectus.arsnspells.client.glyph.BrowserModel.View;
import com.otectus.arsnspells.config.AnsClientConfig;
import com.otectus.arsnspells.config.DisplayMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static com.otectus.arsnspells.client.glyph.BrowserController.tr;
import static com.otectus.arsnspells.client.glyph.BrowserController.amount;

/** A modal GUI layer over the existing editor. Only the current page has widgets or draft validations. */
public final class GlyphLibraryScreen extends Screen {
    private final BrowserController controller;
    private final Map<String, String> selections = new HashMap<>();
    private final Map<String, AbstractWidget> widgets = new LinkedHashMap<>();
    private Filters filters = Filters.DEFAULT;
    private List<Row> rows = List.of();
    private String detail, focusKey;
    private boolean expandDormant;
    private int page, levelPage, seenRevision;
    private int x, y, w, h;
    private EditBox search;
    private Component message = Component.empty(), heading = Component.empty(), summary = Component.empty();
    private int messageY;
    private boolean rebuilding;

    GlyphLibraryScreen(BrowserController controller) { super(tr("title")); this.controller = controller; }
    void resetSession() {
        selections.clear();
        filters = Filters.DEFAULT;
        detail = focusKey = null;
        page = levelPage = 0;
        expandDormant = false;
    }
    void searchFromBook(String query) {
        filters = new Filters(View.LEARNED, query, "", "", true);
        detail = null;
        page = 0;
    }

    @Override protected void init() {
        w = Math.min(width - 10, 374);
        // At roomy scales the panel sits above Ars's composition row (center + 51).
        h = height >= 310 ? Math.min(252, height / 2 + 38) : Math.min(height - 12, 252);
        x = (width - w) / 2;
        y = Math.max(4, Math.min((height - h) / 3, height >= 310 ? height / 2 + 45 - h : height));
        rebuild();
    }

    @Override public void tick() {
        controller.tick();
        if (AnsClientConfig.mode() == DisplayMode.HIDDEN || !controller.host.browserOwnsBook()) {
            returnToEditor();
            return;
        }
        if (seenRevision != controller.revision()) rebuild();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        if (detail != null) {
            String old = detail;
            detail = null;
            focusKey = "row/" + old;
            rebuild();
        } else returnToEditor();
    }

    private void returnToEditor() {
        BrowserPlatform.pop();
        if (!(minecraft.screen instanceof ArsGlyphEditorHost current) || current != controller.host) return;
        var editor = minecraft.screen;
        if (AnsClientConfig.mode() != DisplayMode.HIDDEN && editor.getFocused() instanceof AbstractWidget focused
                && focused.visible && focused.active) return;
        editor.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.visible && widget.active).findFirst().ifPresent(editor::setFocused);
    }

    private void rebuild() {
        if (rebuilding) return;
        rebuilding = true;
        try {
            String focus = focusKey;
            if (focus == null) for (var e : widgets.entrySet()) if (e.getValue() == getFocused()) { focus = e.getKey(); break; }
            int cursor = search == null ? -1 : search.getCursorPosition();
            focusKey = null;
            setFocused(null);
            clearWidgets();
            widgets.clear();
            search = null;
            seenRevision = controller.revision();
            message = Component.empty();
            rows = controller.rows(filters);
            if (detail == null) showList();
            else showDetails();
            AbstractWidget next = widgets.get(focus);
            if (next == null || !next.active) {
                next = widgets.getOrDefault("row/" + (rows.isEmpty() ? "" : rows.get(Math.min(page * perPage(), rows.size()-1)).spell().spellId()),
                    widgets.get(detail == null ? "search" : "back"));
            }
            if (next != null) setFocused(next);
            if (search != null && next == search && cursor >= 0) search.setCursorPosition(Math.min(cursor, search.getValue().length()));
        } finally { rebuilding = false; }
    }

    private int perPage() { return Math.max(1, (h - 125) / 28); }
    private int bottom() { return y + h - 26; }

    private void showList() {
        heading = controller.host.browserCreative() ? tr("creative_title") : title;
        summary = tr("counts", amount("spells", rows.size()), amount("level_count", rows.stream().mapToInt(row -> row.choices().size()).sum()));
        int left = x + 8, inner = w - 16;
        search = new EditBox(font, left, y + 25, inner - 72, 18, tr("search"));
        search.setMaxLength(256);
        search.setHint(tr("search"));
        search.setValue(filters.query());
        search.setResponder(value -> {
            if (rebuilding) return;
            filters = new Filters(filters.view(), value, filters.source(), filters.school(), filters.fits());
            page = 0;
            focusKey = "search";
            rebuild();
        });
        widgets.put("search", addRenderableWidget(search));
        button("reset", left + inner - 70, y + 24, 70, tr("reset_filters"), () -> { filters = Filters.DEFAULT; page = 0; rebuild(); });
        int col = (inner - 4) / 3;
        for (View view : View.values()) {
            var b = button("tab/" + view, left + view.ordinal() * (col + 2), y + 46, col, tr(view.name().toLowerCase(Locale.ROOT)), () -> {
                filters = new Filters(view, filters.query(), filters.source(), filters.school(), filters.fits());
                page = 0; rebuild();
            });
            b.active = filters.view() != view;
        }
        button("source", left, y + 68, col, filters.source().isEmpty() ? tr("sources") : Component.literal(BrowserPlatform.sourceName(filters.source())), () -> {
            var options = controller.model().groups().stream().map(row -> row.spell().namespace()).distinct().sorted().toList();
            filters = new Filters(filters.view(), filters.query(), cycle(options, filters.source()), filters.school(), filters.fits()); page = 0; rebuild();
        });
        button("school", left + col + 2, y + 68, col, filters.school().isEmpty() ? tr("schools") : school(filters.school()), () -> {
            var options = controller.model().groups().stream().flatMap(row -> row.choices().stream()).map(Variant::school).filter(s -> !s.isEmpty()).distinct().sorted().toList();
            filters = new Filters(filters.view(), filters.query(), filters.source(), cycle(options, filters.school()), filters.fits()); page = 0; rebuild();
        });
        button("fits", left + 2 * (col + 2), y + 68, col, tr(filters.fits() ? "fits_on" : "fits_off"), () -> {
            filters = new Filters(filters.view(), filters.query(), filters.source(), filters.school(), !filters.fits()); page = 0; rebuild();
        });
        int capacity = perPage();
        page = BrowserModel.clampPage(page, rows.size(), capacity);
        int first = page * capacity;
        for (int i = first; i < Math.min(first + capacity, rows.size()); i++) {
            addRow(rows.get(i), left, y + 91 + (i - first) * 28, inner);
        }
        if (rows.isEmpty()) {
            messageY = y + 94;
            boolean archive = !controller.pending() && filters.view() == View.LEARNED && controller.model().groups().stream()
                .anyMatch(group -> group.choices().stream().anyMatch(v -> !v.available()));
            message = tr(controller.pending() ? "waiting" : archive ? "empty_learned" :
                controller.model().groups().isEmpty() ? "intro" : filters.view() == View.FAVORITES ? "empty_favorites" : "no_matches");
            if (archive) button("archive", left, Math.min(bottom() - 22, messageY + 24), inner, tr("open_dormant"), () -> {
                filters = new Filters(View.DORMANT, "", "", "", true); page = 0; rebuild();
            });
        }
        nav(page, rows.size(), capacity, delta -> { page += delta; rebuild(); });
        button("settings", left + 68, bottom(), inner - 138, tr("settings"), () -> BrowserPlatform.push(new PersonalDisplayScreen()));
        button("close", left + inner - 68, bottom(), 68, Component.translatable("gui.done"), this::onClose);
    }

    private void addRow(Row row, int left, int top, int inner) {
        Variant spell = row.spell();
        // Only visible spells need contextual validation. Stop when there is more than one eligible choice.
        List<Variant> eligible = filters.view() == View.DORMANT ? List.of() : row.choices().stream()
            .filter(v -> controller.error(v.partId()) == null).limit(2).toList();
        String remembered = selections.get(spell.spellId());
        boolean direct = eligible.size() == 1 && (remembered == null || remembered.equals(eligible.get(0).partId()));
        int directWidth = direct ? Math.min(106, inner / 3) : 0;
        int mainWidth = inner - directWidth - 26;
        Component subtitle = Component.literal(spell.source()).append(" · ").append(amount(controller.host.browserCreative() ? "level_count" : "levels", row.choices().size()));
        String schoolId = spell.school();
        if (!schoolId.isEmpty()) subtitle = subtitle.copy().append(" · ").append(school(schoolId));
        Component narration = Component.literal(spell.name()).append(". ").append(subtitle);
        var rowButton = new SpellRow(left, top, mainWidth, spell, subtitle, narration, () -> inspect(row));
        rowButton.setTooltip(Tooltip.create(narration.copy().append("\n" + spell.spellId())));
        widgets.put("row/" + spell.spellId(), addRenderableWidget(rowButton));
        if (direct) {
            var exact = eligible.get(0);
            var add = button("quick/" + spell.spellId(), left + mainWidth + 2, top + 3, directWidth - 2,
                tr("add", exact.levelName()), () -> add(exact.partId()));
            add.setTooltip(Tooltip.create(tr("add", exact.levelName())));
        }
        favorite(spell, left + inner - 23, top + 3);
    }

    private void inspect(Row row) {
        detail = row.spell().spellId();
        levelPage = 0;
        expandDormant = filters.view() == View.DORMANT;
        if (!selections.containsKey(detail)) {
            var chosen = row.choices().stream().filter(v -> controller.error(v.partId()) == null).findFirst().orElse(row.choices().get(0));
            selections.put(detail, chosen.partId());
        } else if (!filters.query().isBlank() && row.choices().size() == 1) {
            // An explicit click on an exact level search result selects that result.
            selections.put(detail, row.choices().get(0).partId());
        }
        focusKey = "choice/" + selections.get(detail);
        rebuild();
    }

    private void showDetails() {
        Row all = controller.model().group(detail);
        String selected = selections.get(detail);
        Variant current = selected == null ? null : controller.model().variant(selected);
        int left = x + 8, inner = w - 16;
        heading = all == null ? Component.literal(detail) : Component.literal(all.spell().name());
        summary = all == null ? Component.empty() : Component.literal(all.spell().source());
        button("back", left, y + 25, 64, tr("back"), this::onClose);
        int dormant = all == null ? 0 : (int) all.choices().stream().filter(v -> !v.available()).count();
        if (dormant > 0 && filters.view() != View.DORMANT)
            button("expand", left + 66, y + 25, inner - 92, tr(expandDormant ? "collapse" : "expand", dormant),
                () -> { expandDormant = !expandDormant; levelPage = 0; rebuild(); });
        if (all != null) favorite(all.spell(), left + inner - 23, y + 25);

        var choices = new ArrayList<Variant>();
        if (all != null) for (var v : all.choices()) {
            if (v.available() && filters.view() != View.DORMANT && (!filters.fits() || v.tier() <= controller.tier())
                    || !v.available() && expandDormant) choices.add(v);
        }
        int capacity = Math.max(1, (h - 127) / 22);
        levelPage = BrowserModel.clampPage(levelPage, choices.size(), capacity);
        int start = levelPage * capacity;
        for (int i = start; i < Math.min(start + capacity, choices.size()); i++) {
            Variant v = choices.get(i);
            Component why = controller.error(v.partId());
            Component label = Component.literal(v.partId().equals(selected) ? "✓ " : "").append(v.levelName());
            if (why != null) label = label.copy().append(" · ").append(v.available() ? tr("restricted") : tr("dormant"));
            var b = button("choice/" + v.partId(), left, y + 49 + (i - start) * 22, inner, label, () -> {
                selections.put(detail, v.partId()); rebuild();
            });
            Component tip = Component.literal(v.source() + " · " + v.partId());
            if (why != null) tip = tip.copy().append("\n").append(why);
            b.setTooltip(Tooltip.create(tip));
        }
        nav(levelPage, choices.size(), capacity, delta -> { levelPage += delta; rebuild(); });
        Component why = selected == null ? tr("unlearned") : controller.error(selected);
        if (filters.view() == View.DORMANT && why == null) why = tr("inspect_only");
        messageY = bottom() - 41;
        message = why != null ? why : current != null && current.requiresLearning() ? tr("prerequisite") : Component.empty();
        String label = current == null ? selected : current.levelName();
        if (filters.view() != View.DORMANT) {
            var b = button("add", left + 68, bottom(), inner - 68, tr("add", label == null ? "" : label), () -> add(selected));
            b.active = why == null && current != null;
            if (!message.getString().isEmpty()) b.setTooltip(Tooltip.create(message));
        }
    }

    private void add(String exactId) {
        Component error = controller.add(exactId);
        rebuild();
        if (error != null) {
            message = error;
            messageY = bottom() - 40;
            minecraft.getNarrator().sayNow(error);
        }
    }

    private void favorite(Variant spell, int left, int top) {
        boolean saved = controller.preferences().ids().contains(spell.spellId());
        Component label = tr(saved ? "unfavorite" : "favorite", spell.name());
        var b = Button.builder(Component.literal(saved ? "★" : "☆"), clicked -> {
            String failure = controller.preferences().toggle(spell.spellId());
            rebuild();
            if (failure != null) {
                message = tr(failure); messageY = bottom() - 22;
                minecraft.getNarrator().sayNow(message);
                var control = widgets.get("favorite/" + spell.spellId());
                if (control != null) control.setTooltip(Tooltip.create(message));
            }
        }).bounds(left, top, 23, 20).tooltip(Tooltip.create(label)).createNarration(s -> label.copy()).build();
        widgets.put("favorite/" + spell.spellId(), addRenderableWidget(b));
    }

    private void nav(int at, int count, int perPage, java.util.function.IntConsumer move) {
        int last = BrowserModel.clampPage(Integer.MAX_VALUE, count, perPage);
        var prev = button("prev", x + 8, bottom(), 31, Component.literal("‹"), () -> move.accept(-1));
        var next = button("next", x + 41, bottom(), 31, Component.literal("›"), () -> move.accept(1));
        prev.active = at > 0;
        next.active = at < last;
        prev.setTooltip(Tooltip.create(tr("previous", at + 1, last + 1)));
        next.setTooltip(Tooltip.create(tr("next", at + 1, last + 1)));
    }
    private Button button(String key, int left, int top, int width, Component label, Runnable action) {
        var b = Button.builder(label, clicked -> action.run()).bounds(left, top, width, 20).build();
        widgets.put(key, addRenderableWidget(b));
        return b;
    }
    private static String cycle(List<String> options, String current) {
        int next = options.indexOf(current) + 1;
        return next >= options.size() ? "" : options.get(next);
    }
    private static Component school(String id) {
        int split = id.indexOf(':');
        return Component.translatableWithFallback("school." + id.replace(':', '.'), id.substring(split + 1));
    }

    static void paper(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF23192C);
        g.fill(x, y, x + w, y + h, 0xFF9A7858);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFE7D9B8);
        g.fill(x + 2, y + 2, x + w - 2, y + 22, 0xFF48354F);
        g.fill(x + 2, y + 21, x + w - 2, y + 22, 0xFFAD8A56);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float frame) {
        g.fill(0, 0, width, height, 0x66000000);
        paper(g, x, y, w, h);
        int rightWidth = Math.min(w / 2 - 12, font.width(summary));
        String head = clip(font, heading.getString(), w - rightWidth - 25);
        g.drawString(font, head, x + 8, y + 8, 0xFFF4E7BB, false);
        String count = clip(font, summary.getString(), rightWidth);
        g.drawString(font, count, x + w - 8 - font.width(count), y + 8, 0xFFE2D3BA, false);
        int line = messageY;
        for (var text : font.split(message, w - 18)) {
            if (line + font.lineHeight > bottom()) break;
            g.drawString(font, text, x + 9, line, 0xFF3A2B20, false);
            line += font.lineHeight + 1;
        }
        super.render(g, mx, my, frame);
    }

    private static String clip(Font font, String s, int width) {
        return font.width(s) <= width ? s : font.plainSubstrByWidth(s, Math.max(0, width - font.width("…"))) + "…";
    }

    private final class SpellRow extends Button {
        private final Variant spell;
        private final net.minecraft.world.item.ItemStack icon;
        private final Component subtitle;
        SpellRow(int x, int y, int width, Variant spell, Component subtitle, Component narration, Runnable click) {
            super(x, y, width, 26, narration, b -> click.run(), DEFAULT_NARRATION);
            this.spell = spell;
            var part = controller.part(spell.partId());
            this.icon = part == null ? net.minecraft.world.item.ItemStack.EMPTY : part.getGlyph().getDefaultInstance();
            this.subtitle = subtitle;
        }
        @Override public void renderString(GuiGraphics graphics, Font font, int color) {
            graphics.renderItem(icon, getX() + 4, getY() + 5);
            int max = getWidth() - 28;
            graphics.drawString(font, clip(font, spell.name(), max), getX() + 24, getY() + 3, 0xFFFFFFFF, true);
            graphics.drawString(font, clip(font, subtitle.getString(), max), getX() + 24, getY() + 14, 0xFFE2D7BF, true);
        }
    }
}
