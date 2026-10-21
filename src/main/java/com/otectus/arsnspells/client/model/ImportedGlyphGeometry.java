package com.otectus.arsnspells.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.minecraftforge.client.model.geometry.UnbakedGeometryHelper;
import org.joml.Vector3f;

import java.util.Map;
import java.util.function.Function;

/**
 * Client-only item geometry shared by every imported glyph: ANS's blank glyph plate, the Iron's
 * spell's own icon inset on its face, and a small level marker in the corner.
 *
 * <p>The icon is a runtime reference to Iron's texture ({@code <ns>:gui/spell_icons/<spell>}),
 * stitched into the block atlas by ANS's atlas source; nothing of Iron's is copied. When the icon
 * is not there (Iron's absent, or an addon spell without one) the plate shows ANS's own fallback
 * rune instead of the missing-texture checkerboard. The result is ordinary baked quads, so a
 * screen full of glyphs renders like any other items.
 */
public final class ImportedGlyphGeometry implements IUnbakedGeometry<ImportedGlyphGeometry> {
    public static final String LOADER_NAME = "imported_glyph";
    public static final ResourceLocation PLATE = new ResourceLocation("ars_n_spells", "item/imported_glyph/plate");
    public static final ResourceLocation FALLBACK_RUNE = new ResourceLocation("ars_n_spells", "item/imported_glyph/rune");
    /** Level markers exist for the levels the catalog registers; a level beyond them shows none. */
    public static final int MARKED_LEVELS = 10;

    public static final IGeometryLoader<ImportedGlyphGeometry> LOADER = ImportedGlyphGeometry::read;

    private final ResourceLocation icon;
    private final int level;

    private ImportedGlyphGeometry(ResourceLocation icon, int level) {
        this.icon = icon;
        this.level = level;
    }

    private static ImportedGlyphGeometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
        ResourceLocation icon = ResourceLocation.tryParse(GsonHelper.getAsString(json, "icon"));
        if (icon == null) throw new JsonParseException("imported_glyph: invalid icon " + json.get("icon"));
        int level = GsonHelper.getAsInt(json, "level");
        if (level < 1) throw new JsonParseException("imported_glyph: invalid level " + level);
        return new ImportedGlyphGeometry(icon, level);
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
        TextureAtlasSprite plate = sprite(spriteGetter, PLATE);
        TextureAtlasSprite face = sprite(spriteGetter, icon);
        if (missing(face)) face = sprite(spriteGetter, FALLBACK_RUNE);
        IModelBuilder<?> builder = IModelBuilder.of(context.useAmbientOcclusion(), context.useBlockLight(), context.isGui3d(),
            context.getTransforms(), overrides, plate, context.getRenderType(context.getRenderTypeHint()));
        // The plate, extruded exactly like a generated item.
        for (BakedQuad quad : UnbakedGeometryHelper.bakeElements(UnbakedGeometryHelper.createUnbakedItemElements(0, plate.contents()),
                $ -> plate, modelState, modelLocation)) builder.addUnculledFace(quad);
        // The spell's icon, inset on both faces a tenth of a pixel proud of the plate.
        flat(builder, face, 2, 2, 14, 14, 8.6f, Direction.SOUTH, modelState, modelLocation);
        flat(builder, face, 2, 2, 14, 14, 7.4f, Direction.NORTH, modelState, modelLocation);
        if (level <= MARKED_LEVELS) {
            TextureAtlasSprite marker = sprite(spriteGetter, new ResourceLocation("ars_n_spells", "item/imported_glyph/level_" + level));
            if (!missing(marker)) flat(builder, marker, 0, 0, 16, 16, 8.7f, Direction.SOUTH, modelState, modelLocation);
        }
        return builder.build();
    }

    /** One flat, single-sided quad of {@code sprite} covering [x0,x1]x[y0,y1] at depth {@code z}, facing {@code side}. */
    private static void flat(IModelBuilder<?> builder, TextureAtlasSprite sprite, float x0, float y0, float x1, float y1, float z,
                             Direction side, ModelState modelState, ResourceLocation modelLocation) {
        BlockElementFace face = new BlockElementFace(null, -1, "#face", new BlockFaceUV(new float[]{0, 0, 16, 16}, 0));
        BlockElement element = new BlockElement(new Vector3f(x0, y0, z), new Vector3f(x1, y1, z), Map.of(side, face), null, true);
        builder.addUnculledFace(UnbakedGeometryHelper.bakeElementFace(element, face, sprite, side, modelState, modelLocation));
    }

    private static TextureAtlasSprite sprite(Function<Material, TextureAtlasSprite> spriteGetter, ResourceLocation id) {
        return spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS, id));
    }

    private static boolean missing(TextureAtlasSprite sprite) {
        return sprite == null || MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name());
    }
}
