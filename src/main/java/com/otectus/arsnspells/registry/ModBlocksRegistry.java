package com.otectus.arsnspells.registry;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.block.SpellLoomBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Blocks (and their block items) registered by Ars 'n Spells. The mod historically
 * shipped no blocks of its own; the Spell Loom workstation is the first.
 */
public final class ModBlocksRegistry {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, ArsNSpells.MODID);
    public static final DeferredRegister<Item> BLOCK_ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, ArsNSpells.MODID);

    public static final RegistryObject<Block> SPELL_LOOM = BLOCKS.register("spell_loom",
        () -> new SpellLoomBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE)
            .strength(2.5f)
            .sound(SoundType.WOOD)));

    public static final RegistryObject<Item> SPELL_LOOM_ITEM = BLOCK_ITEMS.register("spell_loom",
        () -> new BlockItem(SPELL_LOOM.get(), new Item.Properties()));

    private ModBlocksRegistry() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ITEMS.register(modBus);
        modBus.addListener(ModBlocksRegistry::addToCreativeTab);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(SPELL_LOOM_ITEM);
        }
    }
}
