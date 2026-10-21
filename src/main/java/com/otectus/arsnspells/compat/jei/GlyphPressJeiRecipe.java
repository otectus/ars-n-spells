package com.otectus.arsnspells.compat.jei;

import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridge;
import com.otectus.arsnspells.compat.irons.glyph.IronGlyphBridges;
import com.otectus.arsnspells.glyph.GlyphAvailability;
import com.otectus.arsnspells.glyph.ImportedGlyphCatalog;
import com.otectus.arsnspells.glyph.ImportedGlyphDefinition;
import com.otectus.arsnspells.glyph.ImportedGlyphRegistrar;
import com.otectus.arsnspells.glyph.recipe.GlyphMappingSnapshot;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One Glyph Press conversion as the recipe viewer shows it: a genuine Iron's scroll holding the
 * spell at its level, a blank plate, the tier's catalysts, and that exact level's glyph.
 *
 * <p>Built from the same pieces the Press's planner uses ({@link GlyphAvailability},
 * {@link GlyphMappingSnapshot#pressTerms}), so a conversion is listed only if the Press would
 * accept it, at the cost the Press would charge. Without Iron's there is nothing to list.
 */
public record GlyphPressJeiRecipe(ImportedGlyphDefinition definition, ItemStack scroll, ItemStack plate,
                                  List<ItemStack> catalysts, ItemStack output, int tier,
                                  GlyphMappingSnapshot.PressTerms terms) {

    public ResourceLocation id() {
        return definition.glyphId();
    }

    public static List<GlyphPressJeiRecipe> build() {
        IronGlyphBridge bridge = IronGlyphBridges.get();
        if (!bridge.present() || ModItemsRegistry.blankGlyphPlate() == null) return List.of();
        ItemStack plate = new ItemStack(ModItemsRegistry.blankGlyphPlate().get());
        GlyphMappingSnapshot snapshot = GlyphMappingSnapshot.current();
        List<GlyphPressJeiRecipe> recipes = new ArrayList<>();
        List<ImportedGlyphDefinition> definitions = new ArrayList<>(ImportedGlyphCatalog.get().definitions());
        definitions.sort(Comparator.comparing((ImportedGlyphDefinition d) -> d.spell().toString()).thenComparingInt(ImportedGlyphDefinition::level));
        for (ImportedGlyphDefinition definition : definitions) {
            if (!GlyphAvailability.status(definition).available()) continue;
            ItemStack output = ImportedGlyphRegistrar.part(definition.key()).map(part -> new ItemStack(part.getGlyph())).orElse(ItemStack.EMPTY);
            ItemStack scroll = bridge.filledScroll(definition.spell(), definition.level());
            if (output.isEmpty() || scroll.isEmpty()) continue;
            int tier = GlyphAvailability.tier(definition);
            GlyphMappingSnapshot.PressTerms terms = snapshot.pressTerms(definition, tier);
            recipes.add(new GlyphPressJeiRecipe(definition, scroll, plate, catalysts(terms), output, tier, terms));
        }
        return recipes;
    }

    private static List<ItemStack> catalysts(GlyphMappingSnapshot.PressTerms terms) {
        List<ItemStack> stacks = new ArrayList<>();
        if (terms.catalystCount() <= 0) return stacks;
        TagKey<Item> tag = TagKey.create(Registries.ITEM, terms.catalystTag());
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) stacks.add(new ItemStack(holder.value(), terms.catalystCount()));
        return stacks;
    }
}
