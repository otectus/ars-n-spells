package com.otectus.arsnspells.registry;

import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 3.4.0: the Glyph Press's engraving sound and completion chime (sounds.json maps them to vanilla audio). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ArsNSpells.MODID);

    public static final RegistryObject<SoundEvent> GLYPH_PRESS_ENGRAVE = SOUNDS.register("block.glyph_press.engrave",
        () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArsNSpells.MODID, "block.glyph_press.engrave")));
    public static final RegistryObject<SoundEvent> GLYPH_PRESS_COMPLETE = SOUNDS.register("block.glyph_press.complete",
        () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ArsNSpells.MODID, "block.glyph_press.complete")));

    private ModSounds() {}

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
