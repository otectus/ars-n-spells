package com.otectus.arsnspells.glyph.behavior;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The reloadable data-pack layer over the startup catalog: per-spell availability and tuning, and
 * a pack-wide allow/deny policy. A new ANS schema, not an upstream recipe format.
 *
 * <h2>Location and format</h2>
 * {@code data/<namespace>/ans_iron_glyphs/<any path>.json}, schema 1:
 * <pre>
 * { "schema_version": 1, "spell": "irons_spellbooks:firebolt", "adapter": "ars_n_spells:projectile",
 *   "enabled": true,
 *   "defaults": { "ars_tier": 2 },
 *   "levels": { "3": { "enabled": true, "ars_tier": 2, "source_cost": 1500, "duration_ticks": 140,
 *                      "catalyst": { "tag": "ars_n_spells:glyph_press_catalysts", "count": 2 } } } }
 * </pre>
 * A file with {@code "type": "policy"} instead carries {@code allow_spells}, {@code deny_spells},
 * {@code allow_schools} and {@code deny_schools}.
 *
 * <h2>Precedence</h2>
 * <ul>
 *   <li>For one file path, the highest-priority pack's copy wins (ordinary data-pack override); the
 *       winner's resource id and pack are reported in diagnostics.</li>
 *   <li>Two different files mapping the same spell: the one with the lowest resource id wins, the
 *       other is reported and ignored. Policies from every file are merged.</li>
 *   <li>An explicit deny (spell or school) always wins. When any allow list is non-empty, only
 *       allowed spells (by id or school) are available. A pack can never enable a spell Iron's own
 *       config disables.</li>
 *   <li>Level overrides beat {@code defaults}, which beat the built-in defaults (tier from Iron's
 *       rarity; Press cost and time by tier).</li>
 * </ul>
 *
 * <h2>Validation</h2>
 * Every number is finite and bounded, every spell and level must already have a startup identity,
 * adapters must be the spell's catalogued adapter or {@code ars_n_spells:self}. A file that fails
 * validation is rejected whole. On a reload, any rejected file rejects the whole candidate and the
 * last valid mappings stay in force; at first load, spells named by a rejected file fail closed.
 */
public final class GlyphMappings {

    public static final String DIRECTORY = "ans_iron_glyphs";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_SOURCE_COST = 100_000;
    public static final int MAX_DURATION_TICKS = 72_000;
    public static final int MAX_CATALYST_COUNT = 64;

    /** One file as a pack supplied it. */
    public record Source(ResourceLocation file, String pack, JsonElement json) {}

    /** Optional fields; null means "not set here". */
    public record LevelOverride(@Nullable Boolean enabled, @Nullable Integer tier, @Nullable Integer sourceCost,
                                @Nullable Integer durationTicks, @Nullable String catalystTag, @Nullable Integer catalystCount) {
        static final LevelOverride NONE = new LevelOverride(null, null, null, null, null, null);
    }

    public record SpellMapping(ResourceLocation spell, @Nullable ResourceLocation adapter, @Nullable Boolean enabled,
                               LevelOverride defaults, Map<Integer, LevelOverride> levels,
                               ResourceLocation file, String pack) {}

    public record Policy(Set<ResourceLocation> allowSpells, Set<ResourceLocation> denySpells,
                         Set<ResourceLocation> allowSchools, Set<ResourceLocation> denySchools) {
        public static final Policy OPEN = new Policy(Set.of(), Set.of(), Set.of(), Set.of());

        /** Null when allowed, otherwise why not. */
        @Nullable
        public String refusal(ResourceLocation spell, ResourceLocation school) {
            if (denySpells.contains(spell)) return "spell denied by pack policy";
            if (denySchools.contains(school)) return "school denied by pack policy";
            boolean restricted = !allowSpells.isEmpty() || !allowSchools.isEmpty();
            if (restricted && !allowSpells.contains(spell) && !allowSchools.contains(school)) return "not on the pack allow list";
            return null;
        }
    }

    /** The parsed layer: what applies, what was rejected and why. */
    public record Result(Map<ResourceLocation, SpellMapping> mappings, Policy policy, List<String> errors,
                         Set<ResourceLocation> failedClosed, Map<ResourceLocation, String> winners) {
        public static final Result EMPTY = new Result(Map.of(), Policy.OPEN, List.of(), Set.of(), Map.of());

        public boolean valid() {
            return errors.isEmpty();
        }
    }

    private GlyphMappings() {
    }

    /** Parse every file. Pure: unit-tested without a server. Sources must already be one per file path. */
    public static Result parse(List<Source> sources, ImportedGlyphCatalog catalog) {
        List<Source> sorted = new ArrayList<>(sources);
        sorted.sort((a, b) -> a.file().compareNamespaced(b.file()));
        Map<ResourceLocation, SpellMapping> mappings = new TreeMap<>(ResourceLocation::compareNamespaced);
        Map<ResourceLocation, String> winners = new TreeMap<>(ResourceLocation::compareNamespaced);
        Set<ResourceLocation> allowSpells = new TreeSet<>(ResourceLocation::compareNamespaced);
        Set<ResourceLocation> denySpells = new TreeSet<>(ResourceLocation::compareNamespaced);
        Set<ResourceLocation> allowSchools = new TreeSet<>(ResourceLocation::compareNamespaced);
        Set<ResourceLocation> denySchools = new TreeSet<>(ResourceLocation::compareNamespaced);
        List<String> errors = new ArrayList<>();
        Set<ResourceLocation> failedClosed = new TreeSet<>(ResourceLocation::compareNamespaced);
        for (Source source : sorted) {
            String where = source.file() + " (pack " + source.pack() + ")";
            try {
                JsonObject o = source.json().getAsJsonObject();
                int schema = o.has("schema_version") ? o.get("schema_version").getAsInt() : -1;
                if (schema != SCHEMA_VERSION) {
                    throw new IllegalArgumentException("schema_version " + schema + " is not " + SCHEMA_VERSION);
                }
                if (o.has("type") && "policy".equals(o.get("type").getAsString())) {
                    readIds(o, "allow_spells", allowSpells);
                    readIds(o, "deny_spells", denySpells);
                    readIds(o, "allow_schools", allowSchools);
                    readIds(o, "deny_schools", denySchools);
                    continue;
                }
                SpellMapping mapping = spellMapping(source, o, catalog);
                if (mappings.containsKey(mapping.spell())) {
                    errors.add(where + ": spell " + mapping.spell() + " is already mapped by "
                        + winners.get(mapping.spell()) + "; this file is ignored");
                    continue;
                }
                mappings.put(mapping.spell(), mapping);
                winners.put(mapping.spell(), where);
            } catch (RuntimeException invalid) {
                errors.add(where + ": " + invalid.getMessage());
                ResourceLocation named = namedSpell(source.json());
                if (named != null) failedClosed.add(named);
            }
        }
        return new Result(Collections.unmodifiableMap(mappings),
            new Policy(Set.copyOf(allowSpells), Set.copyOf(denySpells), Set.copyOf(allowSchools), Set.copyOf(denySchools)),
            List.copyOf(errors), Collections.unmodifiableSet(failedClosed), Collections.unmodifiableMap(winners));
    }

    private static SpellMapping spellMapping(Source source, JsonObject o, ImportedGlyphCatalog catalog) {
        ResourceLocation spell = ResourceLocation.parse(o.get("spell").getAsString());
        CatalogEntry entry = catalog.entry(spell);
        if (entry == null || !entry.supported()) {
            throw new IllegalArgumentException("spell " + spell + " has no glyph adapter in the startup catalog");
        }
        ResourceLocation adapter = o.has("adapter") ? ResourceLocation.parse(o.get("adapter").getAsString()) : null;
        if (adapter != null && !adapter.equals(entry.adapter()) && !adapter.toString().equals("ars_n_spells:self")) {
            throw new IllegalArgumentException("adapter " + adapter + " is not available for " + spell
                + " (use " + entry.adapter() + " or ars_n_spells:self)");
        }
        Boolean enabled = o.has("enabled") ? o.get("enabled").getAsBoolean() : null;
        LevelOverride defaults = o.has("defaults") ? override(o.getAsJsonObject("defaults")) : LevelOverride.NONE;
        Map<Integer, LevelOverride> levels = new LinkedHashMap<>();
        if (o.has("levels")) {
            for (var level : o.getAsJsonObject("levels").entrySet()) {
                int n;
                try {
                    n = Integer.parseInt(level.getKey());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("level key '" + level.getKey() + "' is not a number");
                }
                if (n < entry.minLevel() || n > entry.maxLevel()) {
                    throw new IllegalArgumentException("level " + n + " of " + spell + " has no startup identity (catalog levels "
                        + entry.minLevel() + ".." + entry.maxLevel() + "); a reload cannot add glyph variants");
                }
                levels.put(n, override(level.getValue().getAsJsonObject()));
            }
        }
        return new SpellMapping(spell, adapter, enabled, defaults, Collections.unmodifiableMap(levels),
            source.file(), source.pack());
    }

    private static LevelOverride override(JsonObject o) {
        Boolean enabled = o.has("enabled") ? o.get("enabled").getAsBoolean() : null;
        Integer tier = o.has("ars_tier") ? bounded(o, "ars_tier", 1, 3) : null;
        Integer cost = o.has("source_cost") ? bounded(o, "source_cost", 0, MAX_SOURCE_COST) : null;
        Integer ticks = o.has("duration_ticks") ? bounded(o, "duration_ticks", 1, MAX_DURATION_TICKS) : null;
        String tag = null;
        Integer count = null;
        if (o.has("catalyst")) {
            JsonObject catalyst = o.getAsJsonObject("catalyst");
            if (catalyst.has("tag")) {
                tag = ResourceLocation.parse(catalyst.get("tag").getAsString()).toString();
            }
            if (catalyst.has("count")) count = bounded(catalyst, "count", 0, MAX_CATALYST_COUNT);
        }
        return new LevelOverride(enabled, tier, cost, ticks, tag, count);
    }

    private static int bounded(JsonObject o, String key, int min, int max) {
        double raw = o.get(key).getAsDouble();
        if (!Double.isFinite(raw) || raw != Math.rint(raw) || raw < min || raw > max) {
            throw new IllegalArgumentException(key + " must be a whole number in " + min + ".." + max + ", got " + o.get(key));
        }
        return (int) raw;
    }

    private static void readIds(JsonObject o, String key, Set<ResourceLocation> out) {
        if (!o.has(key)) return;
        for (JsonElement e : o.getAsJsonArray(key)) out.add(ResourceLocation.parse(e.getAsString()));
    }

    @Nullable
    private static ResourceLocation namedSpell(JsonElement json) {
        try {
            return ResourceLocation.tryParse(json.getAsJsonObject().get("spell").getAsString());
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
