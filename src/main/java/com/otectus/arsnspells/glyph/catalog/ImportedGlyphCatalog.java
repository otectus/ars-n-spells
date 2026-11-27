package com.otectus.arsnspells.glyph.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The startup catalog of imported glyphs: which Iron's spells and levels have a registered Ars
 * spell part and glyph item.
 *
 * <p>Read once in the mod constructor, before any registry event, from catalog manifests shipped
 * in mod jars ({@code ans_glyph_catalog/*.json}; ANS ships {@code irons_spellbooks.json}, generated
 * from a runtime inventory by {@code tools/generate_iron_glyph_catalog.py}) and from
 * {@code config/ars_n_spells/glyph_catalogs/*.json} (a pack's persistent catalog, which outlives
 * the addon it describes). It never reads the live Iron's registry, so it is identical with Iron's
 * present, absent, or reconfigured. Server settings and datapacks change whether a glyph is
 * available, never whether its id exists.
 *
 * <p><b>3.4.1 identity rules.</b> Every catalogued level is registered whatever the entry's status:
 * an unsupported or retired spell keeps its identities and is dormant, so changing a status never
 * removes an id. Every spell reserves base levels up to its {@code registered_max}. The catalog ANS
 * ships is required: if it is invalid, exceeds a cap, or has lost an id of the published ledger
 * ({@link #LEDGER}), startup stops with the reason instead of registering a shortened set. An
 * optional manifest that is malformed, collides or would exceed a cap is rejected whole and
 * reported; a later manifest may only extend a spell's registered levels (upwards, or downwards when
 * a native minimum falls), never remove one. A spell's behaviour (its adapter contract) has one owner: a placeholder
 * ({@code adapter_version} 0, never executable) contributes identities only, and only a later version
 * of the same optional catalog may give a placeholder its first contract or move an adapter to a
 * newer contract version (3.4.1 review CATALOG-02).
 *
 * <p>Parsing is strict ({@link CatalogJson}): every field is typed and bounded before it is used,
 * unknown fields are refused, and a contract version this build does not implement registers its
 * identities dormant instead of executing them under an older contract.
 *
 * <p>The catalog is immutable after load. Its {@link #hash()} covers every identity-bearing field
 * and is compared between server and client at login; a mismatch is refused with a readable
 * message rather than left to decode spells into the wrong parts.
 */
public final class ImportedGlyphCatalog {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportedGlyphCatalog.class);

    public static final int SCHEMA_VERSION = 2;
    public static final int LEGACY_SCHEMA_VERSION = 1;
    /** Hard caps: an assembled catalog over them is refused, never truncated. */
    public static final int MAX_SPELLS = 2048;
    public static final int MAX_VARIANTS = 8192;
    public static final int MAX_MANIFEST_BYTES = 2 * 1024 * 1024;
    /** Every identity ANS has published, one part id per line; it may only grow. */
    public static final String LEDGER = "/ars_n_spells/glyph_identity_ledger.txt";

    /** Adapters this build implements; a manifest may only name these. */
    public static final Set<String> BUILTIN_ADAPTERS = Set.of(
        "ars_n_spells:self", "ars_n_spells:projectile", "ars_n_spells:entity", "ars_n_spells:position",
        "ars_n_spells:block", "ars_n_spells:teleport", "ars_n_spells:summon", "ars_n_spells:channel");

    /** The catalog cannot be used; startup must stop with this message. */
    public static final class CatalogException extends IllegalArgumentException {
        public CatalogException(String message) {
            super(message);
        }
    }

    /** One manifest and whether startup depends on it (ANS's own). */
    public record Manifest(String source, String json, boolean required) {}

    private static volatile ImportedGlyphCatalog instance = empty();

    private final List<CatalogEntry> entries;
    private final Map<ResourceLocation, CatalogEntry> bySpell;
    private final Map<ResourceLocation, ImportedGlyphKey> byPartId;
    private final List<ImportedGlyphKey> sortedKeys;
    private final String hash;
    private final List<String> errors;

    private ImportedGlyphCatalog(List<CatalogEntry> entries, List<String> errors) {
        Map<ResourceLocation, CatalogEntry> spells = new TreeMap<>(ResourceLocation::compareNamespaced);
        Map<ResourceLocation, ImportedGlyphKey> parts = new TreeMap<>(ResourceLocation::compareNamespaced);
        for (CatalogEntry entry : entries) {
            spells.put(entry.spellId(), entry);
            for (ImportedGlyphKey key : entry.keys()) {
                ImportedGlyphKey previous = parts.put(key.partId(), key);
                if (previous != null && !previous.equals(key)) throw new CatalogException("glyph id collision " + key.partId());
            }
        }
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.bySpell = Collections.unmodifiableMap(spells);
        this.byPartId = Collections.unmodifiableMap(parts);
        List<ImportedGlyphKey> keys = new ArrayList<>(parts.values());
        Collections.sort(keys);
        this.sortedKeys = List.copyOf(keys);
        this.errors = List.copyOf(errors);
        this.hash = digest(this.entries);
    }

    public static ImportedGlyphCatalog empty() {
        return new ImportedGlyphCatalog(List.of(), List.of());
    }

    /** The catalog this game loaded at startup. */
    public static ImportedGlyphCatalog get() {
        return instance;
    }

    /** Install the startup catalog. Called once from the mod constructor. */
    public static void install(ImportedGlyphCatalog catalog) {
        instance = catalog;
        for (String error : catalog.errors) LOGGER.error("[ImportedGlyphs] catalog: {}", error);
        LOGGER.info("[ImportedGlyphs] catalog loaded: {} spells, {} glyph variants, hash {}",
            catalog.entries.size(), catalog.byPartId.size(), catalog.hash.substring(0, 12));
    }

    /** Build a catalog from optional manifests in a deterministic order (sorted by source name); for tests. */
    public static ImportedGlyphCatalog fromManifests(Map<String, String> manifestsBySource) {
        List<Manifest> manifests = new ArrayList<>();
        new TreeMap<>(manifestsBySource).forEach((source, json) -> manifests.add(new Manifest(source, json, false)));
        return assemble(manifests, Set.of());
    }

    /**
     * Assemble the catalog: required manifests first, then optional ones, each group in source order.
     *
     * @param ledger part ids that must be registered; a missing one stops startup
     */
    public static ImportedGlyphCatalog assemble(List<Manifest> manifests, Set<ResourceLocation> ledger) {
        List<Manifest> ordered = new ArrayList<>(manifests);
        // Required first, then live optional manifests, then ANS's retained copies (3.4.1 review SAVE-01), each
        // group in source order: a retained copy is identities only and must never precede a live declaration.
        ordered.sort(java.util.Comparator.comparingInt(ImportedGlyphCatalog::loadGroup).thenComparing(Manifest::source));
        Map<ResourceLocation, CatalogEntry> spells = new java.util.LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        int variants = 0;
        for (Manifest manifest : ordered) {
            List<String> errors = new ArrayList<>();
            List<CatalogEntry> parsed = new ArrayList<>();
            parse(manifest.source(), manifest.json(), manifest.required(), parsed, errors);
            Map<ResourceLocation, CatalogEntry> candidate = new java.util.LinkedHashMap<>(spells);
            List<String> notes = new ArrayList<>();
            for (CatalogEntry entry : parsed) {
                String refusal = merge(candidate, entry, manifest.source(), notes);
                if (refusal != null) errors.add(refusal);
            }
            int candidateVariants = 0;
            for (CatalogEntry entry : candidate.values()) candidateVariants += entry.registeredMax() - entry.minLevel() + 1;
            if (candidate.size() > MAX_SPELLS) errors.add("the catalog would declare " + candidate.size() + " spells (at most " + MAX_SPELLS + ")");
            if (candidateVariants > MAX_VARIANTS) errors.add("the catalog would register " + candidateVariants + " glyph variants (at most " + MAX_VARIANTS + ")");
            if (!errors.isEmpty()) {
                if (manifest.required())
                    throw new CatalogException("Invalid imported-glyph catalog " + manifest.source() + ": " + String.join("; ", errors));
                problems.add(manifest.source() + ": rejected, nothing registered from it: " + String.join("; ", errors));
                continue;
            }
            spells = candidate;
            variants = candidateVariants;
            for (String note : notes) LOGGER.info("[ImportedGlyphs] catalog {}: {}", manifest.source(), note);
        }
        ImportedGlyphCatalog catalog = new ImportedGlyphCatalog(new ArrayList<>(spells.values()), problems);
        List<String> lost = new ArrayList<>();
        for (ResourceLocation id : ledger) if (!catalog.byPartId.containsKey(id)) lost.add(id.toString());
        if (!lost.isEmpty())
            throw new CatalogException(lost.size() + " published imported-glyph identities are missing from the catalog (first: " + lost.get(0)
                + "). Saved spells containing them would lose those glyphs; restore the catalog that registered them.");
        return catalog;
    }

    /** The published-identity ledger shipped in the jar; empty when absent (tests, development). */
    public static Set<ResourceLocation> ledger() {
        try (java.io.InputStream in = ImportedGlyphCatalog.class.getResourceAsStream(LEDGER)) {
            if (in == null) return Set.of();
            Set<ResourceLocation> ids = new java.util.LinkedHashSet<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                ResourceLocation id = ResourceLocation.tryParse(trimmed);
                if (id == null || ImportedGlyphKey.decode(id).isEmpty())
                    throw new CatalogException("The imported-glyph identity ledger lists an invalid id: " + trimmed);
                ids.add(id);
            }
            return ids;
        } catch (java.io.IOException unreadable) {
            throw new CatalogException("The imported-glyph identity ledger could not be read: " + unreadable);
        }
    }

    /**
     * Merge one declaration into {@code candidate}; null when it merged, else why the manifest must be
     * rejected. Identities only ever grow: the registered levels become the union of both ranges, so a
     * later declaration may extend them upwards or downwards but never remove one (a catalog whose
     * native minimum rose keeps its lower levels registered and dormant). Behaviour has one owner:
     * <ul>
     *   <li>the same execution contract (adapter, adapter version, target, final-effect rule) merges;</li>
     *   <li>a placeholder (adapter version 0) contributes identities only and never replaces anything;</li>
     *   <li>a later version of the <em>same</em> optional catalog may give a placeholder its first
     *       contract, or move the same adapter to a newer contract version (a deliberate, versioned
     *       transition); the required catalog's spells are never re-behaved by another manifest;</li>
     *   <li>anything else would replace a published contract, and the manifest is rejected whole.</li>
     * </ul>
     * The same rules as the Forge build's {@code ImportedGlyphCatalog.merge}.
     */
    @org.jetbrains.annotations.Nullable
    private static String merge(Map<ResourceLocation, CatalogEntry> candidate, CatalogEntry incoming, String source, List<String> notes) {
        CatalogEntry previous = candidate.get(incoming.spellId());
        if (previous == null) {
            candidate.put(incoming.spellId(), incoming);
            return null;
        }
        String merged = previous.sourceCatalog() + " + " + source;
        boolean related = !previous.required() && previous.catalog().equals(incoming.catalog());
        if (previous.sameContract(incoming) || incoming.placeholder()) {
            if (incoming.registeredMax() > previous.registeredMax() || incoming.minLevel() < previous.minLevel())
                candidate.put(incoming.spellId(), previous.unitedWith(incoming, merged));
            return null;
        }
        boolean promotion = previous.placeholder() && related;
        boolean upgrade = related && previous.adapter().equals(incoming.adapter()) && previous.target() == incoming.target()
            && previous.finalEffectOnly() == incoming.finalEffectOnly() && incoming.adapterVersion() > previous.adapterVersion();
        if (promotion || upgrade) {
            candidate.put(incoming.spellId(), incoming.unitedWith(previous, merged));
            notes.add(incoming.spellId() + (promotion ? " promoted from a placeholder to " : " moved to ") + incoming.adapter()
                + " v" + incoming.adapterVersion() + " by a later version of catalog " + incoming.catalog() + "; its identities are unchanged");
            return null;
        }
        return incoming.spellId() + " is already catalogued by " + previous.sourceCatalog() + " with adapter " + previous.adapter()
            + " v" + previous.adapterVersion() + "; this source names " + incoming.adapter() + " v" + incoming.adapterVersion()
            + (related ? ", which would replace a published adapter contract"
                : ", and only a later version of the same optional catalog (" + previous.catalog() + ") may change it");
    }

    private static int loadGroup(Manifest manifest) {
        return manifest.required() ? 0 : manifest.source().startsWith(RetainedCatalogs.SOURCE_PREFIX) ? 2 : 1;
    }

    /** Fields a catalog document may have; anything else rejects the manifest. */
    private static final Set<String> ROOT_FIELDS = Set.of("schema_version", "catalog", "catalog_version", "id_encoding", "digest",
        "provenance", "source", "excluded", "irons_builds", "spells", "_comment");
    private static final Set<String> SPELL_FIELDS = Set.of("spell", "name", "school", "cast_type", "levels", "rarity", "tier", "adapter",
        "adapter_version", "status", "reason", "requires_learning", "enabled_by_default", "irons_versions", "target",
        "final_effect_only", "icon");
    private static final Set<String> LEVEL_FIELDS = Set.of("min", "default_max", "registered_max");
    private static final Set<String> LEGACY_SPELL_FIELDS = Set.of("spell", "name", "school", "cast_type", "min_level", "max_level",
        "rarity_by_level", "rarity", "tier_by_level", "default_tier", "adapter", "requires_learning", "enabled_by_default",
        "irons_versions", "target", "final_effect_only", "status", "reason");
    public static final Set<String> CAST_TYPES = Set.of("INSTANT", "LONG", "CONTINUOUS");
    public static final Set<String> RARITIES = Set.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY");
    public static final Set<String> STATUSES = Set.of("supported", "unsupported", "retired");
    public static final String ID_ENCODING = "ars_n_spells:iron/<spell namespace>/<spell path>/level_<n>";
    /** Bound on {@code adapter_version}: far above any real contract, so a typo is caught rather than registered. */
    public static final int MAX_ADAPTER_VERSION = 1000;

    static void parse(String source, String json, List<CatalogEntry> out, List<String> errors) {
        parse(source, json, false, out, errors);
    }

    static void parse(String source, String json, boolean required, List<CatalogEntry> out, List<String> errors) {
        if (json.length() > MAX_MANIFEST_BYTES) {
            errors.add(source + ": manifest larger than " + MAX_MANIFEST_BYTES + " bytes, ignored");
            return;
        }
        JsonObject root;
        int schema;
        String catalogName;
        String version;
        JsonArray spells;
        try {
            root = CatalogJson.parseObject(json);
            CatalogJson.onlyFields(root, ROOT_FIELDS, "");
            schema = CatalogJson.integer(root, "schema_version", "", 0, Integer.MAX_VALUE);
            if (schema != SCHEMA_VERSION && schema != LEGACY_SCHEMA_VERSION)
                throw new IllegalArgumentException("schema_version " + schema + " is not " + SCHEMA_VERSION + " (or " + LEGACY_SCHEMA_VERSION
                    + "); manifest ignored, nothing registered from it");
            String encoding = CatalogJson.optionalString(root, "id_encoding", "", 256);
            if (encoding != null && !ID_ENCODING.equals(encoding)) throw new IllegalArgumentException("unsupported id encoding " + encoding);
            String declaredVersion = CatalogJson.optionalString(root, "catalog_version", "", 64);
            version = declaredVersion == null ? source : declaredVersion;
            JsonArray builds = CatalogJson.optionalArray(root, "irons_builds", "", 64);
            if (builds != null) for (JsonElement build : builds) CatalogJson.stringValue(build, "'irons_builds' entry", 64);
            String named = CatalogJson.optionalString(root, "catalog", "", 128);
            catalogName = named == null ? source : named;
            spells = CatalogJson.array(root, "spells", "", MAX_SPELLS);
        } catch (RuntimeException invalid) {
            errors.add(source + ": " + invalid.getMessage());
            return;
        }
        java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
        for (int index = 0; index < spells.size(); index++) {
            String where = "spells[" + index + "]: ";
            try {
                JsonElement element = spells.get(index);
                if (!element.isJsonObject()) throw new IllegalArgumentException(where + "must be an object");
                JsonObject spell = element.getAsJsonObject();
                ResourceLocation spellId = CatalogJson.id(spell, "spell", where);
                where = spellId + ": ";
                if (!seen.add(spellId)) throw new IllegalArgumentException(where + "duplicate spell in this source");
                if ("ars_n_spells".equals(spellId.getNamespace())) throw new IllegalArgumentException(where + "ANS's own proxy spells are never imported");
                out.add(entry(source, spellId, spell, where, schema, catalogName, version, required));
            } catch (RuntimeException invalid) {
                errors.add(source + ": " + (invalid.getMessage() == null ? where + invalid : invalid.getMessage()));
            }
        }
    }

    private static CatalogEntry entry(String source, ResourceLocation spell, JsonObject o, String where, int schema, String catalogName,
                                      String version, boolean required) {
        int cap = ImportedGlyphKey.MAX_LEVEL;
        int min, max, registered;
        List<String> rarity;
        List<Integer> tiers;
        String status = "supported";
        int adapterVersion = 1;
        ResourceLocation icon = null;
        if (schema == LEGACY_SCHEMA_VERSION) {
            CatalogJson.onlyFields(o, LEGACY_SPELL_FIELDS, where);
            min = CatalogJson.integer(o, "min_level", where, 1, cap, 1);
            max = CatalogJson.integer(o, "max_level", where, min, cap);
            registered = max;
            String rarityField = o.has("rarity_by_level") ? "rarity_by_level" : "rarity";
            String tierField = o.has("tier_by_level") ? "tier_by_level" : "default_tier";
            rarity = rarities(CatalogJson.array(o, rarityField, where, cap), rarityField, max - min + 1, where);
            tiers = tiers(CatalogJson.array(o, tierField, where, cap), tierField, max - min + 1, where);
            status = status(o, where);
            CatalogJson.optionalString(o, "reason", where, 512);
        } else {
            CatalogJson.onlyFields(o, SPELL_FIELDS, where);
            JsonObject levels = CatalogJson.object(o, "levels", where);
            CatalogJson.onlyFields(levels, LEVEL_FIELDS, where + "levels: ");
            min = CatalogJson.integer(levels, "min", where + "levels: ", 1, cap, 1);
            max = CatalogJson.integer(levels, "default_max", where + "levels: ", min, cap);
            registered = CatalogJson.integer(levels, "registered_max", where + "levels: ", max, cap, max);
            rarity = rarities(CatalogJson.array(o, "rarity", where, cap), "rarity", max - min + 1, where);
            tiers = tiers(CatalogJson.array(o, "tier", where, cap), "tier", max - min + 1, where);
            status = status(o, where);
            adapterVersion = CatalogJson.integer(o, "adapter_version", where, 0, MAX_ADAPTER_VERSION, 1);
            if (adapterVersion == 0 && "supported".equals(status))
                throw new IllegalArgumentException(where + "adapter_version 0 marks a placeholder without an adapter contract, so its status "
                    + "must be unsupported or retired");
            CatalogJson.optionalString(o, "reason", where, 512);
            String iconText = CatalogJson.optionalString(o, "icon", where, 256);
            if (iconText != null) {
                icon = CatalogJson.idValue(iconText, where + "'icon'");
                if (!icon.getPath().startsWith("textures/") || !icon.getPath().endsWith(".png"))
                    throw new IllegalArgumentException(where + "'icon' must name a texture file such as addon:textures/gui/spell_icons/x.png");
            }
        }
        String castType = CatalogJson.string(o, "cast_type", where, 16);
        if (!CAST_TYPES.contains(castType))
            throw new IllegalArgumentException(where + "'cast_type' must be INSTANT, LONG or CONTINUOUS (found " + castType + ")");
        ResourceLocation adapter = CatalogJson.id(o, "adapter", where);
        if (!BUILTIN_ADAPTERS.contains(adapter.toString()))
            throw new IllegalArgumentException(where + "adapter " + adapter + " is not one this build implements");
        String targetText = CatalogJson.string(o, "target", where, 32);
        CatalogEntry.TargetKind target;
        try {
            target = CatalogEntry.TargetKind.parse(targetText);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(where + "'target' " + unknown.getMessage());
        }
        JsonArray versions = CatalogJson.optionalArray(o, "irons_versions", where, 64);
        if (versions != null) for (JsonElement build : versions) CatalogJson.stringValue(build, where + "'irons_versions' entry", 64);
        return new CatalogEntry(spell, CatalogJson.string(o, "name", where, 256), CatalogJson.id(o, "school", where), castType, min, max,
            registered, rarity, tiers, CatalogJson.bool(o, "requires_learning", where, false), CatalogJson.bool(o, "enabled_by_default", where, true),
            adapter, target, CatalogJson.bool(o, "final_effect_only", where, false), status, adapterVersion, icon, catalogName, version, required,
            source);
    }

    private static String status(JsonObject o, String where) {
        String named = CatalogJson.optionalString(o, "status", where, 16);
        if (named == null) return "supported";
        if (!STATUSES.contains(named))
            throw new IllegalArgumentException(where + "'status' must be supported, unsupported or retired (found " + named + ")");
        return named;
    }

    private static List<String> rarities(JsonArray list, String field, int count, String where) {
        if (list.size() != count)
            throw new IllegalArgumentException(where + "'" + field + "' lists " + list.size() + " levels; the default levels need " + count);
        List<String> out = new ArrayList<>(count);
        for (JsonElement rarity : list) {
            String value = CatalogJson.stringValue(rarity, where + "'" + field + "' entry", 16);
            if (!RARITIES.contains(value))
                throw new IllegalArgumentException(where + "'" + field + "' entry " + value + " is not one of " + new java.util.TreeSet<>(RARITIES));
            out.add(value);
        }
        return out;
    }

    private static List<Integer> tiers(JsonArray list, String field, int count, String where) {
        if (list.size() != count)
            throw new IllegalArgumentException(where + "'" + field + "' lists " + list.size() + " levels; the default levels need " + count);
        List<Integer> out = new ArrayList<>(count);
        for (JsonElement tier : list) out.add(CatalogJson.integerValue(tier, where + "'" + field + "' entry", 1, 3));
        return out;
    }

    private static String digest(List<CatalogEntry> entries) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            List<CatalogEntry> sorted = new ArrayList<>(entries);
            sorted.sort((a, b) -> a.spellId().compareNamespaced(b.spellId()));
            for (CatalogEntry e : sorted) {
                String line = e.spellId() + "|" + e.minLevel() + "|" + e.maxLevel() + "|" + e.registeredMax() + "|" + e.adapter()
                    + "|" + e.target() + "|" + e.school() + "|" + e.castType() + "|" + e.status() + "|" + e.finalEffectOnly()
                    + "|" + e.defaultTier() + "\n";
                md.update(line.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public List<CatalogEntry> entries() {
        return entries;
    }

    @Nullable
    public CatalogEntry entry(ResourceLocation spellId) {
        return bySpell.get(spellId);
    }

    /** The key a registered part id stands for, if it is one of this catalog's glyphs. */
    @Nullable
    public ImportedGlyphKey keyForPart(ResourceLocation partId) {
        return byPartId.get(partId);
    }

    public boolean contains(ImportedGlyphKey key) {
        return byPartId.containsKey(key.partId());
    }

    /** Every registered key, in the stable order registration uses; computed once. */
    public List<ImportedGlyphKey> keys() {
        return sortedKeys;
    }

    public int variantCount() {
        return byPartId.size();
    }

    public String hash() {
        return hash;
    }

    public List<String> errors() {
        return errors;
    }

    /** Spells by school, for diagnostics. */
    public Map<ResourceLocation, List<CatalogEntry>> bySchool() {
        Map<ResourceLocation, List<CatalogEntry>> out = new LinkedHashMap<>();
        for (CatalogEntry e : entries) out.computeIfAbsent(e.school(), k -> new ArrayList<>()).add(e);
        return out;
    }
}
