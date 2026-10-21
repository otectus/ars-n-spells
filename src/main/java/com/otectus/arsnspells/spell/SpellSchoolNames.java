package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.util.SchoolKeys;
import com.otectus.arsnspells.util.SpellAnalysis;
import com.otectus.arsnspells.util.SpellSchoolId;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Player-facing wording for mechanical schools, shared by the Spell Loom, tooltips and commands
 * so they all describe a selection the same way. Every result is a translatable component, so it
 * reads correctly on whichever side finally renders it. No client-only classes.
 */
public final class SpellSchoolNames {

    private SpellSchoolNames() {}

    /** The display name of one school, or "Automatic" for an empty key. */
    public static MutableComponent name(@Nullable String key) {
        if (key == null || key.isEmpty()) {
            return Component.translatable("ars_n_spells.school.automatic");
        }
        if (IronsCompat.isLoaded()) {
            MutableComponent registered = com.otectus.arsnspells.compat.IronsSchoolAttributes.displayName(key);
            if (registered != null) {
                return registered;
            }
        }
        SpellSchoolId builtin = SchoolKeys.builtin(key);
        if (!builtin.isGeneric()) {
            return Component.translatable("icon.ars_n_spells.school." + builtin.id());
        }
        return Component.literal(key);
    }

    /** The schools the glyphs resolve to, comma-separated, or "no school". */
    public static MutableComponent glyphSchools(SpellAnalysis.Result analysis) {
        List<String> keys = analysis == null ? List.of() : analysis.glyphProfile().allSchoolKeys();
        if (keys.isEmpty()) {
            return Component.translatable("ars_n_spells.school.none");
        }
        MutableComponent joined = Component.empty();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                joined.append(", ");
            }
            joined.append(name(keys.get(i)));
        }
        return joined;
    }

    /**
     * What an entry storing {@code stored} casts as, in one phrase: Automatic with its glyph
     * schools, the chosen school, or a chosen school that is not applied here and why.
     */
    public static MutableComponent describe(@Nullable String stored, SpellAnalysis.Result glyphs) {
        String key = SpellSchoolChoice.normalize(stored);
        if (key.isEmpty()) {
            return Component.translatable("ars_n_spells.school.describe.automatic", glyphSchools(glyphs));
        }
        return switch (SpellSchoolChoice.check(key)) {
            case ACCEPTED, AUTOMATIC -> Component.translatable("ars_n_spells.school.describe.explicit", name(key));
            case DISABLED -> Component.translatable("ars_n_spells.school.describe.disabled",
                name(key), glyphSchools(glyphs));
            case MALFORMED, NOT_INSTALLED -> Component.translatable("ars_n_spells.school.describe.unavailable",
                name(key), glyphSchools(glyphs));
        };
    }
}
