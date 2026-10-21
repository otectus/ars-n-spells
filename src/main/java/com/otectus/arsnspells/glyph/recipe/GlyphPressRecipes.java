package com.otectus.arsnspells.glyph.recipe;

import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistration;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import com.otectus.arsnspells.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.behavior.Dormancy;
import com.otectus.arsnspells.glyph.behavior.GlyphBehavior;
import com.otectus.arsnspells.glyph.behavior.GlyphBehaviorSnapshot;
import com.otectus.arsnspells.glyph.behavior.VariantBehavior;
import com.otectus.arsnspells.glyph.catalog.CatalogEntry;
import com.otectus.arsnspells.glyph.catalog.ImportedGlyphCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Glyph Press's conversions as viewer entries, read from the same catalog and effective
 * behaviour the Press plans with: a real component-bearing Iron's scroll for each spell level, a
 * plate, the level's catalyst tag and count, Source, time and tier, and the exact glyph it makes.
 * Unavailable variants are left out; without Iron's there are none.
 */
public final class GlyphPressRecipes {

    /** One conversion as a viewer shows it. */
    public record Entry(ImportedGlyphKey key, ItemStack scroll, List<ItemStack> catalysts, int gems, int source, int ticks,
                        int tier, ItemStack output, boolean requiresLearning) {
    }

    /** Set by an optional recipe-viewer integration; run whenever the effective behaviour changes. */
    @Nullable private static volatile Runnable viewerRefresh;
    /** Set by an optional recipe-viewer integration: show the Glyph Press category. */
    @Nullable private static volatile Runnable viewerOpen;

    private GlyphPressRecipes() {
    }

    public static void setViewerRefresh(@Nullable Runnable refresh) {
        viewerRefresh = refresh;
    }

    public static void setViewerOpen(@Nullable Runnable open) {
        viewerOpen = open;
    }

    /** Show the Glyph Press category in the installed recipe viewer, if any. */
    public static void openViewer() {
        Runnable open = viewerOpen;
        if (open != null) open.run();
    }

    /** The behaviour changed (a sync, a reload or a config change): let a viewer rebuild its entries. */
    public static void behaviourChanged() {
        Runnable refresh = viewerRefresh;
        if (refresh != null) refresh.run();
    }

    public static List<Entry> entries() {
        if (!IronGlyphBridges.get().available()) return List.of();
        GlyphBehaviorSnapshot snapshot = GlyphBehavior.current();
        List<Entry> out = new ArrayList<>();
        for (CatalogEntry entry : ImportedGlyphCatalog.get().entries()) {
            if (!entry.supported()) continue;
            for (ImportedGlyphKey key : entry.keys()) {
                VariantBehavior behavior = snapshot.variant(key);
                if (!behavior.available() && behavior.dormancy() != Dormancy.NOT_SYNCED) continue;
                ItemStack scroll = IronGlyphBridges.get().scrollStack(key.ironSpellId(), key.baseLevel());
                ImportedIronEffect effect = ImportedGlyphRegistration.effect(key);
                if (scroll.isEmpty() || effect == null) continue;
                out.add(new Entry(key, scroll, catalysts(behavior), behavior.pressGems(), behavior.pressSource(),
                    behavior.pressTicks(), behavior.tier(), new ItemStack(effect.getGlyph()), entry.requiresLearning()));
            }
        }
        return out;
    }

    private static List<ItemStack> catalysts(VariantBehavior behavior) {
        ResourceLocation id = ResourceLocation.tryParse(behavior.catalyst());
        if (id == null) return List.of();
        List<ItemStack> stacks = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).ifPresent(tag ->
            tag.forEach(holder -> stacks.add(new ItemStack(holder, Math.max(1, behavior.pressGems())))));
        return stacks;
    }
}
