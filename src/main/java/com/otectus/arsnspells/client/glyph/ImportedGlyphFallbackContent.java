package com.otectus.arsnspells.client.glyph;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.TreeMap;

/**
 * The client resources an imported-glyph identity needs when nothing installed supplies them
 * (3.4.1 review CATALOG-02): an identity registered by a loose catalog in
 * {@code config/ars_n_spells/glyph_catalogs/} has no item model or name of its own. Its icon needs
 * nothing extra: {@code IronGlyphIconSource} stitches a sprite for every catalogued spell, from its
 * catalogued icon, Iron's standard icon or ANS's fallback.
 *
 * <p>Only what is missing is generated, so a catalog-only jar's or ANS's own bundled files are never
 * shadowed. Nothing generated names a texture that may be absent: level markers exist for levels
 * 1-{@value #MARKED_LEVELS}, so a higher level shows the plain frame (and, dormant, the unmarked dormant
 * model) rather than pretending to a marker of its own. The same model rules as
 * {@code tools/glyph_catalog_pack.py} and the Forge build's generator.
 *
 * <p>Pure: the catalog and the installed files are passed in, so the rules are unit-tested.
 */
public final class ImportedGlyphFallbackContent {
    public static final ResourceLocation LANG = ResourceLocation.fromNamespaceAndPath("ars_n_spells", "lang/en_us.json");
    /** The level marker sprites ANS ships stop at X. */
    public static final int MARKED_LEVELS = 10;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** Whether an installed mod file contains {@code assets/<namespace>/<path>}. */
    @FunctionalInterface
    public interface Installed {
        boolean has(String namespace, String path);
    }

    private ImportedGlyphFallbackContent() {}

    /** Every generated file, by resource location (namespace and path below {@code assets/<namespace>/}). */
    public static Map<ResourceLocation, String> build(ImportedGlyphCatalog catalog, Installed installed) {
        Map<ResourceLocation, String> files = new TreeMap<>();
        JsonObject lang = new JsonObject();
        for (CatalogEntry entry : catalog.entries()) {
            for (ImportedGlyphKey key : entry.keys()) {
                ResourceLocation model = modelFile(key);
                if (installed.has(model.getNamespace(), model.getPath())) continue;
                files.put(model, model(key).toString());
                lang.addProperty(nameKey(key), entry.name() + " " + numeral(key.baseLevel()));
            }
        }
        if (lang.size() > 0) files.put(LANG, lang.toString());
        return files;
    }

    /** {@code ars_n_spells:models/item/glyph_iron/<namespace>/<spell>/level_<n>.json}: the glyph item's model. */
    public static ResourceLocation modelFile(ImportedGlyphKey key) {
        return ResourceLocation.fromNamespaceAndPath(key.itemId().getNamespace(), "models/item/" + key.itemId().getPath() + ".json");
    }

    public static String nameKey(ImportedGlyphKey key) {
        return key.partId().getNamespace() + ".glyph_name." + key.partId().getPath();
    }

    static String numeral(int level) {
        return level > 0 && level < ROMAN.length ? ROMAN[level] : Integer.toString(level);
    }

    /** The model the shipped glyphs use, as {@code tools/glyph_catalog_pack.py} writes it for a catalog-only jar. */
    static JsonObject model(ImportedGlyphKey key) {
        ResourceLocation spell = key.ironSpellId();
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", "ars_n_spells:item/iron_glyph_icon/" + spell.getNamespace() + "/" + spell.getPath());
        textures.addProperty("layer1", "ars_n_spells:item/iron_glyph/frame");
        int level = key.baseLevel();
        if (level <= MARKED_LEVELS) textures.addProperty("layer2", "ars_n_spells:item/iron_glyph/level_" + level);
        JsonObject predicate = new JsonObject();
        predicate.addProperty("ars_n_spells:dormant", 1);
        JsonObject dormant = new JsonObject();
        dormant.add("predicate", predicate);
        dormant.addProperty("model", "ars_n_spells:item/glyph_iron_dormant/" + (level <= MARKED_LEVELS ? "level_" + level : "unmarked"));
        JsonArray overrides = new JsonArray();
        overrides.add(dormant);
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        model.add("textures", textures);
        model.add("overrides", overrides);
        return model;
    }
}
