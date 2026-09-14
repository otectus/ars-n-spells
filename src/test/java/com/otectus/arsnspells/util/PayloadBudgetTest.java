package com.otectus.arsnspells.util;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PayloadBudgetTest {
    @Test void fortyUnicodeCodePointsAreAllowedButControlsAreNot() {
        assertTrue(PayloadBudget.name("😀".repeat(40)));
        assertFalse(PayloadBudget.name("😀".repeat(41)));
        assertFalse(PayloadBudget.name("Spell\nName"));
    }
    @Test void bothNativeRecipeShapesAcceptLargeLegitimateSpells() {
        CompoundTag forge = new CompoundTag(), parts = new CompoundTag();
        parts.putInt("size", 256);
        for (int i = 0; i < 256; i++) parts.putString("part" + i, "ars_nouveau:glyph_heal");
        forge.put("recipe", parts);
        assertTrue(PayloadBudget.arsSpell(forge));
        CompoundTag neo = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < 256; i++) list.add(StringTag.valueOf("ars_nouveau:glyph_heal"));
        neo.put("recipe", list);
        assertTrue(PayloadBudget.arsSpell(neo));
        parts.putInt("size", Integer.MAX_VALUE);
        assertFalse(PayloadBudget.arsSpell(forge));
        assertEquals(Integer.MAX_VALUE, parts.getInt("size"), "refusal must preserve the saved payload");
    }
    @Test void oversizedArraysRefuseWithoutAllocationOrMutation() {
        CompoundTag data = new CompoundTag();
        data.putByteArray("extension", new byte[65_537]);
        assertFalse(PayloadBudget.tag(data));
        assertEquals(65_537, data.getByteArray("extension").length);
    }
    @Test void excessiveDepthAndMalformedRecipeAreRecoverable() {
        CompoundTag data = new CompoundTag(), next = data;
        for (int i = 0; i < 40; i++) { CompoundTag child = new CompoundTag(); next.put("next", child); next = child; }
        assertFalse(PayloadBudget.tag(data));
        assertTrue(data.contains("next"));
        assertFalse(PayloadBudget.arsSpell(new CompoundTag()));
    }
}
