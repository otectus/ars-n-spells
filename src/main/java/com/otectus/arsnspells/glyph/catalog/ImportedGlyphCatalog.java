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
 * from a runtime inventory by {@code tools/generate_iron_glyph_catalog.py}). It never reads the
 * live Iron's registry, so it is identical with Iron's present, absent, or reconfigured. Server
 * settings and datapacks change whether a glyph is available, never whether its id exists.
 *
 * <p>The catalog is immutable after load. Its {@link #hash()} covers every identity-bearing field
 * and is compared between server and client at login; a mismatch is refused with a readable
 * message rather than left to decode spells into the wrong parts.
 */
public final class ImportedGlyphCatalog {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportedGlyphCatalog.class);

    public static final int SCHEMA_VERSION = 1;
    /** Hard caps: a catalog that exceeds them is truncated with an error, never registered whole. */
    public static final int MAX_SPELLS = 2048;
    public static final int MAX_VARIANTS = 8192;
    public static final int MAX_MANIFEST_BYTES = 2 * 1024 * 1024;

    /** Adapters this build implements; a manifest may only name these. */
    public static final Set<String> BUILTIN_ADAPTERS = Set.of(
        "ars_n_spells:self", "ars_n_spells:projectile", "ars_n_spells:entity", "ars_n_spells:position",
        "ars_n_spells:block", "ars_n_spells:teleport", "ars_n_spells:summon", "ars_n_spells:channel");

    private static volatile ImportedGlyphCatalog instance = empty();

    private final List<CatalogEntry> entries;
    private final Map<ResourceLocation, CatalogEntry> bySpell;
    private final Map<ResourceLocation, ImportedGlyphKey> byPartId;
    private final String hash;
    private final List<String> errors;

    private ImportedGlyphCatalog(List<CatalogEntry> entries, List<String> errors) {
        Map<ResourceLocation, CatalogEntry> spells = new TreeMap<>(ResourceLocation::compareNamespaced);
        Map<ResourceLocation, ImportedGlyphKey> parts = new TreeMap<>(ResourceLocation::compareNamespaced);
        List<String> problems = new ArrayList<>(errors);
        List<CatalogEntry> kept = new ArrayList<>();
        int variants = 0;
        for (CatalogEntry entry : entries) {
            if (spells.containsKey(entry.spellId())) {
                problems.add("duplicate spell " + entry.spellId() + " in " + entry.sourceCatalog()
                    + " (kept the one from " + spells.get(entry.spellId()).sourceCatalog() + ")");
                continue;
            }
            if (kept.size() >= MAX_SPELLS || variants + (entry.maxLevel() - entry.minLevel() + 1) > MAX_VARIANTS) {
                problems.add("catalog cap reached; " + entry.spellId() + " and later entries were not registered");
                break;
            }
            boolean collision = false;
            if (entry.supported()) {
                for (ImportedGlyphKey key : entry.keys()) {
                    if (parts.containsKey(key.partId())) {
                        problems.add("glyph id collision " + key.partId() + " between " + parts.get(key.partId()) + " and " + key);
                        collision = true;
                    }
                }
            }
            if (collision) continue;
            spells.put(entry.spellId(), entry);
            kept.add(entry);
            if (entry.supported()) {
                for (ImportedGlyphKey key : entry.keys()) parts.put(key.partId(), key);
                variants += entry.maxLevel() - entry.minLevel() + 1;
            }
        }
        this.entries = Collections.unmodifiableList(kept);
        this.bySpell = Collections.unmodifiableMap(spells);
        this.byPartId = Collections.unmodifiableMap(parts);
        this.errors = List.copyOf(problems);
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

    /** Build a catalog from manifests in a deterministic order (sorted by source name). */
    public static ImportedGlyphCatalog fromManifests(Map<String, String> manifestsBySource) {
        List<CatalogEntry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (var manifest : new TreeMap<>(manifestsBySource).entrySet()) {
            parse(manifest.getKey(), manifest.getValue(), entries, errors);
        }
        return new ImportedGlyphCatalog(entries, errors);
    }

    static void parse(String source, String json, List<CatalogEntry> out, List<String> errors) {
        if (json.length() > MAX_MANIFEST_BYTES) {
            errors.add(source + ": manifest larger than " + MAX_MANIFEST_BYTES + " bytes, ignored");
            return;
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException malformed) {
            errors.add(source + ": not a JSON object (" + malformed.getMessage() + ")");
            return;
        }
        int schema = root.has("schema_version") ? root.get("schema_version").getAsInt() : -1;
        if (schema != SCHEMA_VERSION) {
            errors.add(source + ": schema_version " + schema + " is not " + SCHEMA_VERSION + "; manifest ignored, nothing registered from it");
            return;
        }
        JsonArray spells = root.has("spells") && root.get("spells").isJsonArray() ? root.getAsJsonArray("spells") : new JsonArray();
        for (JsonElement element : spells) {
            try {
                out.add(entry(source, element.getAsJsonObject()));
            } catch (RuntimeException invalid) {
                errors.add(source + ": invalid spell entry " + element + " (" + invalid.getMessage() + ")");
            }
        }
    }

    private static CatalogEntry entry(String source, JsonObject o) {
        ResourceLocation spell = ResourceLocation.parse(o.get("spell").getAsString());
        int min = o.get("min_level").getAsInt();
        int max = o.get("max_level").getAsInt();
        if (min < 1 || max < min || max > ImportedGlyphKey.MAX_LEVEL) {
            throw new IllegalArgumentException("levels " + min + ".." + max + " out of range");
        }
        List<String> rarity = new ArrayList<>();
        o.getAsJsonArray("rarity").forEach(e -> rarity.add(e.getAsString()));
        List<Integer> tiers = new ArrayList<>();
        o.getAsJsonArray("default_tier").forEach(e -> tiers.add(e.getAsInt()));
        if (rarity.size() != max - min + 1 || tiers.size() != max - min + 1) {
            throw new IllegalArgumentException("rarity/default_tier must list every level");
        }
        for (int tier : tiers) {
            if (tier < 1 || tier > 3) throw new IllegalArgumentException("tier " + tier + " out of 1..3");
        }
        String adapter = o.get("adapter").getAsString();
        if (!BUILTIN_ADAPTERS.contains(adapter)) {
            throw new IllegalArgumentException("adapter " + adapter + " is not implemented by this build");
        }
        String status = o.has("status") ? o.get("status").getAsString() : "supported";
        if (!status.equals("supported") && !status.equals("unsupported")) {
            throw new IllegalArgumentException("status " + status);
        }
        return new CatalogEntry(spell, o.get("name").getAsString(), ResourceLocation.parse(o.get("school").getAsString()),
            o.get("cast_type").getAsString(), min, max, rarity, tiers,
            o.has("requires_learning") && o.get("requires_learning").getAsBoolean(),
            !o.has("enabled_by_default") || o.get("enabled_by_default").getAsBoolean(),
            ResourceLocation.parse(adapter), CatalogEntry.TargetKind.parse(o.get("target").getAsString()),
            o.has("final_effect_only") && o.get("final_effect_only").getAsBoolean(), status, source);
    }

    private static String digest(List<CatalogEntry> entries) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            List<CatalogEntry> sorted = new ArrayList<>(entries);
            sorted.sort((a, b) -> a.spellId().compareNamespaced(b.spellId()));
            for (CatalogEntry e : sorted) {
                String line = e.spellId() + "|" + e.minLevel() + "|" + e.maxLevel() + "|" + e.adapter() + "|" + e.target()
                    + "|" + e.school() + "|" + e.castType() + "|" + e.status() + "|" + e.finalEffectOnly()
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

    /** Every registered key, in the stable order registration uses. */
    public List<ImportedGlyphKey> keys() {
        List<ImportedGlyphKey> keys = new ArrayList<>(byPartId.values());
        Collections.sort(keys);
        return keys;
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
