package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.registry.RitualRegistry;
import net.minecraftforge.fml.ModList;

public class RitualRegistryHandler {
    public static void registerRituals() {
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            return;
        }
        // Logic finalized: All spatial integration rituals registered to Ars Nouveau
        RitualRegistry.registerRitual(new ManaInfusionRitual());
        RitualRegistry.registerRitual(new SpellTranscriptionRitual());
        RitualRegistry.registerRitual(new ManaWellRitual());
    }
}
