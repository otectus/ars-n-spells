package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.CatalogManifests;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers every catalogued imported glyph with Ars Nouveau, from the mod constructor.
 *
 * <p>Each variant goes straight into Ars's spell-part and glyph-item maps, which Ars reads during
 * its own item registration: the part under {@link ImportedGlyphKey#partId()}, its glyph item
 * under {@link ImportedGlyphKey#itemId()} (Ars registers glyph items by that map's key). This
 * runs whether or not Iron's is installed: identities never depend on the optional mod, so a
 * learned or composed imported glyph is always a known part and never decodes as Ars's unknown-id
 * fallback (Break). Never called again after startup; reloads only change behaviour.
 */
public final class ImportedGlyphRegistration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportedGlyphRegistration.class);
    private static final Map<ImportedGlyphKey, ImportedIronEffect> EFFECTS = new LinkedHashMap<>();
    private static boolean registered;

    private ImportedGlyphRegistration() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // 3.4.1: assembled from required and optional manifests and checked against the published
        // ledger; a broken required catalog stops startup here rather than registering fewer ids.
        java.util.List<ImportedGlyphCatalog.Manifest> live = CatalogManifests.discoverAll();
        // 3.4.1 review SAVE-01: every catalog ever accepted, read back as identities only (assembled after every live one).
        java.nio.file.Path archive = com.otectus.arsnspells.glyph.catalog.RetainedCatalogs.directory();
        java.util.List<ImportedGlyphCatalog.Manifest> manifests = new java.util.ArrayList<>(live);
        manifests.addAll(com.otectus.arsnspells.glyph.catalog.RetainedCatalogs.sources(archive, live));
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.assemble(manifests, ImportedGlyphCatalog.ledger());
        com.otectus.arsnspells.glyph.catalog.RetainedCatalogs.retain(archive, live, catalog);
        // A rejected optional catalog registers nothing; say so where a pack maker looks first.
        for (String problem : catalog.errors()) LOGGER.error("[ImportedGlyphs] catalog {}", problem);
        ImportedGlyphCatalog.install(catalog);
        long started = System.nanoTime();
        for (ImportedGlyphKey key : catalog.keys()) {
            CatalogEntry entry = catalog.entry(key.ironSpellId());
            if (GlyphRegistry.getSpellpartMap().containsKey(key.partId()) || GlyphRegistry.getGlyphItemMap().containsKey(key.itemId())) {
                LOGGER.error("[ImportedGlyphs] {} or {} is already registered by another mod; skipped", key.partId(), key.itemId());
                continue;
            }
            ImportedIronEffect effect = new ImportedIronEffect(key, entry);
            GlyphRegistry.getSpellpartMap().put(key.partId(), effect);
            GlyphRegistry.getGlyphItemMap().put(key.itemId(), effect::getGlyph);
            EFFECTS.put(key, effect);
        }
        LOGGER.info("[ImportedGlyphs] registered {} imported glyph variants in {} ms", EFFECTS.size(),
            (System.nanoTime() - started) / 1_000_000);
    }

    public static Map<ImportedGlyphKey, ImportedIronEffect> effects() {
        return Collections.unmodifiableMap(EFFECTS);
    }

    public static ImportedIronEffect effect(ImportedGlyphKey key) {
        return EFFECTS.get(key);
    }
}
