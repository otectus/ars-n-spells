package com.otectus.arsnspells.glyph.behavior;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
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
 * The reloadable data-pack layer over the startup catalog: per-spell availability and tuning,
 * Press tier terms, and a pack-wide allow/deny policy.
 *
 * <h2>Location and format (canonical schema 2, shared with the Forge build)</h2>
 * {@code data/<namespace>/ans_iron_glyphs/<any path>.json}, chosen by {@code "type"}:
 * <ul>
 *   <li>{@code ars_n_spells:spell} (the default): {@code spell}; optional {@code enabled};
 *       optional {@code adapter}, the catalogued adapter (a confirmation) or
 *       {@code ars_n_spells:self} (a restriction to Self casts); optional {@code defaults} and
 *       {@code levels} whose entries take {@code enabled}, {@code ars_tier}, {@code source_cost},
 *       {@code duration_ticks} and {@code catalyst} ({@code tag}, {@code count}, 0 for none);</li>
 *   <li>{@code ars_n_spells:press_tiers}: Source, duration and catalyst per conversion tier;</li>
 *   <li>{@code ars_n_spells:policy} ({@code "policy"} in 3.4.0 files): spell and school allow and deny lists.</li>
 * </ul>
 * Schema 1 files of either loader's 3.4.0 dialect are read through the same rules. Every number is
 * checked for type, finiteness, integrality and bounds before narrowing; unknown fields are errors
 * with their path (fields starting with {@code _} are comments).
 *
 * <h2>Precedence</h2>
 * <ul>
 *   <li>For one file path, the highest-priority pack's copy is read (ordinary data-pack override).</li>
 *   <li>For each spell (and each press tier), the declaration from the highest-priority pack wins;
 *       two different declarations at that same priority conflict, are reported, and the spell
 *       fails closed. Policies from every file are merged.</li>
 *   <li>An explicit deny (spell or school) always wins. When any allow list is non-empty, only
 *       allowed spells (by id or school) are available. A pack can never enable a spell Iron's own
 *       config disables.</li>
 *   <li>Level overrides beat {@code defaults}, which beat the tier terms.</li>
 * </ul>
 *
 * <h2>Validation</h2>
 * Every spell and level must already have a startup identity. A file that fails validation is
 * rejected whole. On a reload, any rejected file rejects the whole candidate and the last valid
 * mappings stay in force; at first load, spells named by a rejected file fail closed.
 */
public final class GlyphMappings {

    public static final String DIRECTORY = "ans_iron_glyphs";
    public static final int SCHEMA_VERSION = 2;
    public static final int LEGACY_SCHEMA_VERSION = 1;
    public static final int MAX_FILES = 2048;
    public static final int MAX_SOURCE_COST = 100_000;
    public static final int MAX_DURATION_TICKS = 72_000;
    public static final int MAX_CATALYST_COUNT = 64;
    public static final String SELF_ADAPTER = "ars_n_spells:self";

    private static final Set<String> SPELL_KEYS = Set.of("schema_version", "type", "spell", "enabled", "adapter", "defaults", "levels");
    private static final Set<String> LEVEL_KEYS = Set.of("enabled", "ars_tier", "source_cost", "duration_ticks", "catalyst");
    private static final Set<String> CATALYST_KEYS = Set.of("tag", "count");
    private static final Set<String> TIERS_KEYS = Set.of("schema_version", "type", "tiers");
    private static final Set<String> TIER_TERMS_KEYS = Set.of("source_cost", "duration_ticks", "catalyst");
    private static final Set<String> POLICY_KEYS = Set.of("schema_version", "type", "allow_spells", "deny_spells", "allow_schools", "deny_schools");

    /** One file as a pack supplied it; {@code rank} is the pack's position (higher wins). */
    public record Source(ResourceLocation file, String pack, int rank, JsonElement json) {
        public Source(ResourceLocation file, String pack, JsonElement json) {
            this(file, pack, 0, json);
        }
    }

    /** Optional fields; null means "not set here". */
    public record LevelOverride(@Nullable Boolean enabled, @Nullable Integer tier, @Nullable Integer sourceCost,
                                @Nullable Integer durationTicks, @Nullable String catalystTag, @Nullable Integer catalystCount) {
        static final LevelOverride NONE = new LevelOverride(null, null, null, null, null, null);
    }

    /** One conversion tier's complete terms. */
    public record TierTerms(int sourceCost, int durationTicks, String catalystTag, int catalystCount) {}

    public record SpellMapping(ResourceLocation spell, @Nullable ResourceLocation adapter, @Nullable Boolean enabled,
                               LevelOverride defaults, Map<Integer, LevelOverride> levels,
                               ResourceLocation file, String pack) {
        public boolean selfOnly() {
            return adapter != null && SELF_ADAPTER.equals(adapter.toString());
        }
    }

    public record Policy(Set<ResourceLocation> allowSpells, Set<ResourceLocation> denySpells,
                         Set<ResourceLocation> allowSchools, Set<ResourceLocation> denySchools) {
        public static final Policy OPEN = new Policy(Set.of(), Set.of(), Set.of(), Set.of());

        /** Null when allowed, otherwise why not. */
        @Nullable
        public String refusal(ResourceLocation spell, ResourceLocation school) {
            if (denySpells.contains(spell)) return "spell denied by pack policy";
            if (school != null && denySchools.contains(school)) return "school denied by pack policy";
            boolean restricted = !allowSpells.isEmpty() || !allowSchools.isEmpty();
            if (restricted && !allowSpells.contains(spell) && (school == null || !allowSchools.contains(school))) return "not on the pack allow list";
            return null;
        }
    }

    /** The parsed layer: what applies, what was rejected and why. {@code tiers} holds only the overridden tiers. */
    public record Result(Map<ResourceLocation, SpellMapping> mappings, Policy policy, List<String> errors,
                         Set<ResourceLocation> failedClosed, Map<ResourceLocation, String> winners, Map<Integer, TierTerms> tiers) {
        public static final Result EMPTY = new Result(Map.of(), Policy.OPEN, List.of(), Set.of(), Map.of(), Map.of());

        public Result(Map<ResourceLocation, SpellMapping> mappings, Policy policy, List<String> errors,
                      Set<ResourceLocation> failedClosed, Map<ResourceLocation, String> winners) {
            this(mappings, policy, errors, failedClosed, winners, Map.of());
        }

        public boolean valid() {
            return errors.isEmpty();
        }
    }

    private GlyphMappings() {
    }

    private record Candidate(Source source, SpellMapping mapping) {}

    private record TierCandidate(Source source, TierTerms terms) {}

    /** Parse every file. Pure: unit-tested without a server. Sources must already be one per file path. */
    public static Result parse(List<Source> sources, ImportedGlyphCatalog catalog) {
        List<String> errors = new ArrayList<>();
        List<Source> sorted = new ArrayList<>(sources);
        if (sorted.size() > MAX_FILES) {
            errors.add("too many ans_iron_glyphs files: " + sorted.size() + " (at most " + MAX_FILES + " are read)");
            sorted = new ArrayList<>(sorted.subList(0, MAX_FILES));
        }
        sorted.sort((a, b) -> a.rank() != b.rank() ? Integer.compare(a.rank(), b.rank()) : a.file().compareNamespaced(b.file()));
        Map<ResourceLocation, List<Candidate>> spellCandidates = new TreeMap<>(ResourceLocation::compareNamespaced);
        Map<Integer, List<TierCandidate>> tierCandidates = new TreeMap<>();
        Set<ResourceLocation> allowSpells = new TreeSet<>(ResourceLocation::compareNamespaced);
        Set<ResourceLocation> denySpells = new TreeSet<>(ResourceLocation::compareNamespaced);
        Set<ResourceLocation> allowSchools = new TreeSet<>(ResourceLocation::compareNamespaced);
        Set<ResourceLocation> denySchools = new TreeSet<>(ResourceLocation::compareNamespaced);
        Set<ResourceLocation> failedClosed = new TreeSet<>(ResourceLocation::compareNamespaced);
        for (Source source : sorted) {
            String where = source.file() + " (pack " + source.pack() + ")";
            ResourceLocation named = namedSpell(source.json());
            try {
                if (source.json() == null || !source.json().isJsonObject()) throw new MappingJson.Invalid("not a JSON object");
                JsonObject o = source.json().getAsJsonObject();
                int schema = MappingJson.whole(o.get("schema_version"), "schema_version", 0, Integer.MAX_VALUE);
                if (schema != SCHEMA_VERSION && schema != LEGACY_SCHEMA_VERSION) {
                    throw new MappingJson.Invalid("schema_version: " + schema + " is not supported (expected " + SCHEMA_VERSION
                        + ", or " + LEGACY_SCHEMA_VERSION + " for 3.4.0 files)");
                }
                String type = MappingJson.optionalString(o, "type", "ars_n_spells:spell");
                switch (type) {
                    case "ars_n_spells:policy", "policy" -> {
                        if ("policy".equals(type) && schema != LEGACY_SCHEMA_VERSION)
                            throw new MappingJson.Invalid("type: \"policy\" is the 3.4.0 alias; schema " + SCHEMA_VERSION + " uses \"ars_n_spells:policy\"");
                        MappingJson json = MappingJson.object(o, "", POLICY_KEYS);
                        if (json.has("allow_spells")) allowSpells.addAll(json.ids("allow_spells"));
                        if (json.has("deny_spells")) denySpells.addAll(json.ids("deny_spells"));
                        if (json.has("allow_schools")) allowSchools.addAll(json.ids("allow_schools"));
                        if (json.has("deny_schools")) denySchools.addAll(json.ids("deny_schools"));
                    }
                    case "ars_n_spells:press_tiers" -> {
                        MappingJson json = MappingJson.object(o, "", TIERS_KEYS);
                        MappingJson tiers = json.child("tiers", Set.of("1", "2", "3"));
                        for (var entry : tiers.raw().entrySet()) {
                            if (entry.getKey().startsWith("_")) continue;
                            int tier = MappingJson.levelKey(entry.getKey(), tiers.where(entry.getKey()), 1, 3);
                            TierTerms terms = tierTerms(MappingJson.object(entry.getValue(), tiers.where(entry.getKey()), TIER_TERMS_KEYS));
                            tierCandidates.computeIfAbsent(tier, k -> new ArrayList<>()).add(new TierCandidate(source, terms));
                        }
                    }
                    case "ars_n_spells:spell" -> {
                        MappingJson json = MappingJson.object(o, "", SPELL_KEYS);
                        SpellMapping mapping = spellMapping(source, json, catalog);
                        spellCandidates.computeIfAbsent(mapping.spell(), k -> new ArrayList<>()).add(new Candidate(source, mapping));
                    }
                    default -> throw new MappingJson.Invalid("type: unknown document type \"" + type + "\"");
                }
            } catch (RuntimeException invalid) {
                errors.add(where + ": " + invalid.getMessage());
                if (named != null) failedClosed.add(named);
            }
        }
        Map<ResourceLocation, SpellMapping> mappings = new TreeMap<>(ResourceLocation::compareNamespaced);
        Map<ResourceLocation, String> winners = new TreeMap<>(ResourceLocation::compareNamespaced);
        spellCandidates.forEach((spell, candidates) -> {
            int top = candidates.stream().mapToInt(c -> c.source().rank()).max().orElse(Integer.MIN_VALUE);
            List<Candidate> best = candidates.stream().filter(c -> c.source().rank() == top).toList();
            if (best.size() > 1) {
                errors.add(spell + " is declared by " + best.stream().map(c -> c.source().file() + " (pack " + c.source().pack() + ")").toList()
                    + " at the same pack priority; the declarations conflict, so the spell fails closed");
                failedClosed.add(spell);
                return;
            }
            mappings.put(spell, best.get(0).mapping());
            winners.put(spell, best.get(0).source().file() + " (pack " + best.get(0).source().pack() + ")"
                + (candidates.size() > 1 ? ", overriding " + (candidates.size() - 1) + " lower-priority file(s)" : ""));
        });
        Map<Integer, TierTerms> tiers = new TreeMap<>();
        tierCandidates.forEach((tier, candidates) -> {
            int top = candidates.stream().mapToInt(c -> c.source().rank()).max().orElse(Integer.MIN_VALUE);
            List<TierCandidate> best = candidates.stream().filter(c -> c.source().rank() == top).toList();
            if (best.stream().map(TierCandidate::terms).distinct().count() > 1) {
                errors.add("press tier " + tier + " is declared differently by " + best.stream()
                    .map(c -> c.source().file() + " (pack " + c.source().pack() + ")").toList() + " at the same pack priority; the shipped terms stay in force");
                return;
            }
            tiers.put(tier, best.get(0).terms());
        });
        for (ResourceLocation id : allowSpells) if (catalog.entry(id) == null) errors.add("policy allows unknown spell " + id);
        for (ResourceLocation id : denySpells) if (catalog.entry(id) == null) errors.add("policy denies unknown spell " + id);
        failedClosed.forEach(mappings::remove);
        return new Result(Collections.unmodifiableMap(mappings),
            new Policy(Set.copyOf(allowSpells), Set.copyOf(denySpells), Set.copyOf(allowSchools), Set.copyOf(denySchools)),
            List.copyOf(errors), Collections.unmodifiableSet(failedClosed), Collections.unmodifiableMap(winners),
            Collections.unmodifiableMap(tiers));
    }

    private static SpellMapping spellMapping(Source source, MappingJson json, ImportedGlyphCatalog catalog) {
        ResourceLocation spell = json.id("spell");
        CatalogEntry entry = catalog.entry(spell);
        if (entry == null) throw new MappingJson.Invalid("spell: " + spell + " has no identity in the startup catalog");
        ResourceLocation adapter = json.has("adapter") ? json.id("adapter") : null;
        if (adapter != null && !adapter.equals(entry.adapter()) && !SELF_ADAPTER.equals(adapter.toString())) {
            throw new MappingJson.Invalid(json.where("adapter") + ": adapter " + adapter + " is not available for " + spell
                + " (use " + entry.adapter() + " or " + SELF_ADAPTER + ")");
        }
        Boolean enabled = json.has("enabled") ? json.bool("enabled") : null;
        LevelOverride defaults = json.has("defaults") ? override(json.child("defaults", LEVEL_KEYS)) : LevelOverride.NONE;
        Map<Integer, LevelOverride> levels = new LinkedHashMap<>();
        if (json.has("levels")) {
            MappingJson levelsJson = MappingJson.anyKeys(json.raw().get("levels"), json.where("levels"));
            for (var level : levelsJson.raw().entrySet()) {
                if (level.getKey().startsWith("_")) continue;
                String where = levelsJson.where(level.getKey());
                int n = MappingJson.levelKey(level.getKey(), where, 1, ImportedGlyphKey.MAX_LEVEL);
                if (n < entry.minLevel() || n > entry.registeredMax()) {
                    throw new MappingJson.Invalid(where + ": level " + n + " of " + spell + " has no startup identity (catalog levels "
                        + entry.minLevel() + ".." + entry.registeredMax() + "); a reload cannot add glyph variants");
                }
                levels.put(n, override(MappingJson.object(level.getValue(), where, LEVEL_KEYS)));
            }
        }
        return new SpellMapping(spell, adapter, enabled, defaults, Collections.unmodifiableMap(levels),
            source.file(), source.pack());
    }

    private static LevelOverride override(MappingJson o) {
        Boolean enabled = o.has("enabled") ? o.bool("enabled") : null;
        Integer tier = o.has("ars_tier") ? o.whole("ars_tier", 1, 3) : null;
        Integer cost = o.has("source_cost") ? o.whole("source_cost", 0, MAX_SOURCE_COST) : null;
        Integer ticks = o.has("duration_ticks") ? o.whole("duration_ticks", 1, MAX_DURATION_TICKS) : null;
        String tag = null;
        Integer count = null;
        if (o.has("catalyst")) {
            MappingJson catalyst = o.child("catalyst", CATALYST_KEYS);
            if (catalyst.has("tag")) tag = catalyst.id("tag").toString();
            if (catalyst.has("count")) count = catalyst.whole("count", 0, MAX_CATALYST_COUNT);
        }
        return new LevelOverride(enabled, tier, cost, ticks, tag, count);
    }

    private static TierTerms tierTerms(MappingJson o) {
        if (!o.has("source_cost") || !o.has("duration_ticks") || !o.has("catalyst"))
            throw new MappingJson.Invalid(o.path() + ": a press tier needs source_cost, duration_ticks and catalyst (count 0 for none)");
        MappingJson catalyst = o.child("catalyst", CATALYST_KEYS);
        return new TierTerms(o.whole("source_cost", 0, MAX_SOURCE_COST), o.whole("duration_ticks", 1, MAX_DURATION_TICKS),
            catalyst.has("tag") ? catalyst.id("tag").toString() : VariantBehavior.DEFAULT_CATALYST,
            catalyst.has("count") ? catalyst.whole("count", 0, MAX_CATALYST_COUNT) : 0);
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
