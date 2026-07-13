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

    /**
     * Cosmetic marker stamped on carrier items built by the export pipeline
     * (successor of the 1.20.1 root NBT key {@code arsnspells:export_mode}).
     * Value {@link ArsSpellExportUtil#EXPORT_MODE_SCROLL_CARRIER} marks an
     * Iron's scroll carrying a single Ars entry awaiting spellbook binding.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> EXPORT_MODE =
        COMPONENTS.registerComponentType("export_mode", b -> b
            .persistent(com.mojang.serialization.Codec.STRING)
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
        );

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    private ModDataComponents() {}
}
