package com.otectus.arsnspells.compat.jei;

import com.otectus.arsnspells.spell.irons.ArsCrossProxyHiding;
import io.redspace.ironsspellbooks.jei.ArcaneAnvilJeiRecipe;
import io.redspace.ironsspellbooks.jei.ArcaneAnvilRecipeCategory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Hides Iron's Arcane Anvil recipes that were generated from ANS's internal proxy spells.
 *
 * <p><b>All coupling to Iron's non-API JEI classes lives here</b>, behind a single method the
 * plugin calls inside a try/catch. {@code ArcaneAnvilJeiRecipe} and
 * {@code ArcaneAnvilRecipeCategory} are internal to Iron's, not part of its published API, so
 * this is the one spot that has to be revisited if Iron's reshapes its JEI integration. Both
 * types, and the members used below, are byte-identical in Iron's 3.15.0 and 3.16.2.
 *
 * <p>Recipes are classified by their <em>displayed item stacks</em> rather than by reading the
 * recipe's {@code spell} field, which is package-private and would need reflection. The public
 * {@code getRecipeItems()} accessor gives the same answer through supported means.
 */
final class IronsProxyRecipeHider {

    private IronsProxyRecipeHider() {}

    /**
     * Hide every Arcane Anvil recipe whose displayed stacks are ANS proxy scrolls.
     *
     * <p>Iron's {@code getAffinityAttuneRecipes} maps one recipe per enabled spell with no
     * craftability filter, which is what surfaces the eight {@code ars_cross_*} entries. The
     * scroll-merge recipes happen to come out empty for the proxies (max level 1 gives an
     * empty level range), but they are covered by the same test rather than relying on that.
     *
     * @return how many recipes were hidden
     */
    static int hideProxyRecipes(IJeiRuntime runtime) {
        IRecipeManager recipeManager = runtime.getRecipeManager();
        RecipeType<ArcaneAnvilJeiRecipe> type = ArcaneAnvilRecipeCategory.ARCANE_ANVIL_RECIPE_RECIPE_TYPE;

        List<ArcaneAnvilJeiRecipe> ghosts = recipeManager.createRecipeLookup(type)
            .get()
            .filter(IronsProxyRecipeHider::isProxyRecipe)
            .toList();

        if (ghosts.isEmpty()) {
            return 0;
        }
        recipeManager.hideRecipes(type, ghosts);
        return ghosts.size();
    }

    /**
     * True when a recipe's inputs or outputs consist of ANS proxy scrolls.
     *
     * <p>Requires at least one proxy stack and no genuine spell stack, so a recipe that merely
     * happens to involve an unrelated scroll is never hidden.
     */
    private static boolean isProxyRecipe(ArcaneAnvilJeiRecipe recipe) {
        var items = recipe.getRecipeItems();
        List<ItemStack> all = new ArrayList<>();
        addAll(all, items.a());
        addAll(all, items.b());
        addAll(all, items.c());

        boolean sawProxy = false;
        for (ItemStack stack : all) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (ArsCrossProxyHiding.isProxyOnlyStack(stack)) {
                sawProxy = true;
            } else if (ArsCrossProxyHiding.isSpellContainerStack(stack)) {
                // A real spell is part of this recipe — it is not one of ours.
                return false;
            }
        }
        return sawProxy;
    }

    private static void addAll(List<ItemStack> sink, List<ItemStack> source) {
        if (source != null) {
            sink.addAll(source);
        }
    }
}
