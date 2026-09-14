package com.otectus.arsnspells.registry;

import com.mojang.serialization.MapCodec;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.loot.BlankScrollLootModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;

/**
 * Global loot modifier serializers. Registration is unconditional; the
 * Iron's-presence gate lives inside {@link BlankScrollLootModifier#doApply}.
 */
public final class ModLootModifiersRegistry {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ArsNSpells.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<BlankScrollLootModifier>> BLANK_SCROLL =
        LOOT_MODIFIERS.register("blank_scroll", () -> BlankScrollLootModifier.CODEC);

    private ModLootModifiersRegistry() {}

    public static void register(IEventBus modBus) {
        LOOT_MODIFIERS.register(modBus);
    }
}
