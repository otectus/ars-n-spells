package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.registry.RitualRegistry;
import com.hollingsworth.arsnouveau.common.items.RitualTablet;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.registry.ModItemsRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.RegistryObject;

public class RitualRegistryHandler {
    private static boolean registered = false;

    public static void registerRituals() {
        if (registered) {
            return;
        }
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            return;
        }
        RitualRegistry.registerRitual(new ManaInfusionRitual());
        RitualRegistry.registerRitual(new SpellTranscriptionRitual());
        RitualRegistry.registerRitual(new ManaWellRitual());

        // Splice our RitualTablet into Ars's ritualItemMap so lookups from the
        // brazier (and JEI) find it. Ars's own loop that populates this map
        // runs during item registration and cannot see a ritual added here at
        // common-setup time, so we do it ourselves.
        RegistryObject<RitualTablet> tabletRef = ModItemsRegistry.spellTranscriptionTablet();
        if (tabletRef != null && tabletRef.isPresent()) {
            RitualRegistry.getRitualItemMap().put(
                new ResourceLocation(ArsNSpells.MODID, SpellTranscriptionRitual.REGISTRY_PATH),
                tabletRef.get()
            );
        }

        registered = true;
    }
}
