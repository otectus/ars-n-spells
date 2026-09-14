package com.otectus.arsnspells.registry;

import com.mojang.serialization.Codec;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.loot.BlankScrollLootModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Global loot modifier serializers. Registration is unconditional; the
 * Iron's-presence gate lives inside {@link BlankScrollLootModifier#doApply}.
 */
public final class ModLootModifiersRegistry {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
        DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ArsNSpells.MODID);

    public static final RegistryObject<Codec<BlankScrollLootModifier>> BLANK_SCROLL =
        LOOT_MODIFIERS.register("blank_scroll", () -> BlankScrollLootModifier.CODEC);

    private ModLootModifiersRegistry() {}

    public static void register(IEventBus modBus) {
        LOOT_MODIFIERS.register(modBus);
    }
}
