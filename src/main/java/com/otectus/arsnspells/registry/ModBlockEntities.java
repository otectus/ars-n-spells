package com.otectus.arsnspells.registry;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ArsNSpells.MODID);

    public static final RegistryObject<BlockEntityType<SpellLoomBlockEntity>> SPELL_LOOM =
        BLOCK_ENTITIES.register("spell_loom",
            () -> BlockEntityType.Builder.of(SpellLoomBlockEntity::new,
                ModBlocksRegistry.SPELL_LOOM.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
