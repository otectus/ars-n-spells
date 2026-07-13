package com.otectus.arsnspells.registry;

import com.hollingsworth.arsnouveau.common.items.RitualTablet;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.rituals.SpellTranscriptionRitual;
import com.otectus.arsnspells.rituals.SpellUninscriptionRitual;
import com.otectus.arsnspells.rituals.SpellbookBindingRitual;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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
 * The transcription tablet is only registered when Iron's Spellbooks is
 * loaded; the uninscription tablet is always registered so players can
 * clean up legacy inscribed items after removing Iron's.
 */
public final class ModItemsRegistry {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(BuiltInRegistries.ITEM, ArsNSpells.MODID);

    private static DeferredHolder<Item, RitualTablet> spellTranscriptionTablet;
    private static DeferredHolder<Item, RitualTablet> spellUninscriptionTablet;
    private static DeferredHolder<Item, RitualTablet> spellbookBindingTablet;

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
        spellbookBindingTablet = ITEMS.register(
            SpellbookBindingRitual.REGISTRY_PATH,
            () -> new RitualTablet(new SpellbookBindingRitual())
        );
    }

    /**
     * Registers items that work even without Iron's Spellbooks loaded.
     * Must be called from the mod constructor.
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

    public static DeferredHolder<Item, RitualTablet> spellTranscriptionTablet() {
        return spellTranscriptionTablet;
    }

    public static DeferredHolder<Item, RitualTablet> spellUninscriptionTablet() {
        return spellUninscriptionTablet;
    }

    public static DeferredHolder<Item, RitualTablet> spellbookBindingTablet() {
        return spellbookBindingTablet;
    }
}
