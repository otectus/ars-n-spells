package com.otectus.arsnspells.gametest;

import com.hollingsworth.arsnouveau.api.registry.RitualRegistry;
import com.hollingsworth.arsnouveau.common.items.RitualTablet;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.compat.CompatIds;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.rituals.ManaInfusionRitual;
import com.otectus.arsnspells.rituals.ManaWellRitual;
import com.otectus.arsnspells.rituals.SpellTranscriptionRitual;
import com.otectus.arsnspells.rituals.SpellUninscriptionRitual;
import com.otectus.arsnspells.rituals.SpellbookBindingRitual;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Every ritual this mod registers must have a tablet a player can actually put on a brazier.
 *
 * <p>Two failures motivated this. The three shipped tablets had no item model or texture at
 * all from 3.0.0 onward, so they rendered as the missing-texture checkerboard — that half is
 * a resource-pack concern and is covered by {@code ItemAssetCompletenessTest}, since a
 * dedicated server never loads client assets. The other half is here: Mana Infusion and Mana
 * Well were registered as rituals with <em>no tablet item whatsoever</em>, which made them
 * unreachable in game, and neither the build nor the server said a word about it.
 *
 * <p>ANS registers its tablets itself rather than through Ars Nouveau's bulk loop (that loop
 * runs during Ars's item registration and cannot see rituals added at common setup), then
 * splices them into {@code RitualRegistry.getRitualItemMap()} so the brazier can resolve
 * them. Both halves are asserted, because either one alone leaves the ritual unusable.
 */
@GameTestHolder("ars_n_spells")
@PrefixGameTestTemplate(false)
public final class RitualTabletGameTests {

    private RitualTabletGameTests() {}

    /** Rituals that exist whether or not Iron's Spellbooks is installed. */
    private static final List<String> ALWAYS = List.of(
        SpellUninscriptionRitual.REGISTRY_PATH);

    /** Rituals gated on Iron's Spellbooks being present. */
    private static final List<String> IRONS_ONLY = List.of(
        SpellTranscriptionRitual.REGISTRY_PATH,
        SpellbookBindingRitual.REGISTRY_PATH,
        ManaInfusionRitual.REGISTRY_PATH,
        ManaWellRitual.REGISTRY_PATH);

    private static List<String> expectedPaths() {
        List<String> paths = new ArrayList<>(ALWAYS);
        if (IronsCompat.isLoaded()) {
            paths.addAll(IRONS_ONLY);
        }
        return paths;
    }

    /** The tablet item is registered, so it can be crafted, held and dropped. */
    @GameTest(template = "platform")
    public static void everyRitual_hasARegisteredTabletItem(GameTestHelper helper) {
        List<String> missing = new ArrayList<>();
        for (String path : expectedPaths()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, path);
            // containsKey, not getValue != null: the item registry has a default value, so
            // getValue hands back minecraft:air for an id that was never registered.
            Item item = BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
            if (!(item instanceof RitualTablet)) {
                missing.add(id + " -> " + item);
            }
        }
        if (!missing.isEmpty()) {
            helper.fail("rituals with no obtainable tablet item (unreachable in game): " + missing);
        }
        helper.succeed();
    }

    /** The tablet is spliced into Ars's map, so a brazier can resolve it back to its ritual. */
    @GameTest(template = "platform")
    public static void everyTablet_isResolvableFromTheBrazier(GameTestHelper helper) {
        List<String> broken = new ArrayList<>();
        for (String path : expectedPaths()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, path);
            if (RitualRegistry.getRitual(id) == null) {
                broken.add(id + ": ritual not registered");
                continue;
            }
            RitualTablet tablet = RitualRegistry.getRitualItemMap().get(id);
            if (tablet == null) {
                broken.add(id + ": tablet missing from Ars's ritual item map");
                continue;
            }
            if (!BuiltInRegistries.ITEM.containsKey(id) || tablet != BuiltInRegistries.ITEM.get(id)) {
                broken.add(id + ": the spliced tablet is not the registered item");
            }
        }
        if (!broken.isEmpty()) {
            helper.fail("tablets a brazier cannot resolve: " + broken);
        }
        helper.succeed();
    }

    /** Without Iron's, the Iron's-only tablets must be absent rather than half-registered. */
    @GameTest(template = "platform")
    public static void withoutIrons_ironsOnlyTabletsAreAbsent(GameTestHelper helper) {
        if (OptionalModGate.skipIfPresent(helper, CompatIds.IRONS_SPELLBOOKS)) {
            return;
        }
        List<String> leaked = new ArrayList<>();
        for (String path : IRONS_ONLY) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, path);
            if (BuiltInRegistries.ITEM.containsKey(id)
                || RitualRegistry.getRitualItemMap().containsKey(id)) {
                leaked.add(id.toString());
            }
        }
        if (!leaked.isEmpty()) {
            helper.fail("Iron's-only tablets must not exist on an Iron's-less install: " + leaked);
        }
        helper.succeed();
    }
}
