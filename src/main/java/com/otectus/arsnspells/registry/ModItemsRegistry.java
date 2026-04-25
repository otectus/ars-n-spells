package com.otectus.arsnspells.registry;

import com.hollingsworth.arsnouveau.common.items.RitualTablet;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.rituals.SpellTranscriptionRitual;
import com.otectus.arsnspells.rituals.SpellUninscriptionRitual;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Item registrar for Ars 'n' Spells. The mod does not ship gameplay items of
 * its own, but it must publish a {@link RitualTablet} for the Spell
 * Transcription ritual so players can acquire it via the datapack recipe.
 *
 * Ars Nouveau creates tablet items in bulk during its own item-registration
 * pass by iterating {@code RitualRegistry.getRitualMap()}. That loop only
 * picks up rituals that are already in the map at that point, which happens
 * during mod construction on Ars's event bus. Ars 'n' Spells registers its
 * ritual later, at common setup, so Ars's loop never sees it. We therefore
 * own the tablet item ourselves and insert it into
 * {@code RitualRegistry.getRitualItemMap()} manually from the ritual setup
 * path.
 *
 * The tablet is only registered when Iron's Spellbooks is loaded, to avoid
 * linking {@link SpellTranscriptionRitual} (and its Iron's imports) on
 * installs that don't have Iron's available.
 */
public final class ModItemsRegistry {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, ArsNSpells.MODID);

    private static RegistryObject<RitualTablet> spellTranscriptionTablet;
    private static RegistryObject<RitualTablet> spellUninscriptionTablet;

    private ModItemsRegistry() {}

    /**
     * Registers items that depend on Iron's Spellbooks being present. Must be
     * called from the mod constructor (before the mod event bus fires
     * registration events) and only when {@code irons_spellbooks} is loaded.
     */
    public static void registerIronsDependentItems() {
        if (spellTranscriptionTablet != null) {
            return;
        }
        spellTranscriptionTablet = ITEMS.register(
            SpellTranscriptionRitual.REGISTRY_PATH,
            () -> new RitualTablet(new SpellTranscriptionRitual())
        );
    }

    /**
     * Registers items that work even without Iron's Spellbooks loaded. The
     * uninscribe tablet is here so players can clean up legacy inscribed
     * items after removing Iron's. Must be called from the mod constructor.
     */
    public static void registerCommonItems() {
        if (spellUninscriptionTablet != null) {
            return;
        }
        spellUninscriptionTablet = ITEMS.register(
            SpellUninscriptionRitual.REGISTRY_PATH,
            () -> new RitualTablet(new SpellUninscriptionRitual())
        );
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    public static RegistryObject<RitualTablet> spellTranscriptionTablet() {
        return spellTranscriptionTablet;
    }

    public static RegistryObject<RitualTablet> spellUninscriptionTablet() {
        return spellUninscriptionTablet;
    }
}
