package com.otectus.arsnspells.glyph;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Finds the optional imported-glyph catalog sources at mod construction, before any registry
 * event: {@code ans_glyph_catalog/*.json} inside every loaded mod file (an addon's own manifest,
 * or a catalog-only compatibility jar), then {@code config/ars_n_spells/glyph_catalogs/*.json}
 * (a modpack's persistent catalog, which survives removing the addon it describes).
 *
 * <p>This is the Forge counterpart of the NeoForge build's manifest discovery. Datapacks are
 * deliberately not a source: they load per world, after registration has closed, and identity
 * must be fixed before that.
 */
final class CatalogSources {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogSources.class);

    private CatalogSources() {}

    static List<ImportedGlyphCatalog.Source> discover() {
        List<ImportedGlyphCatalog.Source> sources = new ArrayList<>();
        sources.addAll(fromModFiles());
        sources.addAll(fromConfig());
        return sources;
    }

    private static List<ImportedGlyphCatalog.Source> fromModFiles() {
        TreeMap<String, String> found = new TreeMap<>();
        ModList mods;
        try {
            mods = ModList.get();
        } catch (RuntimeException | LinkageError notLoading) {
            return List.of();
        }
        if (mods == null) return List.of();
        for (IModFileInfo info : mods.getModFiles()) {
            Path dir;
            try {
                dir = info.getFile().findResource(ImportedGlyphCatalog.MANIFEST_DIRECTORY);
            } catch (RuntimeException unavailable) {
                continue;
            }
            if (dir == null || !Files.isDirectory(dir)) continue;
            readDirectory(dir, info.getFile().getFileName() + "/" + ImportedGlyphCatalog.MANIFEST_DIRECTORY + "/", found);
        }
        return toSources(found);
    }

    private static List<ImportedGlyphCatalog.Source> fromConfig() {
        Path dir;
        try {
            dir = FMLPaths.CONFIGDIR.get().resolve(ImportedGlyphCatalog.CONFIG_DIRECTORY);
        } catch (RuntimeException | LinkageError notLoading) {
            return List.of();
        }
        if (!Files.isDirectory(dir)) return List.of();
        TreeMap<String, String> found = new TreeMap<>();
        readDirectory(dir, "config/" + ImportedGlyphCatalog.CONFIG_DIRECTORY + "/", found);
        return toSources(found);
    }

    private static void readDirectory(Path dir, String prefix, TreeMap<String, String> into) {
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(path -> {
                String name = prefix + path.getFileName();
                try {
                    if (Files.size(path) > ImportedGlyphCatalog.MAX_SOURCE_BYTES) {
                        LOGGER.error("Imported-glyph catalog {} is larger than {} bytes and was ignored", name, ImportedGlyphCatalog.MAX_SOURCE_BYTES);
                        return;
                    }
                    into.put(name, Files.readString(path, StandardCharsets.UTF_8));
                } catch (IOException unreadable) {
                    LOGGER.error("Imported-glyph catalog {} could not be read: {}", name, unreadable.toString());
                }
            });
        } catch (IOException unlisted) {
            LOGGER.error("Imported-glyph catalog directory {} could not be listed: {}", prefix, unlisted.toString());
        }
    }

    private static List<ImportedGlyphCatalog.Source> toSources(TreeMap<String, String> found) {
        List<ImportedGlyphCatalog.Source> sources = new ArrayList<>(found.size());
        found.forEach((name, json) -> sources.add(new ImportedGlyphCatalog.Source(name, json, false)));
        return sources;
    }
}
