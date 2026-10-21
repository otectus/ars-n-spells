package com.otectus.arsnspells.client.icons;

import com.mojang.serialization.MapCodec;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Optional;

/**
 * Atlas source for imported-glyph item icons: for every catalogued spell it adds the sprite
 * {@code ars_n_spells:item/iron_glyph_icon/<namespace>/<spell>}, read from the spell's own icon
 * ({@code <namespace>:textures/gui/spell_icons/<spell>.png}) when that resource exists and from the
 * ANS fallback icon when it does not, so a glyph item model never names a missing sprite: without
 * Iron's, or with a resource pack that drops an icon, the glyph still shows original ANS art.
 * Registered in the block atlas through {@code assets/minecraft/atlases/blocks.json}.
 */
public final class IronGlyphIconSource implements SpriteSource {
    public static final MapCodec<IronGlyphIconSource> CODEC = MapCodec.unit(IronGlyphIconSource::new);
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "iron_glyph_icons");
    private static final ResourceLocation FALLBACK =
        ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, "textures/item/iron_glyph/fallback_icon.png");
    static SpriteSourceType type;

    public static void setType(SpriteSourceType registered) {
        type = registered;
    }

    @Override
    public void run(ResourceManager resources, Output output) {
        Optional<Resource> fallback = resources.getResource(FALLBACK);
        for (CatalogEntry entry : ImportedGlyphCatalog.get().entries()) {
            ResourceLocation spell = entry.spellId();
            ResourceLocation sprite = ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID,
                "item/iron_glyph_icon/" + spell.getNamespace() + "/" + spell.getPath());
            Optional<Resource> icon = resources.getResource(ResourceLocation.fromNamespaceAndPath(spell.getNamespace(),
                "textures/gui/spell_icons/" + spell.getPath() + ".png"));
            Optional<Resource> chosen = icon.isPresent() ? icon : fallback;
            chosen.ifPresent(resource -> output.add(sprite, resource));
        }
    }

    @Override
    public SpriteSourceType type() {
        return type;
    }
}
