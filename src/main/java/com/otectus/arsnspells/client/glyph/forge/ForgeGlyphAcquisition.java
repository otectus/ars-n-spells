package com.otectus.arsnspells.client.glyph.forge;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.client.gui.book.GlyphUnlockMenu;
import com.hollingsworth.arsnouveau.client.gui.buttons.UnlockGlyphButton;
import com.hollingsworth.arsnouveau.common.crafting.recipes.GlyphRecipe;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
import com.otectus.arsnspells.client.glyph.BrowserPlatform;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Real recipe discovery and grouping for Ars 4.12.7's registry-based Scribe's Table screen. */
public final class ForgeGlyphAcquisition {
    public record Choice(ImportedIronEffect part, GlyphRecipe recipe) {}
    public record Group(ResourceLocation spellId, String name, List<Choice> choices) {}

    private ForgeGlyphAcquisition() {}

    /**
     * Scan the recipe manager, not the glyph registry. A datapack recipe may use an ID different
     * from its output glyph, and that legitimate route must still appear and select its own recipe.
     */
    public static List<Group> groups() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();
        Map<ResourceLocation, List<Choice>> grouped = new LinkedHashMap<>();
        for (GlyphRecipe recipe : minecraft.level.getRecipeManager().getAllRecipesFor(RecipeRegistry.GLYPH_TYPE.get())) {
            AbstractSpellPart output;
            try {
                output = recipe.getSpellPart();
            } catch (RuntimeException | LinkageError malformed) {
                continue;
            }
            if (!(output instanceof ImportedIronEffect imported) || !BrowserPlatform.available(imported)) continue;
            grouped.computeIfAbsent(imported.definition().spell(), unused -> new ArrayList<>())
                .add(new Choice(imported, recipe));
        }
        ArrayList<Group> result = new ArrayList<>(grouped.size());
        grouped.forEach((spell, choices) -> {
            choices.sort(Comparator.comparingInt((Choice choice) -> choice.part().definition().level())
                .thenComparing(choice -> choice.recipe().getId().toString()));
            var definition = choices.get(0).part().definition();
            String name = Component.translatableWithFallback(
                "spell." + spell.getNamespace() + "." + spell.getPath(), definition.spellName()).getString();
            result.add(new Group(spell, name, List.copyOf(choices)));
        });
        result.sort(Comparator.comparing((Group group) -> group.name().toLowerCase(Locale.ROOT))
            .thenComparing(group -> group.spellId().toString()));
        return List.copyOf(result);
    }

    public static Set<AbstractSpellPart> realRecipeParts() {
        LinkedHashSet<AbstractSpellPart> result = new LinkedHashSet<>();
        for (Group group : groups()) for (Choice choice : group.choices()) result.add(choice.part());
        return Set.copyOf(result);
    }

    public static boolean hasAvailableImports() {
        return com.otectus.arsnspells.glyph.ImportedGlyphRegistrar.parts().stream()
            .anyMatch(BrowserPlatform::available);
    }

    public static boolean hasPressOnlyImports() {
        Set<AbstractSpellPart> recipes = realRecipeParts();
        return com.otectus.arsnspells.glyph.ImportedGlyphRegistrar.parts().stream()
            .anyMatch(part -> BrowserPlatform.available(part) && !recipes.contains(part));
    }

    /** Select exactly the discovered recipe while retaining Ars's native preview and confirmation. */
    public static void select(GlyphUnlockMenu host, Choice choice) {
        UnlockGlyphButton synthetic = new UnlockGlyphButton(0, 0, false, choice.part(), ignored -> {});
        synthetic.recipe = choice.recipe();
        host.onGlyphClick(synthetic);
    }
}
