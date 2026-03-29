package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.registry.RitualRegistry;
import net.minecraftforge.fml.ModList;

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
        registered = true;
    }
}
