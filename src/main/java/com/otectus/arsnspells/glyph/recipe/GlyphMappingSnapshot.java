package com.otectus.arsnspells.glyph.recipe;

import com.google.gson.JsonElement;
import com.otectus.arsnspells.glyph.GlyphFamily;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

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
 * <h2>Schema (canonical schema 2, shared with the NeoForge build)</h2>
 * Three document types, chosen by {@code "type"}:
 * <ul>
 *   <li>{@code ars_n_spells:spell} (the default): {@code spell}; optional {@code enabled};
 *       optional {@code adapter}, either the catalogued adapter (a confirmation) or
 *       {@code ars_n_spells:self} (a restriction to Self casts); optional {@code defaults} and
 *       {@code levels} objects whose entries take {@code enabled}, {@code ars_tier},
 *       {@code source_cost}, {@code duration_ticks} and {@code catalyst} ({@code tag},
 *       {@code count}). A level override beats {@code defaults}, which beats the tier terms;</li>
 *   <li>{@code ars_n_spells:press_tiers}: Source, duration and catalyst per conversion tier;</li>
 *   <li>{@code ars_n_spells:policy}: spell and school allow and deny lists.</li>
 * </ul>
 * Schema 1 files of either loader's 3.4.0 dialect are read through the same rules (the legacy
 * NeoForge policy type {@code "policy"} is an accepted alias). Unknown fields are errors in both
 * schemas; fields starting with {@code _} are comments.
 *
 * <h2>Precedence</h2>
 * An explicit deny (spell or school) wins over every allow; when any allow list is present, only
 * what it names is allowed; {@code enabled: false} at spell, default or level scope disables what
 * it covers. For each spell (and each press tier) the file from the highest-priority pack wins;
 * two different declarations at that same priority are a conflict, reported, and the spell fails
 * closed. Policies from every pack are merged. Upstream Iron's disables are checked separately
 * and always win.
 */
public final class GlyphMappingSnapshot {
    /** The canonical schema; {@link #LEGACY_SCHEMA_VERSION} files are still read. */
    public static final int SCHEMA_VERSION = 2;
    public static final int LEGACY_SCHEMA_VERSION = 1;
    public static final int MAX_FILES = 2048;
    public static final int MAX_SOURCE_COST = 100_000;
    /** One hour: the same bound on both loaders (NeoForge has always allowed it; Forge 3.4.0 stopped at ten minutes). */
    public static final int MAX_DURATION_TICKS = 20 * 60 * 60;
    public static final int MAX_CATALYSTS = 64;
    public static final ResourceLocation DEFAULT_CATALYST_TAG = new ResourceLocation("ars_n_spells", "glyph_press_catalysts");
    /** The adapter restriction a datapack may apply to any spell: Self casts only. */
    public static final ResourceLocation SELF_ADAPTER = new ResourceLocation("ars_n_spells", "self");

    private static final Set<String> SPELL_KEYS = Set.of("schema_version", "type", "spell", "enabled", "adapter", "defaults", "levels");
    private static final Set<String> LEVEL_KEYS = Set.of("enabled", "ars_tier", "source_cost", "duration_ticks", "catalyst");
    private static final Set<String> CATALYST_KEYS = Set.of("tag", "count");
    private static final Set<String> TIERS_KEYS = Set.of("schema_version", "type", "tiers");
    private static final Set<String> TIER_TERMS_KEYS = Set.of("source_cost", "duration_ticks", "catalyst");
    private static final Set<String> POLICY_KEYS = Set.of("schema_version", "type", "allow_spells", "deny_spells", "allow_schools", "deny_schools");

    /** Conversion terms for one tier or one spell level. */
    public record PressTerms(int sourceCost, int durationTicks, ResourceLocation catalystTag, int catalystCount) {
        public PressTerms {
            if (sourceCost < 0 || sourceCost > MAX_SOURCE_COST) throw new IllegalArgumentException("source_cost out of range: " + sourceCost);
            if (durationTicks < 1 || durationTicks > MAX_DURATION_TICKS) throw new IllegalArgumentException("duration_ticks out of range: " + durationTicks);
            if (catalystCount < 0 || catalystCount > MAX_CATALYSTS) throw new IllegalArgumentException("catalyst count out of range: " + catalystCount);
            if (catalystTag == null) throw new IllegalArgumentException("catalyst tag missing");
        }
    }

    /** Optional per-level (or default) fields; null means "not set here". */
    public record LevelOverride(@Nullable Boolean enabled, @Nullable Integer tier, @Nullable Integer sourceCost,
                                @Nullable Integer durationTicks, @Nullable ResourceLocation catalystTag,
                                @Nullable Integer catalystCount) {
        public static final LevelOverride NONE = new LevelOverride(null, null, null, null, null, null);

        public LevelOverride {
            if (tier != null && (tier < 1 || tier > 3)) throw new IllegalArgumentException("ars_tier out of range: " + tier);
            if (sourceCost != null && (sourceCost < 0 || sourceCost > MAX_SOURCE_COST)) throw new IllegalArgumentException("source_cost out of range: " + sourceCost);
            if (durationTicks != null && (durationTicks < 1 || durationTicks > MAX_DURATION_TICKS)) throw new IllegalArgumentException("duration_ticks out of range: " + durationTicks);
            if (catalystCount != null && (catalystCount < 0 || catalystCount > MAX_CATALYSTS)) throw new IllegalArgumentException("catalyst count out of range: " + catalystCount);
        }

        boolean empty() {
            return equals(NONE);
        }
    }

    /** One spell's overrides. */
    record SpellMapping(String sourcePack, ResourceLocation file, @Nullable Boolean enabled, boolean selfOnly,
                        LevelOverride defaults, Map<Integer, LevelOverride> levels) {
        LevelOverride level(int level) {
            return levels.getOrDefault(level, LevelOverride.NONE);
        }
    }

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
    private final Set<ResourceLocation> catalystTags;
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
        this.catalystTags = collectCatalystTags(this.tiers, this.spells);
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
        com.otectus.arsnspells.glyph.behavior.GlyphBehavior.mappingsPublished();
    }

    public static int nextGeneration() {
        return ++generation;
    }

    /** A source file as the loader found it: its id, the pack it came from, the pack's rank (higher wins). */
    public record Source(ResourceLocation id, String pack, int packRank, JsonElement json) {}

    private record Candidate(Source source, SpellMapping mapping) {}

    private record TierCandidate(Source source, PressTerms terms) {}

    /**
     * Build a candidate from every source file. Returns the snapshot and every error found;
     * {@link #errors()} is empty only when every file was valid.
     */
    public static GlyphMappingSnapshot build(List<Source> sources, ImportedGlyphCatalog catalog, int generation) {
        List<String> errors = new ArrayList<>();
        List<String> winners = new ArrayList<>();
        Set<ResourceLocation> allowSpells = new TreeSet<>(), denySpells = new TreeSet<>(),
            allowSchools = new TreeSet<>(), denySchools = new TreeSet<>(), failedClosed = new TreeSet<>();
        if (sources.size() > MAX_FILES) {
            errors.add("too many ans_iron_glyphs files: " + sources.size() + " (at most " + MAX_FILES + " are read)");
            sources = sources.subList(0, MAX_FILES);
        }
        List<Source> ordered = new ArrayList<>(sources);
        ordered.sort((a, b) -> a.packRank() != b.packRank() ? Integer.compare(a.packRank(), b.packRank())
            : a.id().toString().compareTo(b.id().toString()));
        Map<ResourceLocation, List<Candidate>> spellCandidates = new TreeMap<>();
        Map<Integer, List<TierCandidate>> tierCandidates = new TreeMap<>();
        for (Source source : ordered) {
            String where = source.id() + " [" + source.pack() + "]";
            ResourceLocation spellNamed = namedSpell(source.json());
            try {
                if (source.json() == null || !source.json().isJsonObject())
                    throw new MappingJson.Invalid("not a JSON object");
                int schema = MappingJson.whole(source.json().getAsJsonObject().get("schema_version"), "schema_version", 0, Integer.MAX_VALUE);
                if (schema != SCHEMA_VERSION && schema != LEGACY_SCHEMA_VERSION)
                    throw new MappingJson.Invalid("schema_version: " + schema + " is not supported (expected " + SCHEMA_VERSION
                        + ", or " + LEGACY_SCHEMA_VERSION + " for 3.4.0 files)");
                String type = MappingJson.optionalString(source.json().getAsJsonObject(), "type", "ars_n_spells:spell");
                switch (type) {
                    case "ars_n_spells:press_tiers" -> {
                        MappingJson json = MappingJson.object(source.json(), "", TIERS_KEYS);
                        MappingJson tierJson = json.child("tiers", Set.of("1", "2", "3"));
                        for (Map.Entry<String, JsonElement> entry : tierJson.raw().entrySet()) {
                            if (entry.getKey().startsWith("_")) continue;
                            int tier = MappingJson.levelKey(entry.getKey(), tierJson.where(entry.getKey()), 1, 3);
                            PressTerms terms = tierTerms(MappingJson.object(entry.getValue(), tierJson.where(entry.getKey()), TIER_TERMS_KEYS));
                            tierCandidates.computeIfAbsent(tier, ignored -> new ArrayList<>()).add(new TierCandidate(source, terms));
                        }
                    }
                    case "ars_n_spells:policy", "policy" -> {
                        if ("policy".equals(type) && schema != LEGACY_SCHEMA_VERSION)
                            throw new MappingJson.Invalid("type: \"policy\" is the 3.4.0 alias; schema " + SCHEMA_VERSION + " uses \"ars_n_spells:policy\"");
                        MappingJson json = MappingJson.object(source.json(), "", POLICY_KEYS);
                        if (json.has("allow_spells")) allowSpells.addAll(json.ids("allow_spells"));
                        if (json.has("deny_spells")) denySpells.addAll(json.ids("deny_spells"));
                        if (json.has("allow_schools")) allowSchools.addAll(json.ids("allow_schools"));
                        if (json.has("deny_schools")) denySchools.addAll(json.ids("deny_schools"));
                    }
                    case "ars_n_spells:spell" -> {
                        MappingJson json = MappingJson.object(source.json(), "", SPELL_KEYS);
                        ResourceLocation spell = json.id("spell");
                        SpellMapping mapping = spellMapping(source, spell, json, catalog);
                        spellCandidates.computeIfAbsent(spell, ignored -> new ArrayList<>()).add(new Candidate(source, mapping));
                    }
                    default -> throw new MappingJson.Invalid("type: unknown document type \"" + type + "\"");
                }
            } catch (RuntimeException invalid) {
                errors.add(where + ": " + invalid.getMessage());
                if (spellNamed != null) failedClosed.add(spellNamed);
            }
        }
        Map<ResourceLocation, SpellMapping> spells = new LinkedHashMap<>();
        spellCandidates.forEach((spell, candidates) -> {
            int top = candidates.stream().mapToInt(c -> c.source().packRank()).max().orElse(Integer.MIN_VALUE);
            List<Candidate> best = candidates.stream().filter(c -> c.source().packRank() == top).toList();
            if (best.size() > 1) {
                errors.add(spell + " is declared by " + best.stream().map(c -> c.source().id() + " [" + c.source().pack() + "]").toList()
                    + " at the same pack priority; the declarations conflict, so the spell fails closed");
                failedClosed.add(spell);
                return;
            }
            Candidate winner = best.get(0);
            spells.put(spell, winner.mapping());
            winners.add(spell + " <- " + winner.source().id() + " [" + winner.source().pack() + "]"
                + (candidates.size() > 1 ? " (overrides " + (candidates.size() - 1) + " lower-priority file(s))" : ""));
        });
        Map<Integer, PressTerms> tiers = new TreeMap<>(DEFAULT_TIERS);
        tierCandidates.forEach((tier, candidates) -> {
            int top = candidates.stream().mapToInt(c -> c.source().packRank()).max().orElse(Integer.MIN_VALUE);
            List<TierCandidate> best = candidates.stream().filter(c -> c.source().packRank() == top).toList();
            if (best.stream().map(TierCandidate::terms).distinct().count() > 1) {
                errors.add("press tier " + tier + " is declared differently by " + best.stream()
                    .map(c -> c.source().id() + " [" + c.source().pack() + "]").toList() + " at the same pack priority; the shipped terms stay in force");
                return;
            }
            tiers.put(tier, best.get(0).terms());
            winners.add("press tier " + tier + " <- " + best.get(0).source().id() + " [" + best.get(0).source().pack() + "]");
        });
        for (ResourceLocation id : allowSpells) if (catalog.levelsOf(id).isEmpty()) errors.add("policy allows unknown spell " + id);
        for (ResourceLocation id : denySpells) if (catalog.levelsOf(id).isEmpty()) errors.add("policy denies unknown spell " + id);
        failedClosed.forEach(spells::remove);
        return new GlyphMappingSnapshot(tiers, spells, allowSpells, denySpells, allowSchools, denySchools,
            failedClosed, errors, winners, generation);
    }

    private static SpellMapping spellMapping(Source source, ResourceLocation spell, MappingJson json, ImportedGlyphCatalog catalog) {
        List<ImportedGlyphDefinition> levels = catalog.levelsOf(spell);
        if (levels.isEmpty()) throw new MappingJson.Invalid("spell: " + spell + " is not in the imported-glyph catalog");
        boolean selfOnly = false;
        if (json.has("adapter")) {
            ResourceLocation adapter = json.id("adapter");
            GlyphFamily catalogued = levels.get(0).family();
            if (SELF_ADAPTER.equals(adapter)) {
                selfOnly = true;
            } else {
                GlyphFamily named;
                try {
                    named = GlyphFamily.fromAdapterId(adapter.toString());
                } catch (IllegalArgumentException unknown) {
                    throw new MappingJson.Invalid(json.where("adapter") + ": unknown adapter " + adapter);
                }
                if (named != catalogued)
                    throw new MappingJson.Invalid(json.where("adapter") + ": " + spell + " is catalogued with adapter " + catalogued.adapterId()
                        + "; a datapack may confirm it or restrict the spell to " + SELF_ADAPTER + ", never change it");
            }
        }
        Boolean enabled = json.has("enabled") ? json.bool("enabled") : null;
        LevelOverride defaults = json.has("defaults") ? override(json.child("defaults", LEVEL_KEYS)) : LevelOverride.NONE;
        int lowest = levels.get(0).level(), highest = levels.get(levels.size() - 1).level();
        Map<Integer, LevelOverride> byLevel = new TreeMap<>();
        if (json.has("levels")) {
            MappingJson levelsJson = MappingJson.anyKeys(json.raw().get("levels"), json.where("levels"));
            for (Map.Entry<String, JsonElement> entry : levelsJson.raw().entrySet()) {
                if (entry.getKey().startsWith("_")) continue;
                String where = levelsJson.where(entry.getKey());
                int level = MappingJson.levelKey(entry.getKey(), where, 1, com.otectus.arsnspells.glyph.ImportedGlyphKey.MAX_LEVEL);
                if (level < lowest || level > highest)
                    throw new MappingJson.Invalid(where + ": " + spell + " level " + level + " is not a registered glyph (catalog registers "
                        + lowest + "-" + highest + "); a reload cannot add glyph identities");
                byLevel.put(level, override(MappingJson.object(entry.getValue(), where, LEVEL_KEYS)));
            }
        }
        return new SpellMapping(source.pack(), source.id(), enabled, selfOnly, defaults, Collections.unmodifiableMap(byLevel));
    }

    private static LevelOverride override(MappingJson json) {
        Boolean enabled = json.has("enabled") ? json.bool("enabled") : null;
        Integer tier = json.has("ars_tier") ? json.whole("ars_tier", 1, 3) : null;
        Integer cost = json.has("source_cost") ? json.whole("source_cost", 0, MAX_SOURCE_COST) : null;
        Integer ticks = json.has("duration_ticks") ? json.whole("duration_ticks", 1, MAX_DURATION_TICKS) : null;
        ResourceLocation tag = null;
        Integer count = null;
        if (json.has("catalyst")) {
            MappingJson catalyst = json.child("catalyst", CATALYST_KEYS);
            if (catalyst.has("tag")) tag = catalyst.id("tag");
            if (catalyst.has("count")) count = catalyst.whole("count", 0, MAX_CATALYSTS);
        }
        return new LevelOverride(enabled, tier, cost, ticks, tag, count);
    }

    /** A complete tier: Source and duration are required, the catalyst defaults to the shipped tag and none. */
    private static PressTerms tierTerms(MappingJson json) {
        if (!json.has("source_cost") || !json.has("duration_ticks"))
            throw new MappingJson.Invalid(json.path() + ": a press tier needs source_cost and duration_ticks");
        int source = json.whole("source_cost", 0, MAX_SOURCE_COST);
        int duration = json.whole("duration_ticks", 1, MAX_DURATION_TICKS);
        ResourceLocation tag = DEFAULT_CATALYST_TAG;
        int count = 0;
        if (json.has("catalyst")) {
            MappingJson catalyst = json.child("catalyst", CATALYST_KEYS);
            if (catalyst.has("tag")) tag = catalyst.id("tag");
            if (catalyst.has("count")) count = catalyst.whole("count", 0, MAX_CATALYSTS);
        } else {
            throw new MappingJson.Invalid(json.path() + ": a press tier needs a catalyst (use \"count\": 0 for none)");
        }
        return new PressTerms(source, duration, tag, count);
    }

    @Nullable
    private static ResourceLocation namedSpell(JsonElement json) {
        try {
            return ResourceLocation.tryParse(json.getAsJsonObject().get("spell").getAsString());
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** Whether the datapack layer allows this glyph, judged against its catalogued school. */
    public boolean allows(ImportedGlyphDefinition definition) {
        return refusal(definition, definition.school()) == null;
    }

    /** Whether the datapack layer allows this glyph when its spell's effective school is {@code school}. */
    public boolean allows(ImportedGlyphDefinition definition, ResourceLocation school) {
        return refusal(definition, school) == null;
    }

    /** Null when allowed; otherwise which rule refuses it, for diagnostics. An explicit deny wins over everything. */
    @Nullable
    public String refusal(ImportedGlyphDefinition definition, ResourceLocation school) {
        ResourceLocation spell = definition.spell();
        if (failedClosed.contains(spell)) return "a mapping file for this spell is invalid (fails closed)";
        if (denySpells.contains(spell)) return "spell denied by policy";
        if (school != null && denySchools.contains(school)) return "school " + school + " denied by policy";
        if ((!allowSpells.isEmpty() || !allowSchools.isEmpty())
            && !allowSpells.contains(spell) && (school == null || !allowSchools.contains(school))) return "not on the policy allow list";
        SpellMapping mapping = spells.get(spell);
        if (mapping == null) return null;
        if (Boolean.FALSE.equals(mapping.enabled())) return "disabled by its mapping";
        LevelOverride level = mapping.level(definition.level());
        if (level.enabled() != null) return level.enabled() ? null : "level " + definition.level() + " disabled by its mapping";
        if (Boolean.FALSE.equals(mapping.defaults().enabled())) return "disabled by its mapping defaults";
        return null;
    }

    /** Whether a datapack restricted this spell to Self casts. */
    public boolean selfOnly(ResourceLocation spell) {
        SpellMapping mapping = spells.get(spell);
        return mapping != null && mapping.selfOnly();
    }

    public Integer tierOverride(ImportedGlyphDefinition definition) {
        SpellMapping mapping = spells.get(definition.spell());
        if (mapping == null) return null;
        Integer specific = mapping.level(definition.level()).tier();
        return specific != null ? specific : mapping.defaults().tier();
    }

    /** What converting a scroll into this glyph costs at the Press: the tier's terms, then defaults, then the level's own. */
    public PressTerms pressTerms(ImportedGlyphDefinition definition, int effectiveTier) {
        PressTerms base = tiers.getOrDefault(effectiveTier, DEFAULT_TIERS.get(3));
        SpellMapping mapping = spells.get(definition.spell());
        if (mapping == null) return base;
        LevelOverride defaults = mapping.defaults(), level = mapping.level(definition.level());
        if (defaults.empty() && level.empty()) return base;
        return new PressTerms(
            first(level.sourceCost(), defaults.sourceCost(), base.sourceCost()),
            first(level.durationTicks(), defaults.durationTicks(), base.durationTicks()),
            first(level.catalystTag(), defaults.catalystTag(), base.catalystTag()),
            first(level.catalystCount(), defaults.catalystCount(), base.catalystCount()));
    }

    private static <T> T first(@Nullable T a, @Nullable T b, T fallback) {
        return a != null ? a : b != null ? b : fallback;
    }

    /** Every catalyst item tag this snapshot can require: the shipped one, every tier's and every override's. */
    public Set<ResourceLocation> catalystTags() {
        return catalystTags;
    }

    private static Set<ResourceLocation> collectCatalystTags(Map<Integer, PressTerms> tiers, Map<ResourceLocation, SpellMapping> spells) {
        Set<ResourceLocation> tags = new TreeSet<>();
        tags.add(DEFAULT_CATALYST_TAG);
        for (PressTerms terms : tiers.values()) tags.add(terms.catalystTag());
        for (SpellMapping mapping : spells.values()) {
            if (mapping.defaults().catalystTag() != null) tags.add(mapping.defaults().catalystTag());
            for (LevelOverride level : mapping.levels().values()) if (level.catalystTag() != null) tags.add(level.catalystTag());
        }
        return Collections.unmodifiableSet(tags);
    }

    public Map<Integer, PressTerms> tiers() { return tiers; }
    public List<String> errors() { return errors; }
    public List<String> winners() { return winners; }
    public Set<ResourceLocation> failedClosed() { return failedClosed; }
    public String digest() { return serverDigest != null ? serverDigest : digest; }
    public int generation() { return snapshotGeneration; }

    // ---- Client view ------------------------------------------------------------------------

    /** One spell's overrides as the client sees them. */
    public record ClientSpell(@Nullable Boolean enabled, boolean selfOnly, LevelOverride defaults, Map<Integer, LevelOverride> levels) {}

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
            new ClientSpell(mapping.enabled(), mapping.selfOnly(), mapping.defaults(), mapping.levels())));
        return new ClientView(tiers, view, List.copyOf(allowSpells), List.copyOf(denySpells), List.copyOf(allowSchools),
            List.copyOf(denySchools), List.copyOf(failedClosed), digest(), snapshotGeneration);
    }

    /** Rebuild the server's snapshot on a remote client. It keeps the server's digest as its identity. */
    public static GlyphMappingSnapshot fromClientView(ClientView view) {
        Map<ResourceLocation, SpellMapping> spells = new TreeMap<>();
        view.spells().forEach((spell, mapping) -> spells.put(spell, new SpellMapping("server", spell, mapping.enabled(),
            mapping.selfOnly(), mapping.defaults(), new TreeMap<>(mapping.levels()))));
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
        for (SpellMapping mapping : spells.values()) {
            if (mapping.defaults().sourceCost() != null) max = Math.max(max, mapping.defaults().sourceCost());
            for (LevelOverride level : mapping.levels().values()) if (level.sourceCost() != null) max = Math.max(max, level.sourceCost());
        }
        return max;
    }

    private String computeDigest() {
        StringBuilder canonical = new StringBuilder("ans-iron-glyph-behaviour/v" + SCHEMA_VERSION + "\n");
        tiers.forEach((tier, terms) -> canonical.append("tier ").append(tier).append(' ').append(terms).append('\n'));
        spells.forEach((spell, mapping) -> canonical.append("spell ").append(spell).append(' ').append(mapping.enabled())
            .append(' ').append(mapping.selfOnly()).append(' ').append(mapping.defaults()).append(' ').append(mapping.levels()).append('\n'));
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
