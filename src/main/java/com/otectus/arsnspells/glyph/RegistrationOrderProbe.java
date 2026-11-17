package com.otectus.arsnspells.glyph;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Records the order in which registries are filled at startup, so a GameTest can prove why the
 * imported-glyph identities come from an early catalog and not from Iron's live spell registry.
 *
 * <p>Ars registers a glyph item for every spell part when the item registry is filled. Forge fills
 * the vanilla registries first and modded ones afterwards, in name order, so Iron's
 * {@code irons_spellbooks:spells} registry is still empty at that moment: a spell read from it at
 * runtime could never get an item, and a registry once frozen accepts none. Common code; it reads
 * Iron's registry only by name through Forge's registry manager, never through an Iron's class.
 */
public final class RegistrationOrderProbe {
    public static final ResourceLocation IRONS_SPELLS = new ResourceLocation("irons_spellbooks", "spells");
    private static final List<String> ORDER = Collections.synchronizedList(new ArrayList<>());
    private static volatile int ironsSpellsAtItemRegistration = -1;
    private static volatile int glyphPartsAtItemRegistration = -1;

    private RegistrationOrderProbe() {}

    /** Registered on the mod bus at the lowest priority, after every other listener of the same registry. */
    public static void onRegister(RegisterEvent event) {
        ResourceLocation key = event.getRegistryKey().location();
        ORDER.add(key.toString());
        if (event.getRegistryKey().equals(Registries.ITEM)) {
            glyphPartsAtItemRegistration = com.hollingsworth.arsnouveau.api.registry.GlyphRegistry.getSpellpartMap().size();
            ForgeRegistry<?> registry = RegistryManager.ACTIVE.getRegistry(IRONS_SPELLS);
            ironsSpellsAtItemRegistration = registry == null ? -1 : registry.getKeys().size();
        }
    }

    /** Registry ids in the order their RegisterEvent fired. */
    public static List<String> order() {
        synchronized (ORDER) {
            return List.copyOf(ORDER);
        }
    }

    /** How many Iron's spells were registered when Ars registered its glyph items; -1 when Iron's has no registry. */
    public static int ironsSpellsAtItemRegistration() {
        return ironsSpellsAtItemRegistration;
    }

    public static int glyphPartsAtItemRegistration() {
        return glyphPartsAtItemRegistration;
    }
}
