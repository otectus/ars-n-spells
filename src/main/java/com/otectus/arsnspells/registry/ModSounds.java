package com.otectus.arsnspells.registry;

import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sound events of Ars 'n' Spells. Each is its own event with its own subtitle so resource packs
 * can replace it; the shipped {@code sounds.json} points them at vanilla audio.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ArsNSpells.MODID);

    /** A short scrape while the Glyph Press engraves. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLYPH_PRESS_ENGRAVE = register("block.glyph_press.engrave");
    /** The chime when a glyph is finished. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLYPH_PRESS_COMPLETE = register("block.glyph_press.complete");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ArsNSpells.MODID, path)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
