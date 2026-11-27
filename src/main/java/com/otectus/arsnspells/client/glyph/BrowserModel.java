package com.otectus.arsnspells.client.glyph;

import com.otectus.arsnspells.config.DisplayMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Presentation of a snapshot of eligible parts. The registry itself is never a survival collection. */
public final class BrowserModel {
    public enum View { LEARNED, FAVORITES, DORMANT }

    public record Variant(String partId, String spellId, int level, String name, String levelName,
                          String source, String school, int tier, String reason, boolean requiresLearning) {
        public boolean available() { return reason == null; }
        public String namespace() { return spellId.substring(0, spellId.indexOf(':')); }
    }

    /** Choices are the concrete variants matching the current view, search and tier filter. */
    public record Row(Variant spell, List<Variant> choices) {}
    public record Filters(View view, String query, String source, String school, boolean fits) {
        public static final Filters DEFAULT = new Filters(View.LEARNED, "", "", "", true);
    }

    private final List<Row> groups;
    private final Map<String, Row> byId;
    private final Map<String, Variant> variants;

    public BrowserModel(Collection<Variant> known) {
        Map<String, List<Variant>> grouped = new HashMap<>();
        Map<String, Variant> parts = new HashMap<>();
        for (Variant variant : known) {
            if (parts.putIfAbsent(variant.partId, variant) == null)
                grouped.computeIfAbsent(variant.spellId, unused -> new ArrayList<>()).add(variant);
        }
        variants = Map.copyOf(parts);
        List<Row> sorted = new ArrayList<>(grouped.size());
        Map<String, Row> index = new HashMap<>();
        for (var entry : grouped.entrySet()) {
            List<Variant> levels = entry.getValue();
            levels.sort(Comparator.comparingInt(Variant::level));
            Row row = new Row(levels.get(0), List.copyOf(levels));
            sorted.add(row);
            index.put(entry.getKey(), row);
        }
        sorted.sort(Comparator.comparing((Row r) -> fold(r.spell.name)).thenComparing(r -> r.spell.spellId));
        groups = List.copyOf(sorted);
        byId = Map.copyOf(index);
    }

    public Variant variant(String part) { return variants.get(part); }
    public Row group(String spell) { return byId.get(spell); }
    public List<Row> groups() { return groups; }

    public static boolean inPalette(DisplayMode mode, boolean imported, boolean readyAndAvailable) {
        return !imported || mode == DisplayMode.FLAT && readyAndAvailable;
    }

    public List<Row> rows(DisplayMode mode, boolean pending, Filters filters, Set<String> favorites, int bookTier) {
        if (mode == DisplayMode.HIDDEN || pending) return List.of();
        String query = fold(filters.query.strip());
        List<Row> result = new ArrayList<>();
        for (Row row : groups) {
            Variant spell = row.spell;
            if (filters.view == View.FAVORITES && !favorites.contains(spell.spellId)) continue;
            if (!filters.source.isEmpty() && !filters.source.equals(spell.namespace())) continue;
            boolean wholeSpell = query.startsWith("@")
                ? spell.namespace().startsWith(query.substring(1))
                : fold(spell.name).contains(query) || spell.spellId.contains(query) || fold(spell.source).contains(query);
            boolean exactLevel = !wholeSpell && row.choices.stream().anyMatch(v -> fold(v.levelName).equals(query) || v.partId.equals(query));
            List<Variant> matches = new ArrayList<>();
            for (Variant v : row.choices) {
                if ((filters.view == View.DORMANT) == v.available()) continue;
                // A dormant level cannot fit any book. Its nominal tier must not conceal its explanation.
                if (v.available() && filters.fits && v.tier > bookTier) continue;
                if (!filters.school.isEmpty() && !filters.school.equals(v.school)) continue;
                if (!wholeSpell && !(exactLevel ? fold(v.levelName).equals(query) || v.partId.equals(query)
                        : !query.startsWith("@") && (fold(v.levelName).contains(query) || v.partId.contains(query)))) continue;
                matches.add(v);
            }
            if (!matches.isEmpty()) result.add(new Row(matches.get(0), List.copyOf(matches)));
        }
        return List.copyOf(result);
    }

    public static int clampPage(int page, int count, int perPage) {
        return Math.max(0, Math.min(page, Math.max(0, count - 1) / Math.max(1, perPage)));
    }

    private static String fold(String s) { return s.toLowerCase(Locale.ROOT); }
}
