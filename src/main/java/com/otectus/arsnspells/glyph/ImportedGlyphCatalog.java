package com.otectus.arsnspells.glyph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The startup catalog: every imported glyph identity this build registers, read once from the
 * jar at mod construction and never changed afterwards.
 *
 * <p>The catalog is generated from the loaded registries of the pinned Iron's builds
 * ({@code tools/generate_iron_glyph_catalog.py}) and committed, so registration never waits on
 * Iron's late-populated spell registry and never depends on Iron's being installed at all. It
 * decides which glyph ids exist; it does not decide which are usable. Availability, tiers and
 * conversion costs come from the reloadable behaviour layer and the live Iron's config.
 *
 * <p>{@link #identityDigest()} covers exactly the registered identities (spell and level pairs
 * and the id encoding). A client and server with different digests would decode the same saved
 * spell differently, so the handshake treats a mismatch as a compatibility error.
 */
public final class ImportedGlyphCatalog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportedGlyphCatalog.class);
    public static final String RESOURCE = "/ars_n_spells/iron_glyph_catalog.json";
    public static final int SCHEMA_VERSION = 1;
    /** Bounds on what a catalog may declare; the shipped one is far inside both. */
    public static final int MAX_SPELLS = 1024;
    public static final int MAX_VARIANTS = 4096;

    private static volatile ImportedGlyphCatalog loaded;

    private final String catalogVersion;
    private final List<String> ironsBuilds;
    private final Map<ResourceLocation, ImportedGlyphDefinition> byGlyphId;
    private final Map<ResourceLocation, List<ImportedGlyphDefinition>> bySpell;
    private final String identityDigest;

    private ImportedGlyphCatalog(String catalogVersion, List<String> ironsBuilds, List<ImportedGlyphDefinition> definitions) {
        this.catalogVersion = catalogVersion;
        this.ironsBuilds = List.copyOf(ironsBuilds);
        Map<ResourceLocation, ImportedGlyphDefinition> ids = new LinkedHashMap<>();
        Map<ResourceLocation, List<ImportedGlyphDefinition>> spells = new TreeMap<>();
        List<ImportedGlyphDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort((a, b) -> a.key().compareTo(b.key()));
        for (ImportedGlyphDefinition definition : sorted) {
            if (ids.put(definition.glyphId(), definition) != null)
                throw new IllegalArgumentException("Duplicate glyph id " + definition.glyphId());
            spells.computeIfAbsent(definition.spell(), ignored -> new ArrayList<>()).add(definition);
        }
        spells.replaceAll((spell, list) -> List.copyOf(list));
        this.byGlyphId = Collections.unmodifiableMap(ids);
        this.bySpell = Collections.unmodifiableMap(spells);
        this.identityDigest = digest(sorted);
    }

    /** The catalog this build registered. Loaded on first use; a broken resource yields an empty catalog. */
    public static ImportedGlyphCatalog get() {
        ImportedGlyphCatalog current = loaded;
        if (current == null) {
            synchronized (ImportedGlyphCatalog.class) {
                current = loaded;
                if (current == null) loaded = current = loadBundled();
            }
        }
        return current;
    }

    private static ImportedGlyphCatalog loadBundled() {
        try (InputStream in = ImportedGlyphCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.error("Imported-glyph catalog {} is missing from the jar; no imported glyphs will be registered", RESOURCE);
                return empty();
            }
            ImportedGlyphCatalog catalog = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            LOGGER.info("Imported-glyph catalog {}: {} Iron's spells, {} glyphs, identity {}", catalog.catalogVersion,
                catalog.bySpell.size(), catalog.byGlyphId.size(), catalog.identityDigest.substring(0, 16));
            return catalog;
        } catch (Exception failure) {
            LOGGER.error("Imported-glyph catalog {} could not be read; no imported glyphs will be registered", RESOURCE, failure);
            return empty();
        }
    }

    public static ImportedGlyphCatalog empty() {
        return new ImportedGlyphCatalog("empty", List.of(), List.of());
    }

    /** Parse and fully validate a catalog document. Throws with every reason it is unusable. */
    public static ImportedGlyphCatalog parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int schema = root.get("schema_version").getAsInt();
        if (schema != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported catalog schema " + schema);
        String encoding = root.get("id_encoding").getAsString();
        if (!"ars_n_spells:iron/<spell namespace>/<spell path>/level_<n>".equals(encoding))
            throw new IllegalArgumentException("Unsupported id encoding " + encoding);
        List<String> builds = new ArrayList<>();
        for (JsonElement build : root.getAsJsonArray("irons_builds")) builds.add(build.getAsString());
        JsonArray spells = root.getAsJsonArray("spells");
        if (spells.size() > MAX_SPELLS) throw new IllegalArgumentException("Catalog declares " + spells.size() + " spells");
        List<ImportedGlyphDefinition> definitions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
        for (JsonElement element : spells) {
            JsonObject spell = element.getAsJsonObject();
            String id = spell.get("spell").getAsString();
            ResourceLocation spellId = ResourceLocation.tryParse(id);
            if (spellId == null || !seen.add(spellId)) { errors.add("invalid or duplicate spell " + id); continue; }
            int maxLevel = spell.get("max_level").getAsInt();
            JsonArray rarities = spell.getAsJsonArray("rarity_by_level");
            JsonArray tiers = spell.getAsJsonArray("tier_by_level");
            if (maxLevel < 1 || maxLevel > ImportedGlyphKey.MAX_LEVEL || rarities.size() != maxLevel || tiers.size() != maxLevel) {
                errors.add(id + ": level data does not match max_level " + maxLevel);
                continue;
            }
            ResourceLocation school = ResourceLocation.tryParse(spell.get("school").getAsString());
            String castType = spell.get("cast_type").getAsString();
            if (school == null || !List.of("INSTANT", "LONG", "CONTINUOUS").contains(castType)) {
                errors.add(id + ": invalid school or cast type");
                continue;
            }
            GlyphFamily family;
            try {
                family = GlyphFamily.fromAdapterId(spell.get("adapter").getAsString());
            } catch (IllegalArgumentException unknown) {
                errors.add(id + ": " + unknown.getMessage());
                continue;
            }
            List<String> versions = new ArrayList<>();
            for (JsonElement version : spell.getAsJsonArray("irons_versions")) versions.add(version.getAsString());
            for (int level = 1; level <= maxLevel; level++) {
                try {
                    definitions.add(new ImportedGlyphDefinition(new ImportedGlyphKey(spellId, level),
                        spell.get("name").getAsString(), school, castType, maxLevel,
                        rarities.get(level - 1).getAsString(), tiers.get(level - 1).getAsInt(),
                        spell.get("requires_learning").getAsBoolean(), spell.get("enabled_by_default").getAsBoolean(),
                        family, versions));
                } catch (IllegalArgumentException invalid) {
                    errors.add(id + " level " + level + ": " + invalid.getMessage());
                }
            }
        }
        if (definitions.size() > MAX_VARIANTS) errors.add("catalog declares " + definitions.size() + " glyphs");
        if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid imported-glyph catalog: " + String.join("; ", errors));
        return new ImportedGlyphCatalog(root.get("catalog_version").getAsString(), builds, definitions);
    }

    private static String digest(List<ImportedGlyphDefinition> sorted) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(("ans-iron-glyph-identity/v" + SCHEMA_VERSION + "\n").getBytes(StandardCharsets.UTF_8));
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

    public Optional<ImportedGlyphDefinition> byGlyphId(ResourceLocation id) {
        return Optional.ofNullable(byGlyphId.get(id));
    }

    public Optional<ImportedGlyphDefinition> byKey(ImportedGlyphKey key) {
        return byGlyphId(key.glyphId());
    }

    /** Every catalogued level of {@code spell}, ascending; empty when the spell is not catalogued. */
    public List<ImportedGlyphDefinition> levelsOf(ResourceLocation spell) {
        return bySpell.getOrDefault(spell, List.of());
    }

    /** Test seam: replace the loaded catalog. */
    static void setForTesting(ImportedGlyphCatalog catalog) {
        loaded = catalog;
    }
}
