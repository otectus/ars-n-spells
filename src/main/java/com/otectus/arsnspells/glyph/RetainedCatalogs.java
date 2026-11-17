package com.otectus.arsnspells.glyph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Ars 'n' Spells' own copy of every optional imported-glyph catalog it has accepted (3.4.1 review
 * SAVE-01), in {@code config/ars_n_spells/retained_glyph_catalogs/}.
 *
 * <p>A catalog decides which glyph ids exist, and Ars stores spells and knowledge by id, so removing
 * the package that registered some ids (an addon's catalog-only jar, a loose config catalog) used to
 * leave the world's saved glyphs unregistered, to be dropped on the next save. Each accepted optional
 * source is therefore copied here, byte for byte and append-only, named by its catalog and content hash.
 * At startup, after every live source, each retained copy is read back as <em>identities only</em>:
 * every spell becomes a retired placeholder ({@code adapter_version} 0), which registers its ids
 * dormant and can never replace or re-behave a live declaration. With the package present nothing
 * changes; with it gone the glyphs stay registered and dormant ("its package is not installed") and
 * work again once it is reinstalled. A copy whose content no longer matches its name is corrupt and is
 * not read; the world-start check of {@link GlyphIdentityRecord} then refuses a world that needs it.
 *
 * <p>To deliberately forget a package's identities, delete its retained copy and accept the loss at
 * the next world start (see {@link GlyphIdentityRecord#ACCEPT_MISSING_PROPERTY}).
 */
public final class RetainedCatalogs {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetainedCatalogs.class);
    public static final String DIRECTORY = "ars_n_spells/retained_glyph_catalogs";
    /** Source-name prefix of a retained copy in the assembled catalog's reports and provenance. */
    public static final String SOURCE_PREFIX = "retained/";
    /** At most this many copies are kept or read; a pack that needs more is far outside any real use. */
    public static final int MAX_FILES = 256;
    private static volatile List<String> lastProblems = List.of();

    private RetainedCatalogs() {}

    /** The archive directory of this game instance, or null outside a running game (tests, tools). */
    @Nullable
    public static Path directory() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(DIRECTORY);
        } catch (RuntimeException | LinkageError notLoading) {
            return null;
        }
    }

    /**
     * The retained copies as identity-only sources, in name order, skipping every copy whose original
     * text a live source supplies right now (it adds nothing).
     */
    public static List<ImportedGlyphCatalog.Source> sources(@Nullable Path dir, List<ImportedGlyphCatalog.Source> live) {
        List<ImportedGlyphCatalog.Source> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            lastProblems = List.of();
            return out;
        }
        Set<String> liveHashes = new HashSet<>();
        for (ImportedGlyphCatalog.Source source : live) liveHashes.add(sha256(source.json()));
        List<Path> files = list(dir, problems);
        for (Path file : files) {
            String name = file.getFileName().toString();
            String hash = hashOf(name);
            try {
                if (Files.size(file) > ImportedGlyphCatalog.MAX_SOURCE_BYTES)
                    throw new IOException("larger than " + ImportedGlyphCatalog.MAX_SOURCE_BYTES + " bytes");
                String text = Files.readString(file, StandardCharsets.UTF_8);
                String actual = sha256(text);
                if (hash == null || !actual.startsWith(hash))
                    throw new IOException("its content does not match its name (expected " + hash + ", found " + actual.substring(0, 16) + ")");
                if (liveHashes.contains(actual)) continue;
                out.add(new ImportedGlyphCatalog.Source(SOURCE_PREFIX + name, identityOnly(text), false));
            } catch (IOException | RuntimeException corrupt) {
                problems.add(name + ": " + corrupt.getMessage() + "; not read, so the identities only it kept are not registered");
            }
        }
        for (String problem : problems) LOGGER.error("Retained imported-glyph catalog {}", problem);
        lastProblems = List.copyOf(problems);
        return out;
    }

    /** Copy every accepted live optional source that is not retained yet. Failures are logged, never fatal. */
    public static int retain(@Nullable Path dir, List<ImportedGlyphCatalog.Source> live, ImportedGlyphCatalog catalog) {
        if (dir == null) return 0;
        Set<String> accepted = new HashSet<>();
        for (ImportedGlyphCatalog.SourceReport report : catalog.sources()) if (report.errors().isEmpty()) accepted.add(report.name());
        int written = 0;
        try {
            Files.createDirectories(dir);
            List<String> problems = new ArrayList<>();
            List<Path> existing = list(dir, problems);
            Set<String> kept = new HashSet<>();
            for (Path file : existing) {
                String hash = hashOf(file.getFileName().toString());
                if (hash != null) kept.add(hash);
            }
            for (ImportedGlyphCatalog.Source source : live) {
                if (source.required() || !accepted.contains(source.name())) continue;
                String hash = sha256(source.json()).substring(0, 16);
                if (kept.contains(hash)) continue;
                if (existing.size() + written >= MAX_FILES) {
                    LOGGER.warn("Not retaining imported-glyph catalog {}: {} already holds {} copies", source.name(), dir, MAX_FILES);
                    break;
                }
                Path target = dir.resolve(fileName(catalogName(source), hash));
                Path temporary = dir.resolve(target.getFileName() + ".tmp");
                Files.writeString(temporary, source.json(), StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException notAtomic) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                kept.add(hash);
                written++;
                LOGGER.info("Retained imported-glyph catalog {} as {}: its identities stay registered if the package is removed", source.name(), target);
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Could not retain imported-glyph catalogs in {}: {}", dir, failure.toString());
        }
        return written;
    }

    /**
     * The same document with every spell turned into a retired placeholder: identities only, never an
     * executable contract. A schema-1 document is converted to schema 2 on the way.
     */
    static String identityOnly(String json) {
        JsonObject root = CatalogJson.parseObject(json);
        JsonElement spells = root.get("spells");
        if (spells == null || !spells.isJsonArray()) throw new IllegalArgumentException("no spells list");
        boolean legacy = root.has("schema_version") && root.get("schema_version").isJsonPrimitive()
            && root.get("schema_version").getAsJsonPrimitive().isNumber() && root.get("schema_version").getAsInt() == ImportedGlyphCatalog.LEGACY_SCHEMA_VERSION;
        JsonArray converted = new JsonArray();
        for (JsonElement element : spells.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                converted.add(element);
                continue;
            }
            JsonObject spell = element.getAsJsonObject().deepCopy();
            if (legacy) upgrade(spell);
            spell.addProperty("status", "retired");
            spell.addProperty("adapter_version", 0);
            converted.add(spell);
        }
        root.add("spells", converted);
        if (legacy) root.addProperty("schema_version", ImportedGlyphCatalog.SCHEMA_VERSION);
        return root.toString();
    }

    private static void upgrade(JsonObject spell) {
        JsonObject levels = new JsonObject();
        levels.add("min", spell.has("min_level") ? spell.remove("min_level") : new com.google.gson.JsonPrimitive(1));
        JsonElement max = spell.remove("max_level");
        levels.add("default_max", max);
        levels.add("registered_max", max == null ? null : max.deepCopy());
        spell.add("levels", levels);
        JsonElement rarity = spell.has("rarity_by_level") ? spell.remove("rarity_by_level") : spell.remove("rarity");
        if (rarity != null) spell.add("rarity", rarity);
        JsonElement tier = spell.has("tier_by_level") ? spell.remove("tier_by_level") : spell.remove("default_tier");
        if (tier != null) spell.add("tier", tier);
    }

    /** Problems found reading the archive at the last startup, for diagnostics. */
    public static List<String> lastProblems() {
        return lastProblems;
    }

    static String fileName(String catalog, String hash) {
        String safe = catalog.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        if (safe.length() > 64) safe = safe.substring(0, 64);
        return safe + "@" + hash + ".json";
    }

    /** The content hash a retained file's name carries, or null when the name is not one this class writes. */
    @Nullable
    static String hashOf(String fileName) {
        int at = fileName.lastIndexOf('@');
        if (at < 0 || !fileName.endsWith(".json")) return null;
        String hash = fileName.substring(at + 1, fileName.length() - ".json".length());
        return hash.matches("[0-9a-f]{16}") ? hash : null;
    }

    private static String catalogName(ImportedGlyphCatalog.Source source) {
        try {
            JsonObject root = CatalogJson.parseObject(source.json());
            String name = CatalogJson.optionalString(root, "catalog", "", 128);
            if (name != null) return name;
        } catch (RuntimeException unreadable) {
            // An accepted source always parses; fall back to its file name anyway.
        }
        String name = source.name();
        int slash = name.lastIndexOf('/');
        name = slash >= 0 ? name.substring(slash + 1) : name;
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    private static List<Path> list(Path dir, List<String> problems) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> listing = Files.list(dir)) {
            listing.filter(p -> p.getFileName().toString().endsWith(".json") && Files.isRegularFile(p)).sorted().forEach(files::add);
        } catch (IOException unreadable) {
            problems.add(dir + ": " + unreadable);
        }
        if (files.size() > MAX_FILES) {
            problems.add(dir + " holds " + files.size() + " copies; only the first " + MAX_FILES + " in name order are read");
            return new ArrayList<>(files.subList(0, MAX_FILES));
        }
        return files;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
