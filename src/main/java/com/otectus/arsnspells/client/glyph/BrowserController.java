package com.otectus.arsnspells.client.glyph;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.otectus.arsnspells.config.AnsClientConfig;
import com.otectus.arsnspells.config.DisplayMode;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owned by one editor; the recipe-viewer's publication callback and player data remain independent. */
public final class BrowserController {
    final ArsGlyphEditorHost host;
    private Map<ResourceLocation, AbstractSpellPart> knowledge = Map.of();
    private Map<String, ImportedIronEffect> parts = Map.of();
    private BrowserModel model = new BrowserModel(List.of());
    private List<?> draft = List.of();
    private GlyphFavorites preferences;
    private GlyphLibraryScreen panel;
    private Object player, connection, language;
    private DisplayMode mode;
    private long generation = -1;
    private int tier, ticks, revision, catalogBuilds;
    private boolean creative, pending;
    private int searchRevision = -1;
    private String lastQuery = "";
    private int nativeMatches;

    public BrowserController(ArsGlyphEditorHost host) { this.host = host; }
    public void tick() { refresh(++ticks % 10 == 0); }
    int revision() { return revision; }
    int builds() { return catalogBuilds; }
    BrowserModel model() { return model; }
    boolean pending() { return pending; }
    int tier() { return tier; }
    ImportedIronEffect part(String id) { return parts.get(id); }
    GlyphFavorites preferences() {
        if (preferences == null) preferences = new GlyphFavorites(BrowserPlatform.favoritesPath());
        return preferences;
    }
    static Component tr(String key, Object... args) { return Component.translatable("ars_n_spells.browser." + key, args); }

    static Component amount(String unit, int count) { return tr(unit + (count == 1 ? "_one" : ""), count); }

    private void refresh(boolean checkKnowledge) {
        var mc = Minecraft.getInstance();
        boolean newPlayer = mc.player != player || mc.getConnection() != connection;
        if (newPlayer) {
            player = mc.player;
            connection = mc.getConnection();
            if (panel != null) panel.resetSession();
        }
        DisplayMode display = AnsClientConfig.mode();
        long accepted = BrowserPlatform.generation();
        Object translations = Language.getInstance();
        int bookTier = host.browserBookTier();
        boolean creativeBook = host.browserCreative();
        boolean rebuild = newPlayer || mode != display || generation != accepted || language != translations
            || tier != bookTier || creative != creativeBook;
        if (rebuild || checkKnowledge) {
            Map<ResourceLocation, AbstractSpellPart> current = new HashMap<>();
            if (player != null && host.browserOwnsBook()) {
                for (var part : host.browserKnowledge()) if (part != null) current.put(part.getRegistryName(), part);
            }
            // Some addons mutate their collection in place. Compare actual IDs, independent of iteration order.
            rebuild |= !current.keySet().equals(knowledge.keySet());
            knowledge = current;
        }
        if (rebuild) {
            mode = display;
            generation = accepted;
            language = translations;
            tier = bookTier;
            creative = creativeBook;
            pending = BrowserPlatform.pending();
            var variants = new ArrayList<BrowserModel.Variant>();
            Map<String, ImportedIronEffect> effects = new HashMap<>();
            if (mode != DisplayMode.HIDDEN) {
                for (var part : knowledge.values()) {
                    if (part instanceof ImportedIronEffect effect) {
                        effects.put(effect.getRegistryName().toString(), effect);
                        if (!pending) variants.add(BrowserPlatform.variant(effect));
                    }
                }
            }
            parts = effects;
            model = new BrowserModel(variants);
            catalogBuilds++;
            revision++;
            host.browserRefreshPalette();
        }
        var context = host.browserDraft();
        if (!context.equals(draft)) {
            draft = context;
            revision++;
        }
    }

    public boolean inPalette(AbstractSpellPart part) {
        if (!(part instanceof ImportedIronEffect effect)) return true;
        DisplayMode display = AnsClientConfig.mode();
        return BrowserModel.inPalette(display, true, display == DisplayMode.FLAT && BrowserPlatform.available(effect));
    }

    @Nullable public Component bookmark() {
        if (AnsClientConfig.mode() == DisplayMode.HIDDEN || parts.isEmpty() && !BrowserPlatform.enabled()) return null;
        String query = host.browserQuery();
        if (mode == DisplayMode.GROUPED && query != null && !query.isBlank()) {
            if (searchRevision != revision || !lastQuery.equals(query)) {
                nativeMatches = model.rows(mode, pending, new BrowserModel.Filters(BrowserModel.View.LEARNED, query, "", "", true),
                    Set.of(), tier).size();
                searchRevision = revision;
                lastQuery = query;
            }
            if (nativeMatches > 0) return tr("search_link", amount("spells", nativeMatches));
        }
        return tr("bookmark");
    }

    public void open() {
        refresh(true);
        if (bookmark() == null || !host.browserOwnsBook()) return;
        if (panel == null) panel = new GlyphLibraryScreen(this);
        if (mode == DisplayMode.GROUPED && searchRevision == revision && nativeMatches > 0 && lastQuery.equals(host.browserQuery()))
            panel.searchFromBook(lastQuery);
        BrowserPlatform.push(panel);
    }

    List<BrowserModel.Row> rows(BrowserModel.Filters filters) {
        Set<String> favorites = filters.view() == BrowserModel.View.FAVORITES ? preferences().ids() : Set.of();
        return model.rows(mode, pending, filters, favorites, tier);
    }

    @Nullable Component error(String id) {
        if (!host.browserOwnsBook()) return tr("book_changed");
        if (pending) return tr("waiting");
        var part = parts.get(id);
        if (part == null || GlyphRegistry.getSpellpartMap().get(part.getRegistryName()) != part) return tr("unlearned");
        var variant = model.variant(id);
        if (variant == null) return tr("unlearned");
        if (!variant.available()) return Component.literal(variant.reason());
        if (variant.tier() > tier) return tr("requires_tier", variant.tier());
        return host.browserError(part);
    }

    @Nullable Component add(String id) {
        var mc = Minecraft.getInstance();
        if (player != mc.player || connection != mc.getConnection() || !host.browserOwnsBook()) return tr("book_changed");
        refresh(true);
        if (mode == DisplayMode.HIDDEN) return tr("unlearned");
        Component invalid = error(id);
        if (invalid != null) return invalid;
        host.browserInsert(parts.get(id));
        refresh(false);
        return null;
    }
}
