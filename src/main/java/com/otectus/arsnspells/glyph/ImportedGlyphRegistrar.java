package com.otectus.arsnspells.glyph;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registers every catalogued imported glyph with Ars Nouveau, once, during mod construction.
 *
 * <p>Ars registers a glyph item for every part in {@code GlyphRegistry} when its item registry
 * event fires, which is after every mod constructor, so inserting the parts here makes Ars
 * register their items under the same ids. The parts go straight into Ars's maps rather than
 * through {@code GlyphRegistry.registerSpell}: that method creates a separate server config
 * file per glyph, which would be several hundred files for one feature, and the settings it
 * would hold (enabled, tier, cost) belong to the imported-glyph behaviour layer instead. Every
 * field Ars reads from those configs is null-safe or overridden by {@link ImportedIronEffect}.
 *
 * <p>Registration happens whether or not Iron's is installed. Nothing here or in the part
 * classes touches an Iron's type.
 */
public final class ImportedGlyphRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportedGlyphRegistrar.class);
    private static final Map<ResourceLocation, ImportedIronEffect> PARTS = new LinkedHashMap<>();
    private static boolean registered;

    private ImportedGlyphRegistrar() {}

    public static synchronized void registerAll() {
        if (registered) return;
        registered = true;
        ImportedGlyphCatalog catalog = ImportedGlyphCatalog.get();
        int collisions = 0;
        for (ImportedGlyphDefinition definition : catalog.definitions()) {
            ImportedIronEffect part = new ImportedIronEffect(definition);
            AbstractSpellPart previous = GlyphRegistry.getSpellpartMap().putIfAbsent(part.getRegistryName(), part);
            if (previous != null) {
                collisions++;
                LOGGER.error("Imported glyph id {} is already registered by {}; that glyph is skipped",
                    part.getRegistryName(), previous.getClass().getName());
                continue;
            }
            GlyphRegistry.getGlyphItemMap().put(part.getRegistryName(), part::getGlyph);
            PARTS.put(part.getRegistryName(), part);
        }
        LOGGER.info("Registered {} imported Iron's glyphs ({} Iron's spells){}", PARTS.size(), catalog.spells().size(),
            collisions == 0 ? "" : ", " + collisions + " id collision(s) skipped");
    }

    public static Optional<ImportedIronEffect> part(ResourceLocation glyphId) {
        return Optional.ofNullable(PARTS.get(glyphId));
    }

    public static Optional<ImportedIronEffect> part(ImportedGlyphKey key) {
        return part(key.glyphId());
    }

    public static Collection<ImportedIronEffect> parts() {
        return Collections.unmodifiableCollection(PARTS.values());
    }
}
