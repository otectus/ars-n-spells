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
 * Finds imported-glyph catalog manifests in loaded mod files.
 *
 * <p>Any mod may ship {@code ans_glyph_catalog/<name>.json} in its jar (not in a datapack: these
 * decide registry identity, which must be fixed before registration). ANS ships
 * {@code irons_spellbooks.json}. Manifests are read in a deterministic order, keyed
 * {@code <modfile>/<name>}; see {@code docs/3.4.0/adapter-authoring.md} for the format.
 */
public final class CatalogManifests {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogManifests.class);
    public static final String DIRECTORY = "ans_glyph_catalog";

    private CatalogManifests() {
    }

    public static Map<String, String> discover() {
        Map<String, String> manifests = new TreeMap<>();
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
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(p -> {
                    String source = info.getFile().getFileName() + "/" + p.getFileName();
                    try {
                        if (Files.size(p) > ImportedGlyphCatalog.MAX_MANIFEST_BYTES) {
                            LOGGER.error("[ImportedGlyphs] {} exceeds {} bytes and was ignored", source, ImportedGlyphCatalog.MAX_MANIFEST_BYTES);
                            return;
                        }
                        manifests.put(source, Files.readString(p, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        LOGGER.error("[ImportedGlyphs] could not read {}: {}", source, e.toString());
                    }
                });
            } catch (IOException e) {
                LOGGER.error("[ImportedGlyphs] could not list {} in {}: {}", DIRECTORY, info.getFile().getFileName(), e.toString());
            }
        }
        return manifests;
    }
}
