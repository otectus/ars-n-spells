package com.otectus.arsnspells.client.glyph;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.client.model.ImportedGlyphGeometry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The client resources an imported-glyph identity needs when nothing installed supplies them
 * (3.4.1 review CATALOG-02): an identity registered by a loose catalog in
 * {@code config/ars_n_spells/glyph_catalogs/} has no item model, name or atlas entry of its own.
 *
 * <p>Only what is missing is generated, so a catalog-only jar's or ANS's own bundled files are never
 * shadowed: an item model and a name for every identity whose model no mod file ships, and a block
 * atlas entry for every catalogued icon that lives outside {@code gui/spell_icons/} (the directory
 * ANS's atlas already stitches) and that an installed mod file actually contains. Nothing generated
 * names a texture that may be absent without a fallback: the {@link ImportedGlyphGeometry} draws
 * ANS's rune when the icon sprite is not in the atlas, and draws a level marker only for the levels
 * that have one ({@value ImportedGlyphGeometry#MARKED_LEVELS} and below); a higher level shows the
 * plain plate rather than pretending to a marker of its own.
 *
 * <p>Pure: the catalog and the installed files are passed in, so the rules are unit-tested.
 */
public final class ImportedGlyphFallbackContent {
    public static final ResourceLocation LANG = new ResourceLocation("ars_n_spells", "lang/en_us.json");
    public static final ResourceLocation BLOCK_ATLAS = new ResourceLocation("minecraft", "atlases/blocks.json");
    /** The icon directory ANS's own block-atlas source already stitches, in every namespace. */
    static final String STITCHED_ICON_DIRECTORY = "gui/spell_icons/";

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
        JsonArray atlas = new JsonArray();
        Set<ResourceLocation> seenSprites = new HashSet<>();
        for (ImportedGlyphDefinition definition : catalog.definitions()) {
            ResourceLocation sprite = iconSprite(definition);
            if (!sprite.getPath().startsWith(STITCHED_ICON_DIRECTORY) && seenSprites.add(sprite)
                    && installed.has(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png")) {
                JsonObject single = new JsonObject();
                single.addProperty("type", "single");
                single.addProperty("resource", sprite.toString());
                atlas.add(single);
            }
            ResourceLocation model = modelFile(definition);
            if (installed.has(model.getNamespace(), model.getPath())) continue;
            files.put(model, model(definition, sprite).toString());
            lang.addProperty(nameKey(definition), definition.spellName() + " " + definition.levelNumeral());
        }
        if (lang.size() > 0) files.put(LANG, lang.toString());
        if (!atlas.isEmpty()) {
            JsonObject root = new JsonObject();
            root.add("sources", atlas);
            files.put(BLOCK_ATLAS, root.toString());
        }
        return files;
    }

    /**
     * The icon sprite the glyph shows: the catalogued icon texture when the catalog names one, else the
     * spell's icon in Iron's standard place. Either way the geometry falls back to ANS's rune when the
     * sprite is not stitched.
     */
    public static ResourceLocation iconSprite(ImportedGlyphDefinition definition) {
        ResourceLocation icon = definition.icon();
        if (icon != null && icon.getPath().startsWith("textures/") && icon.getPath().endsWith(".png"))
            return new ResourceLocation(icon.getNamespace(), icon.getPath().substring("textures/".length(), icon.getPath().length() - ".png".length()));
        return new ResourceLocation(definition.spell().getNamespace(), STITCHED_ICON_DIRECTORY + definition.spell().getPath());
    }

    /** {@code ars_n_spells:models/item/iron/<namespace>/<spell>/level_<n>.json}: the item model of the identity's glyph. */
    public static ResourceLocation modelFile(ImportedGlyphDefinition definition) {
        return new ResourceLocation(definition.glyphId().getNamespace(), "models/item/" + definition.glyphId().getPath() + ".json");
    }

    public static String nameKey(ImportedGlyphDefinition definition) {
        return definition.glyphId().getNamespace() + ".glyph_name." + definition.glyphId().getPath();
    }

    /** The model the shipped glyphs use, as {@code tools/glyph_catalog_pack.py} writes it for a catalog-only jar. */
    static JsonObject model(ImportedGlyphDefinition definition, ResourceLocation sprite) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "forge:item/default");
        model.addProperty("loader", "ars_n_spells:" + ImportedGlyphGeometry.LOADER_NAME);
        model.addProperty("icon", sprite.toString());
        model.addProperty("level", definition.level());
        return model;
    }
}
