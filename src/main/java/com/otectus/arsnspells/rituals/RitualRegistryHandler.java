package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.registry.RitualRegistry;
import com.hollingsworth.arsnouveau.common.items.RitualTablet;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;

public class RitualRegistryHandler {
    private static boolean registered = false;

    public static void registerRituals() {
        if (registered) {
            return;
        }

        // Iron's-independent rituals are registered unconditionally. The
        // uninscribe ritual touches only known root component keys and remains
        // useful even after Iron's Spellbooks has been uninstalled, when a
        // player might still hold legacy inscribed items.
        RitualRegistry.registerRitual(new SpellUninscriptionRitual());
        spliceTablet(SpellUninscriptionRitual.REGISTRY_PATH,
            ModItemsRegistry.spellUninscriptionTablet());

        if (!ModList.get().isLoaded("irons_spellbooks")) {
            registered = true;
            return;
        }

        RitualRegistry.registerRitual(new ManaInfusionRitual());
        RitualRegistry.registerRitual(new SpellTranscriptionRitual());
        RitualRegistry.registerRitual(new ManaWellRitual());

        spliceTablet(SpellTranscriptionRitual.REGISTRY_PATH,
            ModItemsRegistry.spellTranscriptionTablet());

        registered = true;
    }

    /**
     * Inserts the tablet into Ars's {@code ritualItemMap} so brazier and JEI
     * lookups by ResourceLocation resolve. Ars's own item-registration loop
     * iterates {@code ritualMap} during item RegisterEvent and cannot see
     * rituals added at common setup, so we splice manually.
     */
    private static void spliceTablet(String path, DeferredHolder<net.minecraft.world.item.Item, RitualTablet> tabletRef) {
        if (tabletRef == null || !tabletRef.isBound()) {
            return;
        }
        RitualRegistry.getRitualItemMap().put(
            ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, path),
            tabletRef.get()
        );
    }
}
