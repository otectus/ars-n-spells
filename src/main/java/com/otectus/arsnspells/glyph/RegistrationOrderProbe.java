package com.otectus.arsnspells.glyph;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Records the order in which registries are filled at startup, so a GameTest can prove why the
 * imported-glyph identities come from an early catalog and not from Iron's live spell registry.
 *
 * <p>Ars registers a glyph item for every spell part when the item registry is filled. NeoForge
 * fills the vanilla registries first and modded ones afterwards, so Iron's
 * {@code irons_spellbooks:spells} registry is still empty at that moment: a spell read from it at
 * runtime could never get an item, and a frozen registry accepts none. Common code; Iron's registry
 * is looked up by name, never through an Iron's class.
 */
public final class RegistrationOrderProbe {
    public static final ResourceLocation IRONS_SPELLS = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spells");
    private static final List<String> ORDER = Collections.synchronizedList(new ArrayList<>());
    private static volatile int ironsSpellsAtItemRegistration = -1;
    private static volatile int glyphPartsAtItemRegistration = -1;

    private RegistrationOrderProbe() {
    }

    public static void onRegister(RegisterEvent event) {
        ORDER.add(event.getRegistryKey().location().toString());
        if (event.getRegistryKey().equals(Registries.ITEM)) {
            glyphPartsAtItemRegistration = com.hollingsworth.arsnouveau.api.registry.GlyphRegistry.getSpellpartMap().size();
            Registry<?> spells = BuiltInRegistries.REGISTRY.get(IRONS_SPELLS);
            ironsSpellsAtItemRegistration = spells == null ? -1 : spells.size();
        }
    }

    public static List<String> order() {
        synchronized (ORDER) {
            return List.copyOf(ORDER);
        }
    }

    public static int ironsSpellsAtItemRegistration() {
        return ironsSpellsAtItemRegistration;
    }

    public static int glyphPartsAtItemRegistration() {
        return glyphPartsAtItemRegistration;
    }
}
