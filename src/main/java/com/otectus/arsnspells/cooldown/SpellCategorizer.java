package com.otectus.arsnspells.cooldown;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.otectus.arsnspells.util.SpellAnalysis;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * @deprecated Use {@link SpellAnalysis#analyze} and {@link SpellAnalysis.Result#category()} instead.
 */
@Deprecated
public class SpellCategorizer {
    /**
     * @deprecated Use {@code SpellAnalysis.analyze(spell).category()} instead.
     */
    @Deprecated
    public static CooldownCategory categorizeArsGlyph(AbstractSpellPart glyph) {
        return SpellAnalysis.deriveCategory(glyph);
    }

    public static CooldownCategory categorizeIronsSpell(ResourceLocation schoolId) {
        if (schoolId == null) return CooldownCategory.UTILITY;
        String path = schoolId.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("holy") || path.contains("ice")) return CooldownCategory.DEFENSIVE;
        if (path.contains("ender") || path.contains("blink")) return CooldownCategory.MOVEMENT;
        return CooldownCategory.OFFENSIVE;
    }
}
