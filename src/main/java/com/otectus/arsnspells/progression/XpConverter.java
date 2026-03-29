package com.otectus.arsnspells.progression;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.otectus.arsnspells.util.SpellAnalysis;

/**
 * @deprecated Use {@link SpellAnalysis#analyze} and {@link SpellAnalysis.Result#dominantSchool()} instead.
 */
@Deprecated
public class XpConverter {
    /**
     * @deprecated Use {@code SpellAnalysis.analyze(spell).dominantSchool()} instead.
     */
    @Deprecated
    public static String mapGlyphToSchool(AbstractSpellPart glyph) {
        String school = SpellAnalysis.deriveSchool(glyph);
        if ("generic".equals(school)) {
            return "NONE";
        }
        return "irons_spellbooks:" + school;
    }
}
