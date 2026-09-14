package com.otectus.arsnspells.gametest;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.registry.ModBlocksRegistry;
import com.otectus.arsnspells.registry.ModCreativeTabs;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import com.otectus.arsnspells.rituals.ManaInfusionRitual;
import com.otectus.arsnspells.rituals.ManaWellRitual;
import com.otectus.arsnspells.rituals.SpellTranscriptionRitual;
import com.otectus.arsnspells.rituals.SpellUninscriptionRitual;
import com.otectus.arsnspells.rituals.SpellbookBindingRitual;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * The mod's items belong in the mod's own creative tab, and nowhere else.
 *
 * <p>Before 3.2.0 they were scattered: the Spell Loom sat in vanilla {@code FUNCTIONAL_BLOCKS}
 * and the five ritual tablets surfaced in Ars Nouveau's tab, the latter as an unavoidable side
 * effect of the {@code RitualRegistry} splice that braziers and JEI depend on.
 * {@link ModCreativeTabs} publishes an {@code ars_n_spells} tab and strips our items back out
 * of Ars's.
 *
 * <p>These drive the real tab machinery rather than inspecting source or registry declarations.
 * {@code CreativeModeTab.buildContents} runs the tab's display generator and then fires
 * {@code BuildCreativeModeTabContentsEvent} through {@code ForgeHooks}, so calling it here
 * exercises the removal listener exactly as the creative menu does.
 *
 * <p>The search-tab assertion deliberately checks our own tab's search set rather than building
 * the vanilla Search tab. Vanilla builds every {@code Type.CATEGORY} tab first and Search last,
 * and Search is a plain union of the category tabs' search sets — so membership in ours is the
 * property that matters, and asserting it does not require building every other mod's tab on a
 * dedicated server.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class CreativeTabGameTests {

    private CreativeTabGameTests() {}

    private static final ResourceKey<CreativeModeTab> ARS_NOUVEAU_GENERAL =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "general"));

    /**
     * A floor on how much of Ars Nouveau's own tab must survive our removal. Ars 4.12.7 puts
     * several hundred items there; anything near this number means the predicate has stopped
     * discriminating by namespace.
     */
    private static final int MIN_ARS_NOUVEAU_TAB_SIZE = 50;

    /** The tab exists in the registry, so the creative menu has somewhere to put our items. */
    @GameTest(template = "platform")
    public static void ansTab_isRegistered(GameTestHelper helper) {
        if (BuiltInRegistries.CREATIVE_MODE_TAB.get(ModCreativeTabs.MAIN.getKey()) == null) {
            helper.fail("the ars_n_spells creative tab is not registered");
        }
        helper.succeed();
    }

    /** Every item the mod publishes is in the tab, and nothing of ours is missing from it. */
    @GameTest(template = "platform")
    public static void ansTab_holdsEveryAnsItem(GameTestHelper helper) {
        CreativeModeTab tab = require(helper, ModCreativeTabs.MAIN.getKey());
        tab.buildContents(params(helper));

        Set<String> actual = ansPathsIn(tab.getDisplayItems());
        Set<String> expected = expectedPaths();
        if (!actual.equals(expected)) {
            helper.fail("the ars_n_spells tab holds " + actual + " but should hold " + expected
                + " (irons_spellbooks loaded: " + IronsCompat.isLoaded() + ")");
        }
        helper.succeed();
    }

    /**
     * The tablets are gone from Ars Nouveau's tab. This is the regression guard on the removal
     * listener: Ars's generator enumerates {@code RitualRegistry.getRitualItemMap()}, which ANS
     * must keep spliced, so the tablets reappear there the moment the listener stops working.
     *
     * <p>Ars's own items must survive. Without that half, a removal predicate broad enough to
     * empty someone else's tab would pass this test.
     */
    @GameTest(template = "platform")
    public static void arsNouveauTab_hasNoAnsItems(GameTestHelper helper) {
        CreativeModeTab tab = require(helper, ARS_NOUVEAU_GENERAL);
        tab.buildContents(params(helper));

        Set<String> leaked = ansPathsIn(tab.getDisplayItems());
        if (!leaked.isEmpty()) {
            helper.fail("ars_n_spells items still showing in the Ars Nouveau creative tab: "
                + leaked);
        }
        int survivors = tab.getDisplayItems().size();
        if (survivors < MIN_ARS_NOUVEAU_TAB_SIZE) {
            helper.fail("the Ars Nouveau creative tab is down to " + survivors + " items — the "
                + "ars_n_spells removal is stripping items that are not ours");
        }
        helper.succeed();
    }

    /**
     * The tab icon resolves on an install without Iron's Spellbooks. Meaningful on the default
     * GameTest profile, which is Iron's-less: four of the five tablets do not exist there, so an
     * icon sourced from one of them would resolve to nothing (or throw on a null accessor).
     */
    @GameTest(template = "platform")
    public static void ansTab_iconResolvesWithoutIrons(GameTestHelper helper) {
        CreativeModeTab tab = require(helper, ModCreativeTabs.MAIN.getKey());
        ItemStack icon = tab.getIconItem();
        if (icon == null || icon.isEmpty()) {
            helper.fail("the ars_n_spells tab icon resolved to nothing (irons_spellbooks loaded: "
                + IronsCompat.isLoaded() + ") — the creative menu would show a blank tab button");
        }
        helper.succeed();
    }

    /**
     * Removing our items from a foreign tab must not cost them their place in creative search.
     * They keep it because our own tab contributes them with the default visibility.
     */
    @GameTest(template = "platform")
    public static void ansItems_stayInCreativeSearch(GameTestHelper helper) {
        CreativeModeTab tab = require(helper, ModCreativeTabs.MAIN.getKey());
        tab.buildContents(params(helper));

        Set<String> searchable = ansPathsIn(tab.getSearchTabDisplayItems());
        Set<String> expected = expectedPaths();
        if (!searchable.containsAll(expected)) {
            Set<String> missing = new TreeSet<>(expected);
            missing.removeAll(searchable);
            helper.fail("items the creative Search tab can no longer reach: " + missing);
        }
        helper.succeed();
    }

    /** The Spell Loom no longer squats in a vanilla tab. */
    @GameTest(template = "platform")
    public static void spellLoom_isNotInVanillaFunctionalBlocks(GameTestHelper helper) {
        CreativeModeTab tab = require(helper, CreativeModeTabs.FUNCTIONAL_BLOCKS);
        tab.buildContents(params(helper));

        Set<String> leaked = ansPathsIn(tab.getDisplayItems());
        if (!leaked.isEmpty()) {
            helper.fail("ars_n_spells items still showing in vanilla Functional Blocks: " + leaked);
        }
        helper.succeed();
    }

    private static CreativeModeTab require(GameTestHelper helper, ResourceKey<CreativeModeTab> key) {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(key);
        if (tab == null) {
            helper.fail("creative tab " + key.location() + " is not registered");
        }
        return tab;
    }

    /** The same parameters the game builds tabs with, sourced from the running server. */
    private static CreativeModeTab.ItemDisplayParameters params(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return new CreativeModeTab.ItemDisplayParameters(
            level.enabledFeatures(), true, level.registryAccess());
    }

    /** Registry paths of the {@code ars_n_spells} items among {@code stacks}. */
    private static Set<String> ansPathsIn(Collection<ItemStack> stacks) {
        Set<String> paths = new TreeSet<>();
        for (ItemStack stack : stacks) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && ArsNSpells.MODID.equals(id.getNamespace())) {
                paths.add(id.getPath());
            }
        }
        return paths;
    }

    /** Every item the mod registers in the current environment. */
    private static Set<String> expectedPaths() {
        Set<String> paths = new TreeSet<>();
        paths.add(ModBlocksRegistry.SPELL_LOOM_ITEM.getId().getPath());
        paths.add(SpellUninscriptionRitual.REGISTRY_PATH);
        paths.add(ModItemsRegistry.blankScroll().getId().getPath());
        if (IronsCompat.isLoaded()) {
            paths.add(SpellTranscriptionRitual.REGISTRY_PATH);
            paths.add(SpellbookBindingRitual.REGISTRY_PATH);
            paths.add(ManaInfusionRitual.REGISTRY_PATH);
            paths.add(ManaWellRitual.REGISTRY_PATH);
        }
        return paths;
    }
}
