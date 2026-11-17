package com.otectus.arsnspells.glyph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The startup catalog: every imported glyph identity this game registers, assembled once at mod
 * construction and never changed afterwards.
 *
 * <p><b>Sources.</b> The catalog ANS ships for Iron's own spells ({@link #RESOURCE}, required), then
 * every {@code ans_glyph_catalog/*.json} manifest in a loaded mod file, then every
 * {@code config/ars_n_spells/glyph_catalogs/*.json} pack catalog, each group in name order. A pack
 * catalog in the config folder is how a modpack keeps an addon's identities registered after the
 * addon itself is removed: a manifest that lives only inside the removed jar cannot.
 *
 * <p><b>Identity versus behaviour.</b> The catalog decides which glyph ids exist. It does not decide
 * which are usable: availability, tiers and conversion costs come from the reloadable behaviour
 * layer and Iron's live configuration. Every catalogued level is registered whatever its status; a
 * retired or unsupported identity is registered and dormant, never removed.
 *
 * <p><b>Validation, before anything is registered.</b> The required catalog must be valid, inside the
 * caps, and contain every identity in the published ledger ({@link #LEDGER}); otherwise startup fails
 * with the reason rather than booting with a shortened identity set. An optional contribution that
 * is malformed, collides or would exceed a cap is rejected whole and reported; it can never remove
 * or reassign an identity another source registered. A second source may only <em>extend</em> a
 * spell's registered levels, which is how a catalog update reserves levels for a server that raises
 * Iron's maximum, or downwards when a native minimum falls; no source can remove one. A spell's behaviour (its adapter
 * contract) has one owner: a placeholder ({@code adapter_version} 0, never executable) contributes
 * identities only, and only a later version of the same optional catalog may give a placeholder
 * its first contract or move an adapter to a newer contract version (3.4.1 review CATALOG-02).
 *
 * <p>Parsing is strict ({@link CatalogJson}): every field is typed and bounded before it is used,
 * unknown fields are refused, and a contract version this build does not implement registers its
 * identities dormant instead of executing them under an older contract.
 *
 * <p>{@link #identityDigest()} covers exactly the registered ids. A client and server with
 * different digests would decode the same saved spell differently, so the handshake refuses it.
 */
public final class ImportedGlyphCatalog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportedGlyphCatalog.class);
    public static final String RESOURCE = "/ars_n_spells/iron_glyph_catalog.json";
    /** Every identity ANS has ever published, one part id per line; it may only grow. */
    public static final String LEDGER = "/ars_n_spells/glyph_identity_ledger.txt";
    public static final String MANIFEST_DIRECTORY = "ans_glyph_catalog";
    public static final String CONFIG_DIRECTORY = "ars_n_spells/glyph_catalogs";
    public static final int SCHEMA_VERSION = 2;
    public static final int LEGACY_SCHEMA_VERSION = 1;
    public static final String ID_ENCODING = "ars_n_spells:iron/<spell namespace>/<spell path>/level_<n>";
    /** Bounds on what the assembled catalog may declare; the shipped one is far inside both. */
    public static final int MAX_SPELLS = 1024;
    public static final int MAX_VARIANTS = 4096;
    public static final int MAX_SOURCE_BYTES = 2 * 1024 * 1024;

    /** The catalog cannot be used; startup must stop with this message. */
    public static final class CatalogException extends IllegalArgumentException {
        public CatalogException(String message) {
            super(message);
        }
    }

    /** One catalog document and where it came from. A required source that fails stops startup. */
    public record Source(String name, String json, boolean required) {}

    /** What happened to one source, for diagnostics. */
    public record SourceReport(String name, boolean required, int spells, int glyphs, List<String> errors) {}

    private static volatile ImportedGlyphCatalog loaded;

    private final String catalogVersion;
    private final List<String> ironsBuilds;
    private final Map<ResourceLocation, ImportedGlyphDefinition> byGlyphId;
    private final Map<ResourceLocation, List<ImportedGlyphDefinition>> bySpell;
    private final String identityDigest;
    private final List<SourceReport> sources;
    private final Map<ResourceLocation, Provenance> provenance;

    /**
     * Where a spell's identities came from (3.4.1 review SAVE-01): the catalog lineage that first
     * declared it, that declaration's version, and every source that contributed, in load order. A
     * world records this so a missing-package report can say what to reinstall.
     */
    public record Provenance(String catalog, String version, List<String> sources) {
        public Provenance {
            sources = List.copyOf(sources);
        }
    }

    private ImportedGlyphCatalog(String catalogVersion, List<String> ironsBuilds, List<ImportedGlyphDefinition> definitions,
                                 List<SourceReport> sources, Map<ResourceLocation, Provenance> provenance) {
        this.catalogVersion = catalogVersion;
        this.ironsBuilds = List.copyOf(ironsBuilds);
        Map<ResourceLocation, ImportedGlyphDefinition> ids = new LinkedHashMap<>();
        Map<ResourceLocation, List<ImportedGlyphDefinition>> spells = new TreeMap<>();
        List<ImportedGlyphDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort((a, b) -> a.key().compareTo(b.key()));
        for (ImportedGlyphDefinition definition : sorted) {
            if (ids.put(definition.glyphId(), definition) != null)
                throw new CatalogException("Duplicate glyph id " + definition.glyphId());
            spells.computeIfAbsent(definition.spell(), ignored -> new ArrayList<>()).add(definition);
        }
        spells.replaceAll((spell, list) -> List.copyOf(list));
        this.byGlyphId = Collections.unmodifiableMap(ids);
        this.bySpell = Collections.unmodifiableMap(spells);
        this.identityDigest = digest(sorted);
        this.sources = List.copyOf(sources);
        this.provenance = Collections.unmodifiableMap(new TreeMap<>(provenance));
    }

    /** The catalog this game registered. Assembled on first use; a required failure throws {@link CatalogException}. */
    public static ImportedGlyphCatalog get() {
        ImportedGlyphCatalog current = loaded;
        if (current == null) {
            synchronized (ImportedGlyphCatalog.class) {
                current = loaded;
                if (current == null) loaded = current = loadAll();
            }
        }
        return current;
    }

    private static ImportedGlyphCatalog loadAll() {
        List<Source> sources = new ArrayList<>();
        sources.add(new Source("ars_n_spells" + RESOURCE, bundled(), true));
        List<Source> live = CatalogSources.discover();
        sources.addAll(live);
        // 3.4.1 review SAVE-01: every catalog ever accepted, read back as identities only, after every live source.
        java.nio.file.Path archive = RetainedCatalogs.directory();
        sources.addAll(RetainedCatalogs.sources(archive, live));
        ImportedGlyphCatalog catalog = assemble(sources, ledger());
        RetainedCatalogs.retain(archive, live, catalog);
        for (SourceReport report : catalog.sources) {
            if (report.errors().isEmpty())
                LOGGER.info("Imported-glyph catalog source {}: {} spells, {} glyphs", report.name(), report.spells(), report.glyphs());
            else for (String error : report.errors()) LOGGER.error("Imported-glyph catalog source {}: {}", report.name(), error);
        }
        LOGGER.info("Imported-glyph catalog {}: {} spells, {} glyphs, identity {}", catalog.catalogVersion,
            catalog.bySpell.size(), catalog.byGlyphId.size(), catalog.identityDigest.substring(0, 16));
        return catalog;
    }

    private static String bundled() {
        try (InputStream in = ImportedGlyphCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new CatalogException("The imported-glyph catalog " + RESOURCE + " is missing from the Ars 'n' Spells jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException unreadable) {
            throw new CatalogException("The imported-glyph catalog " + RESOURCE + " could not be read: " + unreadable);
        }
    }

    /** The published-identity ledger shipped in the jar; empty when absent (tests, development). */
    public static Set<ResourceLocation> ledger() {
        try (InputStream in = ImportedGlyphCatalog.class.getResourceAsStream(LEDGER)) {
            if (in == null) return Set.of();
            Set<ResourceLocation> ids = new LinkedHashSet<>();
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

    public static ImportedGlyphCatalog empty() {
        return new ImportedGlyphCatalog("empty", List.of(), List.of(), List.of(), Map.of());
    }

    /** Parse and fully validate one required catalog document. Throws with every reason it is unusable. */
    public static ImportedGlyphCatalog parse(String json) {
        return assemble(List.of(new Source("catalog", json, true)), Set.of());
    }

    /** One spell as a source declares it, before merging. */
    private record Declared(ResourceLocation spell, String name, ResourceLocation school, String castType, int minLevel, int maxLevel,
                            int registeredMax, List<String> rarities, List<Integer> tiers, boolean requiresLearning,
                            boolean enabledByDefault, GlyphFamily family, List<String> versions,
                            ImportedGlyphDefinition.Status status, int adapterVersion, @org.jetbrains.annotations.Nullable String target,
                            boolean finalEffectOnly, @org.jetbrains.annotations.Nullable ResourceLocation icon, String catalog,
                            String version, boolean required, String source) {
        /** Adapter version 0: identities only, never an executable contract. */
        boolean placeholder() {
            return adapterVersion == 0;
        }

        /** The execution contract two declarations must share to merge without a version transition. */
        boolean sameContract(Declared other) {
            return family == other.family && adapterVersion == other.adapterVersion
                && java.util.Objects.equals(target, other.target) && finalEffectOnly == other.finalEffectOnly;
        }

        Declared withEnvelope(int registered, String mergedSource) {
            return new Declared(spell, name, school, castType, minLevel, maxLevel, registered, rarities, tiers, requiresLearning,
                enabledByDefault, family, versions, status, adapterVersion, target, finalEffectOnly, icon, catalog, version, required, mergedSource);
        }

        /** Rarity at {@code level}: the declared default levels' list, Legendary (tier III) for a reserved level. */
        String rarityAt(int level) {
            int index = level - minLevel;
            return index >= 0 && index < rarities.size() ? rarities.get(index) : "LEGENDARY";
        }

        int tierAt(int level) {
            int index = level - minLevel;
            return index >= 0 && index < tiers.size() ? tiers.get(index) : 3;
        }

        /**
         * This declaration with its registered levels widened to cover {@code other}'s too: identities
         * only ever grow. Levels added below this declaration's first level take {@code other}'s
         * per-level data; everything else (behaviour, default maximum) stays this declaration's.
         */
        Declared unitedWith(Declared other, String mergedSource) {
            int registered = Math.max(registeredMax, other.registeredMax);
            if (other.minLevel >= minLevel) return withEnvelope(registered, mergedSource);
            List<String> lowRarities = new ArrayList<>(rarities.size() + minLevel - other.minLevel);
            List<Integer> lowTiers = new ArrayList<>(lowRarities.size());
            for (int level = other.minLevel; level < minLevel; level++) {
                lowRarities.add(other.rarityAt(level));
                lowTiers.add(other.tierAt(level));
            }
            lowRarities.addAll(rarities);
            lowTiers.addAll(tiers);
            return new Declared(spell, name, school, castType, other.minLevel, maxLevel, registered, lowRarities, lowTiers, requiresLearning,
                enabledByDefault, family, versions, status, adapterVersion, target, finalEffectOnly, icon, catalog, version, required, mergedSource);
        }
    }

    /**
     * Assemble the catalog from {@code sources} in order. See the class comment for the rules.
     *
     * @param ledger identities that must be present (part ids); a missing one fails startup
     */
    public static ImportedGlyphCatalog assemble(List<Source> sources, Set<ResourceLocation> ledger) {
        Map<ResourceLocation, Declared> spells = new LinkedHashMap<>();
        List<SourceReport> reports = new ArrayList<>();
        String catalogVersion = "empty";
        Set<String> builds = new LinkedHashSet<>();
        int variants = 0;
        for (Source source : sources) {
            List<String> errors = new ArrayList<>();
            List<Declared> declared;
            String version;
            List<String> sourceBuilds = new ArrayList<>();
            try {
                if (source.json().length() > MAX_SOURCE_BYTES) throw new IllegalArgumentException("larger than " + MAX_SOURCE_BYTES + " bytes");
                JsonObject root = CatalogJson.parseObject(source.json());
                CatalogJson.onlyFields(root, ROOT_FIELDS, "");
                version = CatalogJson.optionalString(root, "catalog_version", "", 64);
                if (version == null) version = source.name();
                JsonArray irons = CatalogJson.optionalArray(root, "irons_builds", "", 64);
                if (irons != null) for (JsonElement build : irons) sourceBuilds.add(CatalogJson.stringValue(build, "'irons_builds' entry", 64));
                declared = declare(source, root, errors, version);
            } catch (RuntimeException invalid) {
                errors.add(invalid.getMessage() == null ? invalid.toString() : invalid.getMessage());
                declared = List.of();
                version = null;
            }
            // Merge into a candidate first, so a rejected source changes nothing.
            Map<ResourceLocation, Declared> candidate = new LinkedHashMap<>(spells);
            List<String> notes = new ArrayList<>();
            for (Declared spell : declared) {
                String refusal = merge(candidate, spell, source.name(), notes);
                if (refusal != null) errors.add(refusal);
            }
            int candidateVariants = 0;
            for (Declared spell : candidate.values()) candidateVariants += spell.registeredMax() - spell.minLevel() + 1;
            if (candidate.size() > MAX_SPELLS) errors.add("the catalog would declare " + candidate.size() + " spells (at most " + MAX_SPELLS + ")");
            if (candidateVariants > MAX_VARIANTS) errors.add("the catalog would register " + candidateVariants + " glyphs (at most " + MAX_VARIANTS + ")");
            if (!errors.isEmpty()) {
                if (source.required())
                    throw new CatalogException("Invalid imported-glyph catalog " + source.name() + ": " + String.join("; ", errors));
                reports.add(new SourceReport(source.name(), false, 0, 0, List.of("rejected, nothing registered from it: " + String.join("; ", errors))));
                continue;
            }
            int addedSpells = candidate.size() - spells.size();
            int addedGlyphs = candidateVariants - variants;
            spells = candidate;
            variants = candidateVariants;
            if (source.required() && version != null) catalogVersion = version;
            builds.addAll(sourceBuilds);
            for (String note : notes) LOGGER.info("Imported-glyph catalog source {}: {}", source.name(), note);
            reports.add(new SourceReport(source.name(), source.required(), addedSpells, addedGlyphs, List.of()));
        }
        List<ImportedGlyphDefinition> definitions = new ArrayList<>(variants);
        for (Declared spell : spells.values()) {
            for (int level = spell.minLevel(); level <= spell.registeredMax(); level++) {
                boolean reserved = level > spell.maxLevel();
                String rarity = reserved ? "LEGENDARY" : spell.rarities().get(level - spell.minLevel());
                int tier = reserved ? 3 : spell.tiers().get(level - spell.minLevel());
                definitions.add(new ImportedGlyphDefinition(new ImportedGlyphKey(spell.spell(), level), spell.name(), spell.school(),
                    spell.castType(), spell.minLevel(), spell.maxLevel(), spell.registeredMax(), rarity, tier, spell.requiresLearning(),
                    spell.enabledByDefault(), spell.family(), spell.versions(), spell.status(), spell.adapterVersion(), spell.icon(),
                    spell.source()));
            }
        }
        Map<ResourceLocation, Provenance> provenance = new LinkedHashMap<>();
        for (Declared spell : spells.values())
            provenance.put(spell.spell(), new Provenance(spell.catalog(), spell.version(), List.of(spell.source().split(" \\+ "))));
        ImportedGlyphCatalog catalog = new ImportedGlyphCatalog(catalogVersion, new ArrayList<>(builds), definitions, reports, provenance);
        List<String> lost = new ArrayList<>();
        for (ResourceLocation id : ledger) if (!catalog.byGlyphId.containsKey(id)) lost.add(id.toString());
        if (!lost.isEmpty())
            throw new CatalogException(lost.size() + " published imported-glyph identities are missing from the catalog (first: "
                + lost.get(0) + "). Saved spells containing them would lose those glyphs; restore the catalog that registered them.");
        return catalog;
    }

    /**
     * Merge one declaration into {@code candidate}; null when it merged, else why the source must be
     * rejected. Identities only ever grow: the registered levels become the union of both ranges, so a
     * later declaration may extend them upwards or downwards but never remove one (a catalog whose
     * native minimum rose keeps its lower levels registered and dormant). Behaviour has one owner:
     * <ul>
     *   <li>the same execution contract (adapter, adapter version, target, final-effect rule) merges;</li>
     *   <li>a placeholder (adapter version 0) contributes identities only and never replaces anything;</li>
     *   <li>a later version of the <em>same</em> optional catalog may give a placeholder its first
     *       contract, or move the same adapter to a newer contract version (a deliberate, versioned
     *       transition); the required catalog's spells are never re-behaved by another source;</li>
     *   <li>anything else would replace a published contract, and the source is rejected whole.</li>
     * </ul>
     */
    @org.jetbrains.annotations.Nullable
    private static String merge(Map<ResourceLocation, Declared> candidate, Declared incoming, String sourceName, List<String> notes) {
        Declared previous = candidate.get(incoming.spell());
        if (previous == null) {
            candidate.put(incoming.spell(), incoming);
            return null;
        }
        String merged = previous.source() + " + " + sourceName;
        boolean related = !previous.required() && previous.catalog().equals(incoming.catalog());
        if (previous.sameContract(incoming) || incoming.placeholder()) {
            if (incoming.registeredMax() > previous.registeredMax() || incoming.minLevel() < previous.minLevel())
                candidate.put(incoming.spell(), previous.unitedWith(incoming, merged));
            return null;
        }
        boolean promotion = previous.placeholder() && related;
        boolean upgrade = related && previous.family() == incoming.family() && java.util.Objects.equals(previous.target(), incoming.target())
            && previous.finalEffectOnly() == incoming.finalEffectOnly() && incoming.adapterVersion() > previous.adapterVersion();
        if (promotion || upgrade) {
            candidate.put(incoming.spell(), incoming.unitedWith(previous, merged));
            notes.add(incoming.spell() + (promotion ? " promoted from a placeholder to " : " moved to ") + incoming.family().adapterId()
                + " v" + incoming.adapterVersion() + " by a later version of catalog " + incoming.catalog() + "; its identities are unchanged");
            return null;
        }
        return incoming.spell() + " is already catalogued by " + previous.source() + " with adapter " + previous.family().adapterId()
            + " v" + previous.adapterVersion() + "; this source names " + incoming.family().adapterId() + " v" + incoming.adapterVersion()
            + (related ? ", which would replace a published adapter contract"
                : ", and only a later version of the same optional catalog (" + previous.catalog() + ") may change it");
    }

    /** Fields a catalog document may have; anything else rejects the source. */
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
    public static final Set<String> TARGETS = Set.of("self", "entity", "entity_or_self", "block", "position", "any");
    /** Bound on {@code adapter_version}: far above any real contract, so a typo is caught rather than registered. */
    public static final int MAX_ADAPTER_VERSION = 1000;

    private static List<Declared> declare(Source source, JsonObject root, List<String> errors, String version) {
        int schema = CatalogJson.integer(root, "schema_version", "", 0, Integer.MAX_VALUE);
        if (schema != SCHEMA_VERSION && schema != LEGACY_SCHEMA_VERSION)
            throw new IllegalArgumentException("Unsupported catalog schema " + schema + " (this build reads " + LEGACY_SCHEMA_VERSION
                + " and " + SCHEMA_VERSION + ")");
        String encoding = CatalogJson.optionalString(root, "id_encoding", "", 256);
        if (encoding != null && !ID_ENCODING.equals(encoding)) throw new IllegalArgumentException("Unsupported id encoding " + encoding);
        String catalogName = CatalogJson.optionalString(root, "catalog", "", 128);
        if (catalogName == null) catalogName = source.name();
        JsonArray spells = CatalogJson.array(root, "spells", "", MAX_SPELLS);
        List<Declared> out = new ArrayList<>();
        Set<ResourceLocation> seen = new java.util.HashSet<>();
        for (int index = 0; index < spells.size(); index++) {
            String where = "spells[" + index + "]: ";
            try {
                JsonElement element = spells.get(index);
                if (!element.isJsonObject()) throw new IllegalArgumentException(where + "must be an object");
                JsonObject spell = element.getAsJsonObject();
                ResourceLocation spellId = CatalogJson.id(spell, "spell", where);
                where = spellId + ": ";
                if (!seen.add(spellId)) { errors.add(where + "duplicate spell in this source"); continue; }
                if ("ars_n_spells".equals(spellId.getNamespace())) { errors.add(where + "ANS's own proxy spells are never imported"); continue; }
                out.add(schema == LEGACY_SCHEMA_VERSION ? legacy(source, spellId, spell, where, catalogName, version)
                    : current(source, spellId, spell, where, catalogName, version));
            } catch (RuntimeException invalid) {
                errors.add(invalid.getMessage() == null ? where + invalid : invalid.getMessage());
            }
        }
        return out;
    }

    private static Declared current(Source source, ResourceLocation spellId, JsonObject spell, String where, String catalogName, String version) {
        CatalogJson.onlyFields(spell, SPELL_FIELDS, where);
        JsonObject levels = CatalogJson.object(spell, "levels", where);
        CatalogJson.onlyFields(levels, LEVEL_FIELDS, where + "levels: ");
        int max = ImportedGlyphKey.MAX_LEVEL;
        int min = CatalogJson.integer(levels, "min", where + "levels: ", 1, max, 1);
        int defaultMax = CatalogJson.integer(levels, "default_max", where + "levels: ", min, max);
        int registeredMax = CatalogJson.integer(levels, "registered_max", where + "levels: ", defaultMax, max, defaultMax);
        int count = defaultMax - min + 1;
        List<String> rarities = rarities(CatalogJson.array(spell, "rarity", where, ImportedGlyphKey.MAX_LEVEL), "rarity", count, where);
        List<Integer> tiers = tiers(CatalogJson.array(spell, "tier", where, ImportedGlyphKey.MAX_LEVEL), "tier", count, where);
        ImportedGlyphDefinition.Status status = status(spell, where);
        int adapterVersion = CatalogJson.integer(spell, "adapter_version", where, 0, MAX_ADAPTER_VERSION, 1);
        if (adapterVersion == 0 && status == ImportedGlyphDefinition.Status.SUPPORTED)
            throw new IllegalArgumentException(where + "adapter_version 0 marks a placeholder without an adapter contract, so its status "
                + "must be unsupported or retired");
        CatalogJson.optionalString(spell, "reason", where, 512);
        ResourceLocation icon = null;
        String iconText = CatalogJson.optionalString(spell, "icon", where, 256);
        if (iconText != null) {
            icon = CatalogJson.idValue(iconText, where + "'icon'");
            if (!icon.getPath().startsWith("textures/") || !icon.getPath().endsWith(".png"))
                throw new IllegalArgumentException(where + "'icon' must name a texture file such as addon:textures/gui/spell_icons/x.png");
        }
        return new Declared(spellId, CatalogJson.string(spell, "name", where, 256), CatalogJson.id(spell, "school", where),
            castType(spell, where), min, defaultMax, registeredMax, rarities, tiers, CatalogJson.bool(spell, "requires_learning", where, false),
            CatalogJson.bool(spell, "enabled_by_default", where, true), family(spell, where), versions(spell, where), status, adapterVersion,
            target(spell, where), CatalogJson.bool(spell, "final_effect_only", where, false), icon, catalogName, version, source.required(),
            source.name());
    }

    /** Schema 1, as either loader's 3.4.0 catalog wrote it: one level range, every level supported, adapter contract 1. */
    private static Declared legacy(Source source, ResourceLocation spellId, JsonObject spell, String where, String catalogName, String version) {
        CatalogJson.onlyFields(spell, LEGACY_SPELL_FIELDS, where);
        int max = ImportedGlyphKey.MAX_LEVEL;
        int min = CatalogJson.integer(spell, "min_level", where, 1, max, 1);
        int defaultMax = CatalogJson.integer(spell, "max_level", where, min, max);
        int count = defaultMax - min + 1;
        String rarityField = spell.has("rarity_by_level") ? "rarity_by_level" : "rarity";
        String tierField = spell.has("tier_by_level") ? "tier_by_level" : "default_tier";
        CatalogJson.optionalString(spell, "reason", where, 512);
        return new Declared(spellId, CatalogJson.string(spell, "name", where, 256), CatalogJson.id(spell, "school", where),
            castType(spell, where), min, defaultMax, defaultMax,
            rarities(CatalogJson.array(spell, rarityField, where, ImportedGlyphKey.MAX_LEVEL), rarityField, count, where),
            tiers(CatalogJson.array(spell, tierField, where, ImportedGlyphKey.MAX_LEVEL), tierField, count, where),
            CatalogJson.bool(spell, "requires_learning", where, false), CatalogJson.bool(spell, "enabled_by_default", where, true),
            family(spell, where), versions(spell, where), status(spell, where), 1, target(spell, where),
            CatalogJson.bool(spell, "final_effect_only", where, false), null, catalogName, version, source.required(), source.name());
    }

    private static ImportedGlyphDefinition.Status status(JsonObject spell, String where) {
        String statusText = CatalogJson.optionalString(spell, "status", where, 16);
        try {
            return statusText == null ? ImportedGlyphDefinition.Status.SUPPORTED : ImportedGlyphDefinition.Status.parse(statusText);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(where + "'status' " + unknown.getMessage());
        }
    }

    private static String castType(JsonObject spell, String where) {
        String castType = CatalogJson.string(spell, "cast_type", where, 16);
        if (!CAST_TYPES.contains(castType))
            throw new IllegalArgumentException(where + "'cast_type' must be INSTANT, LONG or CONTINUOUS (found " + castType + ")");
        return castType;
    }

    private static GlyphFamily family(JsonObject spell, String where) {
        ResourceLocation adapter = CatalogJson.id(spell, "adapter", where);
        try {
            return GlyphFamily.fromAdapterId(adapter.toString());
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(where + "adapter " + adapter + " is not one this build implements");
        }
    }

    @org.jetbrains.annotations.Nullable
    private static String target(JsonObject spell, String where) {
        String target = CatalogJson.optionalString(spell, "target", where, 32);
        if (target != null && !TARGETS.contains(target))
            throw new IllegalArgumentException(where + "'target' must be one of " + new java.util.TreeSet<>(TARGETS) + " (found " + target + ")");
        return target;
    }

    private static List<String> versions(JsonObject spell, String where) {
        List<String> versions = new ArrayList<>();
        JsonArray list = CatalogJson.optionalArray(spell, "irons_versions", where, 64);
        if (list != null) for (JsonElement version : list) versions.add(CatalogJson.stringValue(version, where + "'irons_versions' entry", 64));
        return versions;
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

    private static String digest(List<ImportedGlyphDefinition> sorted) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(("ans-iron-glyph-identity/v1\n").getBytes(StandardCharsets.UTF_8));
            for (ImportedGlyphDefinition definition : sorted) {
                sha.update(definition.glyphId().toString().getBytes(StandardCharsets.UTF_8));
                sha.update((byte) '\n');
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public String catalogVersion() { return catalogVersion; }
    public List<String> ironsBuilds() { return ironsBuilds; }
    public String identityDigest() { return identityDigest; }
    public int size() { return byGlyphId.size(); }
    public java.util.Collection<ImportedGlyphDefinition> definitions() { return byGlyphId.values(); }
    public java.util.Set<ResourceLocation> spells() { return bySpell.keySet(); }
    public List<SourceReport> sources() { return sources; }

    public Optional<ImportedGlyphDefinition> byGlyphId(ResourceLocation id) {
        return Optional.ofNullable(byGlyphId.get(id));
    }

    public Optional<ImportedGlyphDefinition> byKey(ImportedGlyphKey key) {
        return byGlyphId(key.glyphId());
    }

    /** Every registered level of {@code spell}, ascending; empty when the spell is not catalogued. */
    public List<ImportedGlyphDefinition> levelsOf(ResourceLocation spell) {
        return bySpell.getOrDefault(spell, List.of());
    }

    /** Where {@code spell}'s identities came from; empty when it is not catalogued. */
    public Optional<Provenance> provenance(ResourceLocation spell) {
        return Optional.ofNullable(provenance.get(spell));
    }

    /** The lowest registered base level of {@code spell}, or 0 when it is not catalogued. */
    public int minLevel(ResourceLocation spell) {
        List<ImportedGlyphDefinition> levels = levelsOf(spell);
        return levels.isEmpty() ? 0 : levels.get(0).level();
    }

    /** The highest registered base level of {@code spell}, or 0 when it is not catalogued. */
    public int registeredMax(ResourceLocation spell) {
        List<ImportedGlyphDefinition> levels = levelsOf(spell);
        return levels.isEmpty() ? 0 : levels.get(levels.size() - 1).level();
    }

    /** Test seam: replace the loaded catalog. */
    public static void setForTesting(ImportedGlyphCatalog catalog) {
        loaded = catalog;
    }
}
