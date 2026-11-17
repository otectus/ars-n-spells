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
public final class GlyphPressJeiRecipe {
    private final ImportedGlyphDefinition definition;
    private final ItemStack scroll;
    private final ItemStack plate;
    private final List<ItemStack> catalysts;
    private final ItemStack output;
    private final int tier;
    private final GlyphMappingSnapshot.PressTerms terms;

    public GlyphPressJeiRecipe(ImportedGlyphDefinition definition, ItemStack scroll, ItemStack plate, List<ItemStack> catalysts,
                               ItemStack output, int tier, GlyphMappingSnapshot.PressTerms terms) {
        this.definition = definition;
        this.scroll = scroll;
        this.plate = plate;
        this.catalysts = catalysts;
        this.output = output;
        this.tier = tier;
        this.terms = terms;
    }

    public ImportedGlyphDefinition definition() { return definition; }
    public ItemStack scroll() { return scroll; }
    public ItemStack plate() { return plate; }
    public List<ItemStack> catalysts() { return catalysts; }
    public ItemStack output() { return output; }
    public int tier() { return tier; }
    public GlyphMappingSnapshot.PressTerms terms() { return terms; }

    public ResourceLocation id() {
        return definition.glyphId();
    }

    /**
     * What makes two entries the same conversion for the viewer: every material input (3.4.1 review
     * VIEW-01): the glyph, its tier and terms, and the catalyst tag's resolved members. A tag reload that
     * swaps a member is a different recipe, so the viewer stops offering the old item.
     */
    String key() {
        StringBuilder members = new StringBuilder();
        for (ItemStack catalyst : catalysts) members.append(BuiltInRegistries.ITEM.getKey(catalyst.getItem())).append('x').append(catalyst.getCount()).append(',');
        return definition.glyphId() + "|" + tier + "|" + terms + "|" + members;
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
