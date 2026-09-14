package com.otectus.arsnspells.registry;

import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * The mod's own creative tab. Until 3.2.0 Ars 'n' Spells shipped no tab and its items were
 * scattered through other mods' menus: the Spell Loom was pushed into vanilla
 * {@code FUNCTIONAL_BLOCKS}, and the ritual tablets surfaced inside Ars Nouveau's tab.
 *
 * <p>The tablets landed there as a side effect of something the mod genuinely needs. Ars's tab
 * generator enumerates {@code RitualRegistry.getRitualItemMap()}, and
 * {@link com.otectus.arsnspells.rituals.RitualRegistryHandler} must splice ANS tablets into that
 * same map so braziers and JEI can resolve a tablet back to its ritual. The splice stays; this
 * class removes only the display consequence.
 *
 * <p><b>Why the removal is scoped to the {@code ars_nouveau} namespace</b> rather than sweeping
 * every tab: a modpack that deliberately rehomes ANS items into a custom tab should keep them.
 *
 * <p><b>Search-tab safety.</b> On 1.20.1 this was a real hazard — Forge's event exposed one
 * entry set, and removing from it while the vanilla Search tab was building would have made the
 * items unfindable. NeoForge 21.1 splits the event into parent and search entry sets, so the
 * removal below asks for {@code PARENT_TAB_ONLY} and the search index is untouched by
 * construction rather than by careful scoping.
 *
 * <p>Ars Nouveau is a mandatory dependency ordered {@code AFTER} in {@code neoforge.mods.toml},
 * so its tabs always exist by the time the listener runs. Keying on the namespace rather than a
 * literal {@code ars_nouveau:general} means the removal survives Ars renaming or adding tabs; if
 * Ars ever stops enumerating the ritual map, the listener quietly becomes a no-op.
 */
public final class ModCreativeTabs {
    /** Namespace of the mod whose tab enumerates our ritual tablets. */
    private static final String ARS_NOUVEAU = "ars_nouveau";

    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArsNSpells.MODID);

    /**
     * The single Ars 'n' Spells tab. Named {@code general} to match Ars Nouveau's own tab path.
     *
     * <p>The icon is the Spell Loom block item because it is registered unconditionally — the
     * Iron's-gated tablets are absent entirely on an install without Iron's Spellbooks, so none
     * of them can serve as an icon that never resolves to anything.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("general", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + ArsNSpells.MODID))
            .icon(() -> new ItemStack(ModBlocksRegistry.SPELL_LOOM_ITEM.get()))
            // "before" means those tabs sort ahead of this one: park us after all vanilla tabs,
            // which is also what Ars Nouveau does with its own two.
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .displayItems(ModCreativeTabs::buildContents)
            .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
        // LOWEST so anything another ANS listener adds to an Ars tab is already there to remove,
        // matching the convention on ArsCrossProxyRegistry's sweep. Note that EventPriority only
        // orders listeners within this mod's bus: ModLoader dispatches the event to each mod's
        // own bus in mod-load order, so a third-party tab editor loading after ANS could still
        // re-add an entry at NORMAL priority after this removal. That is a loader-level limit —
        // escalating the priority here would not fix it.
        modBus.addListener(EventPriority.LOWEST, ModCreativeTabs::removeFromArsNouveauTabs);
    }

    /**
     * Everything the mod registers, block items first so the Spell Loom workstation leads.
     *
     * <p>Iterating the {@link DeferredRegister}s rather than naming the seven items (the Spell
     * Loom block item, the four Iron's-gated tablets, the uninscribe tablet, and the blank
     * scroll) makes the Iron's gate structural instead of defensive. Without Iron's,
     * {@code ModItemsRegistry.registerIronsDependentItems()} is never called, so those entries do
     * not exist to iterate — there is nothing to null-check. Naming them instead would mean
     * calling accessors that return a <em>null</em> holder on an Iron's-less install. Any future
     * ANS item is picked up here for free.
     *
     * <p>{@code DeferredRegister} backs its entries with a {@code LinkedHashMap}, so the tab
     * order is registration order, fixed by the {@code ArsNSpells} constructor.
     *
     * <p>The default {@code TabVisibility} of {@link CreativeModeTab.Output#accept(ItemLike)} is
     * {@code PARENT_AND_SEARCH_TABS} and must stay that way: it is what keeps these items in the
     * creative Search tab after {@link #removeFromArsNouveauTabs} pulls them out of Ars's.
     */
    private static void buildContents(CreativeModeTab.ItemDisplayParameters params,
                                      CreativeModeTab.Output out) {
        for (DeferredHolder<Item, ? extends Item> entry : ModBlocksRegistry.BLOCK_ITEMS.getEntries()) {
            accept(out, entry);
        }
        for (DeferredHolder<Item, ? extends Item> entry : ModItemsRegistry.ITEMS.getEntries()) {
            accept(out, entry);
        }
    }

    private static void accept(CreativeModeTab.Output out, DeferredHolder<Item, ? extends Item> ref) {
        if (ref == null || !ref.isBound()) {
            return;
        }
        out.accept(ref.get());
    }

    /** Strip {@code ars_n_spells} items out of Ars Nouveau's tabs — see the class javadoc. */
    private static void removeFromArsNouveauTabs(BuildCreativeModeTabContentsEvent event) {
        if (!ARS_NOUVEAU.equals(event.getTabKey().location().getNamespace())) {
            return;
        }
        // Collected before removal: getParentEntries() is an unmodifiable view over the live
        // backing set, so removing during iteration would still be a concurrent modification.
        List<ItemStack> ours = new ArrayList<>();
        for (ItemStack stack : event.getParentEntries()) {
            if (isOurs(stack)) {
                ours.add(stack);
            }
        }
        for (ItemStack stack : ours) {
            // PARENT_TAB_ONLY: drop it from Ars's visible tab, leave the search index alone.
            event.remove(stack, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        }
    }

    /** True when the stack's item is registered under this mod's namespace. */
    public static boolean isOurs(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && ArsNSpells.MODID.equals(id.getNamespace());
    }
}
