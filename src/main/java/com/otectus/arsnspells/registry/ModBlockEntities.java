package com.otectus.arsnspells.registry;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.block.SpellLoomBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ArsNSpells.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpellLoomBlockEntity>> SPELL_LOOM =
        BLOCK_ENTITIES.register("spell_loom",
            () -> BlockEntityType.Builder.of(SpellLoomBlockEntity::new,
                ModBlocksRegistry.SPELL_LOOM.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
