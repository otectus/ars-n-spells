package com.otectus.arsnspells.glyph.catalog;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Finds imported-glyph catalog manifests in loaded mod files and in the config folder.
 *
 * <p>Any mod may ship {@code ans_glyph_catalog/<name>.json} in its jar (not in a datapack: these
 * decide registry identity, which must be fixed before registration). ANS ships
 * {@code irons_spellbooks.json}, which is <em>required</em>: startup fails if it is invalid. A pack
 * may also keep catalogs in {@code config/ars_n_spells/glyph_catalogs/*.json}, which survive
 * removing the addon they describe. Manifests are read in a deterministic order, keyed
 * {@code <modfile>/<name>} or {@code config/<name>}; see {@code docs/3.4.1/catalog-packages.md}.
 */
public final class CatalogManifests {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogManifests.class);
    public static final String DIRECTORY = "ans_glyph_catalog";
    public static final String CONFIG_DIRECTORY = "ars_n_spells/glyph_catalogs";

    private CatalogManifests() {
    }

    /** Every manifest source and its text; ANS's own are marked required. */
    public static java.util.List<ImportedGlyphCatalog.Manifest> discoverAll() {
        java.util.List<ImportedGlyphCatalog.Manifest> out = new java.util.ArrayList<>();
        for (IModFileInfo info : ModList.get().getModFiles()) {
            Path dir;
            try {
                dir = info.getFile().findResource(DIRECTORY);
            } catch (RuntimeException unavailable) {
                continue;
            }
            if (dir == null || !Files.isDirectory(dir)) {
                continue;
            }
            boolean ours = info.getMods().stream().anyMatch(mod -> com.otectus.arsnspells.ArsNSpells.MODID.equals(mod.getModId()));
            read(dir, info.getFile().getFileName() + "/", ours, out);
        }
        try {
            Path config = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(CONFIG_DIRECTORY);
            if (Files.isDirectory(config)) read(config, "config/", false, out);
        } catch (RuntimeException | LinkageError notLoading) {
            // Unit tests and tools: no config folder.
        }
        return out;
    }

    /** The optional manifests only, by source (kept for callers that predate required sources). */
    public static Map<String, String> discover() {
        Map<String, String> manifests = new TreeMap<>();
        for (ImportedGlyphCatalog.Manifest manifest : discoverAll()) manifests.put(manifest.source(), manifest.json());
        return manifests;
    }

    private static void read(Path dir, String prefix, boolean required, java.util.List<ImportedGlyphCatalog.Manifest> out) {
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(p -> {
                String source = prefix + p.getFileName();
                try {
                    if (Files.size(p) > ImportedGlyphCatalog.MAX_MANIFEST_BYTES) {
                        LOGGER.error("[ImportedGlyphs] {} exceeds {} bytes and was ignored", source, ImportedGlyphCatalog.MAX_MANIFEST_BYTES);
                        return;
                    }
                    out.add(new ImportedGlyphCatalog.Manifest(source, Files.readString(p, StandardCharsets.UTF_8), required));
                } catch (IOException e) {
                    LOGGER.error("[ImportedGlyphs] could not read {}: {}", source, e.toString());
                }
            });
        } catch (IOException e) {
            LOGGER.error("[ImportedGlyphs] could not list {}: {}", dir, e.toString());
        }
    }
}
