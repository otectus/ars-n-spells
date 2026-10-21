package com.otectus.arsnspells.block;

import com.otectus.arsnspells.glyph.ImportedGlyphKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A saved Press job keeps every term it was started with, and an unreadable one is rejected rather than guessed. */
class GlyphPressJobTest {
    private static final GlyphPressJob JOB = new GlyphPressJob(UUID.randomUUID(), UUID.randomUUID(),
        new ImportedGlyphKey(ResourceLocation.parse("irons_spellbooks:firebolt"), 3), 2, 2, 1500, 140,
        "ars_n_spells:glyph_press_catalysts", "a".repeat(64), "b".repeat(64));

    @Test
    void roundTripsExactly() {
        assertEquals(JOB, GlyphPressJob.load(JOB.save()));
    }

    @Test
    void anIncompleteOrImpossibleJobIsNotLoaded() {
        CompoundTag noOperation = JOB.save();
        noOperation.remove("operation");
        assertNull(GlyphPressJob.load(noOperation));
        CompoundTag badSpell = JOB.save();
        badSpell.putString("spell", "Not A Spell");
        assertNull(GlyphPressJob.load(badSpell));
        CompoundTag badLevel = JOB.save();
        badLevel.putInt("level", 0);
        assertNull(GlyphPressJob.load(badLevel));
        CompoundTag badTicks = JOB.save();
        badTicks.putInt("ticks", 0);
        assertNull(GlyphPressJob.load(badTicks));
    }
}
