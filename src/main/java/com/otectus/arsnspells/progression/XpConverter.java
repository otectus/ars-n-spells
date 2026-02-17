package com.otectus.arsnspells.progression;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;

import java.util.Locale;

public class XpConverter {
    public static String mapGlyphToSchool(AbstractSpellPart glyph) {
        if (glyph == null || glyph.getRegistryName() == null) {
            return "NONE";
        }
        String path = glyph.getRegistryName().getPath().toLowerCase(Locale.ROOT);
        // Exhaustive Keyword scan to ensure no spell is left behind
        if (path.contains("fire") || path.contains("flare") || path.contains("plasma")) return "irons_spellbooks:fire";
        if (path.contains("ice") || path.contains("freeze") || path.contains("frost")) return "irons_spellbooks:ice";
        if (path.contains("lightning") || path.contains("shock") || path.contains("storm")) return "irons_spellbooks:lightning";
        if (path.contains("holy") || path.contains("heal") || path.contains("life")) return "irons_spellbooks:holy";
        if (path.contains("ender") || path.contains("blink") || path.contains("rift")) return "irons_spellbooks:ender";
        if (path.contains("blood") || path.contains("drain") || path.contains("vampire")) return "irons_spellbooks:blood";
        if (path.contains("evocation") || path.contains("fang") || path.contains("proj")) return "irons_spellbooks:evocation";
        if (path.contains("nature") || path.contains("earth") || path.contains("grow")) return "irons_spellbooks:nature";
        if (path.contains("eldritch") || path.contains("dark") || path.contains("void")) return "irons_spellbooks:eldritch";
        return "NONE";
    }
}
