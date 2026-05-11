package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Replaces the ItemStack-NBT cross-cast payload (root tags
 * {@code arsnspells:cross_spells} list + {@code arsnspells:cross_spell_index}
 * int) with a single {@link DataComponentType} carrying a typed
 * {@link CrossModSpellList} record. NeoForge handles persistence + network
 * sync via the Codec/StreamCodec wired into the component builder.
 */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE,
            ArsNSpells.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CrossModSpellList>> CROSS_SPELLS =
        COMPONENTS.registerComponentType("cross_spells", b -> b
            .persistent(CrossModSpellList.CODEC)
            .networkSynchronized(CrossModSpellList.STREAM_CODEC)
        );

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    private ModDataComponents() {}
}
