package com.otectus.arsnspells.glyph.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.arsnspells.glyph.GlyphFamily;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The reloadable behaviour layer for imported glyphs: which are allowed, tier overrides, and what
 * a Glyph Press conversion costs. Built from {@code data/<namespace>/ans_iron_glyphs/**.json}
 * on every datapack load, validated as a whole and published atomically.
 *
 * <p>It can only describe glyphs the startup catalog already registered. A file naming a spell
 * the catalog does not know, or a level beyond what the catalog registered, is an error: a
 * reload never manufactures a new id, and an unsupported level is reported, never remapped.
 *
 * <p>Three file types, chosen by {@code "type"}:
 * <ul>
 *   <li>{@code ars_n_spells:press_tiers}: Source, catalyst count and duration per conversion tier;</li>
 *   <li>{@code ars_n_spells:spell} (the default): one spell's {@code enabled} flag and per-level
 *       {@code ars_tier}, {@code source_cost}, {@code duration_ticks} and {@code catalyst} count. An
 *       optional {@code adapter} must name a known adapter and match the catalogued one;</li>
 *   <li>{@code ars_n_spells:policy}: spell and school allow and deny lists.</li>
 * </ul>
 * Precedence: an explicit deny (spell or school) wins over every allow; when any allow list is
 * present, only what it names is allowed; a per-spell {@code enabled: false} disables that
 * spell; a later pack's file for a spell replaces an earlier pack's. Two files for the same spell
 * in one pack are an error. Upstream Iron's disables are checked separately and always win.
 */
public final class GlyphMappingSnapshot {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_FILES = 2048;
    public static final int MAX_SOURCE_COST = 100_000;
    public static final int MAX_DURATION_TICKS = 20 * 60 * 10;
    public static final int MAX_CATALYSTS = 64;
    public static final ResourceLocation DEFAULT_CATALYST_TAG = new ResourceLocation("ars_n_spells", "glyph_press_catalysts");

    /** Conversion terms for one tier or one spell level. */
    public record PressTerms(int sourceCost, int durationTicks, ResourceLocation catalystTag, int catalystCount) {
        public PressTerms {
            if (sourceCost < 0 || sourceCost > MAX_SOURCE_COST) throw new IllegalArgumentException("source_cost out of range: " + sourceCost);
            if (durationTicks < 1 || durationTicks > MAX_DURATION_TICKS) throw new IllegalArgumentException("duration_ticks out of range: " + durationTicks);
            if (catalystCount < 0 || catalystCount > MAX_CATALYSTS) throw new IllegalArgumentException("catalyst count out of range: " + catalystCount);
            if (catalystTag == null) throw new IllegalArgumentException("catalyst tag missing");
        }
    }

    /** One spell's overrides. A null field means "not overridden". */
    record SpellMapping(String sourcePack, ResourceLocation file, Boolean enabled,
                        Map<Integer, Integer> tierByLevel, Map<Integer, PressTerms> termsByLevel) {}

    /** The shipped tier terms: tier I 1 gem, 500 Source, 5 s; II 2, 1,500, 7 s; III 4, 3,000, 10 s. */
    public static final Map<Integer, PressTerms> DEFAULT_TIERS = Map.of(
        1, new PressTerms(500, 100, DEFAULT_CATALYST_TAG, 1),
        2, new PressTerms(1500, 140, DEFAULT_CATALYST_TAG, 2),
        3, new PressTerms(3000, 200, DEFAULT_CATALYST_TAG, 4));

    private static volatile GlyphMappingSnapshot current = defaults();
    private static volatile int generation;

    private final Map<Integer, PressTerms> tiers;
    private final Map<ResourceLocation, SpellMapping> spells;
    private final Set<ResourceLocation> allowSpells, denySpells, allowSchools, denySchools;
    private final Set<ResourceLocation> failedClosed;
    private final List<String> errors;
    private final List<String> winners;
    private final String digest;
    private final int snapshotGeneration;
    /** A client view carries the server's digest instead of computing its own over partial data. */
    private String serverDigest;

    private GlyphMappingSnapshot(Map<Integer, PressTerms> tiers, Map<ResourceLocation, SpellMapping> spells,
                                 Set<ResourceLocation> allowSpells, Set<ResourceLocation> denySpells,
                                 Set<ResourceLocation> allowSchools, Set<ResourceLocation> denySchools,
                                 Set<ResourceLocation> failedClosed, List<String> errors, List<String> winners, int generation) {
        this.tiers = Collections.unmodifiableMap(new TreeMap<>(tiers));
        this.spells = Collections.unmodifiableMap(new TreeMap<>(spells));
        this.allowSpells = Collections.unmodifiableSet(new TreeSet<>(allowSpells));
        this.denySpells = Collections.unmodifiableSet(new TreeSet<>(denySpells));
        this.allowSchools = Collections.unmodifiableSet(new TreeSet<>(allowSchools));
        this.denySchools = Collections.unmodifiableSet(new TreeSet<>(denySchools));
        this.failedClosed = Collections.unmodifiableSet(new TreeSet<>(failedClosed));
        this.errors = List.copyOf(errors);
        this.winners = List.copyOf(winners);
        this.snapshotGeneration = generation;
        this.digest = computeDigest();
    }

    public static GlyphMappingSnapshot defaults() {
        return new GlyphMappingSnapshot(DEFAULT_TIERS, Map.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
            List.of(), List.of(), 0);
    }

    public static GlyphMappingSnapshot current() {
        return current;
    }

    /** Publish a fully validated snapshot. */
    public static void publish(GlyphMappingSnapshot snapshot) {
        current = snapshot;
    }

    public static int nextGeneration() {
        return ++generation;
    }

    /** A source file as the loader found it: its id, the pack it came from, the pack's rank (higher wins). */
    public record Source(ResourceLocation id, String pack, int packRank, JsonElement json) {}

    /**
     * Build a candidate from every source file. Returns the snapshot and every error found;
     * {@link #errors()} is empty only when every file was valid.
     */
    public static GlyphMappingSnapshot build(List<Source> sources, ImportedGlyphCatalog catalog, int generation) {
        List<String> errors = new ArrayList<>();
        List<String> winners = new ArrayList<>();
        Map<Integer, PressTerms> tiers = new TreeMap<>(DEFAULT_TIERS);
        Map<ResourceLocation, SpellMapping> spells = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> spellRank = new LinkedHashMap<>();
        Set<ResourceLocation> allowSpells = new TreeSet<>(), denySpells = new TreeSet<>(),
            allowSchools = new TreeSet<>(), denySchools = new TreeSet<>(), failedClosed = new TreeSet<>();
        if (sources.size() > MAX_FILES) {
            errors.add("too many ans_iron_glyphs files: " + sources.size());
            sources = sources.subList(0, MAX_FILES);
        }
        List<Source> ordered = new ArrayList<>(sources);
        ordered.sort((a, b) -> a.packRank() != b.packRank() ? Integer.compare(a.packRank(), b.packRank())
            : a.id().toString().compareTo(b.id().toString()));
        int tierRank = Integer.MIN_VALUE;
        for (Source source : ordered) {
            ResourceLocation spellNamed = null;
            try {
                JsonObject json = source.json().getAsJsonObject();
                int schema = json.has("schema_version") ? json.get("schema_version").getAsInt() : -1;
                if (schema != SCHEMA_VERSION) throw new IllegalArgumentException("schema_version must be " + SCHEMA_VERSION);
                String type = json.has("type") ? json.get("type").getAsString() : "ars_n_spells:spell";
                switch (type) {
                    case "ars_n_spells:press_tiers" -> {
                        JsonObject tierJson = json.getAsJsonObject("tiers");
                        Map<Integer, PressTerms> parsed = new TreeMap<>();
                        for (Map.Entry<String, JsonElement> entry : tierJson.entrySet()) {
                            int tier = Integer.parseInt(entry.getKey());
                            if (tier < 1 || tier > 3) throw new IllegalArgumentException("tier must be 1-3: " + tier);
                            parsed.put(tier, terms(entry.getValue().getAsJsonObject(), null));
                        }
                        if (source.packRank() < tierRank) continue;
                        tierRank = source.packRank();
                        tiers.putAll(parsed);
                        winners.add("press_tiers <- " + source.id() + " [" + source.pack() + "]");
                    }
                    case "ars_n_spells:policy" -> {
                        readIds(json, "allow_spells", allowSpells);
                        readIds(json, "deny_spells", denySpells);
                        readIds(json, "allow_schools", allowSchools);
                        readIds(json, "deny_schools", denySchools);
                    }
                    case "ars_n_spells:spell" -> {
                        ResourceLocation spell = id(json.get("spell").getAsString());
                        spellNamed = spell;
                        List<ImportedGlyphDefinition> levels = catalog.levelsOf(spell);
                        if (levels.isEmpty()) throw new IllegalArgumentException("spell " + spell + " is not in the imported-glyph catalog");
                        if (json.has("adapter")) {
                            String adapter = json.get("adapter").getAsString();
                            GlyphFamily named;
                            try {
                                named = GlyphFamily.fromAdapterId(adapter);
                            } catch (IllegalArgumentException unknown) {
                                throw new IllegalArgumentException("unknown adapter " + adapter);
                            }
                            if (named != levels.get(0).family())
                                throw new IllegalArgumentException(spell + " is catalogued with adapter " + levels.get(0).family().adapterId()
                                    + "; an adapter is part of the startup catalog and cannot be changed by a datapack");
                        }
                        Boolean enabled = json.has("enabled") ? json.get("enabled").getAsBoolean() : null;
                        Map<Integer, Integer> tierByLevel = new TreeMap<>();
                        Map<Integer, PressTerms> termsByLevel = new TreeMap<>();
                        if (json.has("levels")) {
                            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("levels").entrySet()) {
                                int level = Integer.parseInt(entry.getKey());
                                if (level < 1 || level > levels.size())
                                    throw new IllegalArgumentException(spell + " level " + level + " is not a registered glyph (catalog has 1-" + levels.size() + ")");
                                JsonObject levelJson = entry.getValue().getAsJsonObject();
                                if (levelJson.has("ars_tier")) {
                                    int tier = levelJson.get("ars_tier").getAsInt();
                                    if (tier < 1 || tier > 3) throw new IllegalArgumentException("ars_tier must be 1-3");
                                    tierByLevel.put(level, tier);
                                }
                                if (levelJson.has("source_cost") || levelJson.has("duration_ticks") || levelJson.has("catalyst")) {
                                    int tier = tierByLevel.getOrDefault(level, levels.get(level - 1).defaultTier());
                                    termsByLevel.put(level, terms(levelJson, DEFAULT_TIERS.get(tier)));
                                }
                            }
                        }
                        Integer previousRank = spellRank.get(spell);
                        if (previousRank != null && previousRank == source.packRank())
                            throw new IllegalArgumentException("a second file for " + spell + " in pack " + source.pack());
                        spellRank.put(spell, source.packRank());
                        spells.put(spell, new SpellMapping(source.pack(), source.id(), enabled, tierByLevel, termsByLevel));
                        winners.add(spell + " <- " + source.id() + " [" + source.pack() + "]");
                    }
                    default -> throw new IllegalArgumentException("unknown type " + type);
                }
            } catch (RuntimeException invalid) {
                errors.add(source.id() + " [" + source.pack() + "]: " + invalid.getMessage());
                if (spellNamed != null) failedClosed.add(spellNamed);
            }
        }
        for (ResourceLocation id : allowSpells) if (catalog.levelsOf(id).isEmpty()) errors.add("policy allows unknown spell " + id);
        for (ResourceLocation id : denySpells) if (catalog.levelsOf(id).isEmpty()) errors.add("policy denies unknown spell " + id);
        return new GlyphMappingSnapshot(tiers, spells, allowSpells, denySpells, allowSchools, denySchools,
            failedClosed, errors, winners, generation);
    }

    private static PressTerms terms(JsonObject json, PressTerms fallback) {
        int source = json.has("source_cost") ? json.get("source_cost").getAsInt() : require(fallback).sourceCost();
        int duration = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt() : require(fallback).durationTicks();
        ResourceLocation tag = fallback == null ? DEFAULT_CATALYST_TAG : fallback.catalystTag();
        int count = fallback == null ? 0 : fallback.catalystCount();
        if (json.has("catalyst")) {
            JsonObject catalyst = json.getAsJsonObject("catalyst");
            if (catalyst.has("tag")) tag = id(catalyst.get("tag").getAsString());
            if (catalyst.has("count")) count = catalyst.get("count").getAsInt();
        } else if (fallback == null) {
            throw new IllegalArgumentException("catalyst missing");
        }
        if (!Double.isFinite(source) || !Double.isFinite(duration)) throw new IllegalArgumentException("non-finite number");
        return new PressTerms(source, duration, tag, count);
    }

    private static PressTerms require(PressTerms fallback) {
        if (fallback == null) throw new IllegalArgumentException("source_cost and duration_ticks are required");
        return fallback;
    }

    private static void readIds(JsonObject json, String key, Set<ResourceLocation> into) {
        if (!json.has(key)) return;
        for (JsonElement element : json.getAsJsonArray(key)) into.add(id(element.getAsString()));
    }

    private static ResourceLocation id(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("invalid id " + raw);
        return id;
    }

    /** Whether the datapack layer allows this glyph. An explicit deny wins over everything. */
    public boolean allows(ImportedGlyphDefinition definition) {
        ResourceLocation spell = definition.spell();
        if (failedClosed.contains(spell)) return false;
        if (denySpells.contains(spell) || denySchools.contains(definition.school())) return false;
        if ((!allowSpells.isEmpty() || !allowSchools.isEmpty())
            && !allowSpells.contains(spell) && !allowSchools.contains(definition.school())) return false;
        SpellMapping mapping = spells.get(spell);
        return mapping == null || mapping.enabled() == null || mapping.enabled();
    }

    public Integer tierOverride(ImportedGlyphDefinition definition) {
        SpellMapping mapping = spells.get(definition.spell());
        return mapping == null ? null : mapping.tierByLevel().get(definition.level());
    }

    /** What converting a scroll into this glyph costs at the Press. */
    public PressTerms pressTerms(ImportedGlyphDefinition definition, int effectiveTier) {
        SpellMapping mapping = spells.get(definition.spell());
        PressTerms override = mapping == null ? null : mapping.termsByLevel().get(definition.level());
        return override != null ? override : tiers.getOrDefault(effectiveTier, DEFAULT_TIERS.get(3));
    }

    public Map<Integer, PressTerms> tiers() { return tiers; }
    public List<String> errors() { return errors; }
    public List<String> winners() { return winners; }
    public Set<ResourceLocation> failedClosed() { return failedClosed; }
    public String digest() { return serverDigest != null ? serverDigest : digest; }
    public int generation() { return snapshotGeneration; }

    // ---- Client view ------------------------------------------------------------------------

    /** One spell's overrides as the client sees them. */
    public record ClientSpell(Boolean enabled, Map<Integer, Integer> tierByLevel, Map<Integer, PressTerms> termsByLevel) {}

    /**
     * Everything the client needs to hide, tier and price glyphs exactly as the server does, for the
     * editor, tooltips and the recipe viewer. Diagnostics (errors, winners) stay on the server.
     */
    public record ClientView(Map<Integer, PressTerms> tiers, Map<ResourceLocation, ClientSpell> spells,
                             List<ResourceLocation> allowSpells, List<ResourceLocation> denySpells,
                             List<ResourceLocation> allowSchools, List<ResourceLocation> denySchools,
                             List<ResourceLocation> failedClosed, String digest, int generation) {}

    public ClientView clientView() {
        Map<ResourceLocation, ClientSpell> view = new TreeMap<>();
        spells.forEach((spell, mapping) -> view.put(spell,
            new ClientSpell(mapping.enabled(), mapping.tierByLevel(), mapping.termsByLevel())));
        return new ClientView(tiers, view, List.copyOf(allowSpells), List.copyOf(denySpells), List.copyOf(allowSchools),
            List.copyOf(denySchools), List.copyOf(failedClosed), digest(), snapshotGeneration);
    }

    /** Rebuild the server's snapshot on a remote client. It keeps the server's digest as its identity. */
    public static GlyphMappingSnapshot fromClientView(ClientView view) {
        Map<ResourceLocation, SpellMapping> spells = new TreeMap<>();
        view.spells().forEach((spell, mapping) -> spells.put(spell, new SpellMapping("server", spell, mapping.enabled(),
            new TreeMap<>(mapping.tierByLevel()), new TreeMap<>(mapping.termsByLevel()))));
        GlyphMappingSnapshot snapshot = new GlyphMappingSnapshot(view.tiers(), spells, new TreeSet<>(view.allowSpells()),
            new TreeSet<>(view.denySpells()), new TreeSet<>(view.allowSchools()), new TreeSet<>(view.denySchools()),
            new TreeSet<>(view.failedClosed()), List.of(), List.of(), view.generation());
        snapshot.serverDigest = view.digest();
        return snapshot;
    }

    /** The most Source any conversion this snapshot allows can need, for the Press capacity check. */
    public int maxSourceCost() {
        int max = 0;
        for (PressTerms terms : tiers.values()) max = Math.max(max, terms.sourceCost());
        for (SpellMapping mapping : spells.values()) for (PressTerms terms : mapping.termsByLevel().values()) max = Math.max(max, terms.sourceCost());
        return max;
    }

    private String computeDigest() {
        StringBuilder canonical = new StringBuilder("ans-iron-glyph-behaviour/v" + SCHEMA_VERSION + "\n");
        tiers.forEach((tier, terms) -> canonical.append("tier ").append(tier).append(' ').append(terms).append('\n'));
        spells.forEach((spell, mapping) -> canonical.append("spell ").append(spell).append(' ').append(mapping.enabled())
            .append(' ').append(mapping.tierByLevel()).append(' ').append(mapping.termsByLevel()).append('\n'));
        canonical.append("allow ").append(allowSpells).append(allowSchools).append('\n');
        canonical.append("deny ").append(denySpells).append(denySchools).append('\n');
        canonical.append("closed ").append(failedClosed).append('\n');
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
