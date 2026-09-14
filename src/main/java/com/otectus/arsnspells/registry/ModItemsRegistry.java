package com.otectus.arsnspells.registry;

import com.hollingsworth.arsnouveau.common.items.RitualTablet;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.rituals.ManaInfusionRitual;
import com.otectus.arsnspells.rituals.ManaWellRitual;
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
    private static DeferredHolder<Item, RitualTablet> manaInfusionTablet;
    private static DeferredHolder<Item, RitualTablet> manaWellTablet;
    private static DeferredHolder<Item, Item> blankScroll;

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
        // 3.2.0: Mana Infusion and the Mana Well had been registered as rituals since the
        // 1.x line with no tablet item at all, so a brazier could never resolve them and
        // they were unobtainable in survival. Both are Iron's-gated only because their
        // Enchanting Apparatus recipes call for irons_spellbooks:arcane_essence.
        manaInfusionTablet = ITEMS.register(
            ManaInfusionRitual.REGISTRY_PATH,
            () -> new RitualTablet(new ManaInfusionRitual())
        );
        manaWellTablet = ITEMS.register(
            ManaWellRitual.REGISTRY_PATH,
            () -> new RitualTablet(new ManaWellRitual())
        );
    }

    /**
     * Registers items that work even without Iron's Spellbooks loaded. The
     * uninscribe tablet is here so players can clean up legacy inscribed
     * items after removing Iron's. The blank scroll is here because stock
     * Iron's ships no recipe for a bare {@code irons_spellbooks:scroll}, so
     * the Spell Loom needs a blank of our own to accept as a target; it is a
     * plain item with no behaviour and is inert without Iron's. Must be
     * called from the mod constructor.
     */
    public static void registerCommonItems() {
        if (spellUninscriptionTablet != null) {
            return;
        }
        spellUninscriptionTablet = ITEMS.register(
            SpellUninscriptionRitual.REGISTRY_PATH,
            () -> new RitualTablet(new SpellUninscriptionRitual())
        );
        blankScroll = ITEMS.register("blank_scroll", () -> new Item(new Item.Properties()));
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

    public static DeferredHolder<Item, RitualTablet> manaInfusionTablet() {
        return manaInfusionTablet;
    }

    public static DeferredHolder<Item, RitualTablet> manaWellTablet() {
        return manaWellTablet;
    }

    public static DeferredHolder<Item, Item> blankScroll() {
        return blankScroll;
    }
}
