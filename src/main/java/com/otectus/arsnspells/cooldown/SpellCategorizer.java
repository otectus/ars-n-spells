package com.otectus.arsnspells.cooldown;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public class SpellCategorizer {
    public static CooldownCategory categorizeArsGlyph(AbstractSpellPart glyph) {
        if (glyph == null || glyph.getRegistryName() == null) {
            return CooldownCategory.UTILITY;
        }
        String path = glyph.getRegistryName().getPath().toLowerCase(Locale.ROOT);
        if (path.contains("projectile") || path.contains("flare") || path.contains("dmg")) return CooldownCategory.OFFENSIVE;
        if (path.contains("shield") || path.contains("heal") || path.contains("barrier")) return CooldownCategory.DEFENSIVE;
        if (path.contains("leap") || path.contains("blink") || path.contains("speed")) return CooldownCategory.MOVEMENT;
        return CooldownCategory.UTILITY;
    }

    public static CooldownCategory categorizeIronsSpell(ResourceLocation schoolId) {
        if (schoolId == null) return CooldownCategory.UTILITY;
        String path = schoolId.getPath().toLowerCase(Locale.ROOT);
        // Direct ID-based categorization for binary safety
        if (path.contains("holy") || path.contains("ice")) return CooldownCategory.DEFENSIVE;
        if (path.contains("ender") || path.contains("blink")) return CooldownCategory.MOVEMENT;
        return CooldownCategory.OFFENSIVE;
    }
}
