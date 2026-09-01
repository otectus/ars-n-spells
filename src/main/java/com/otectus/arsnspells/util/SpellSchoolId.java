package com.otectus.arsnspells.util;

import org.jetbrains.annotations.Nullable;
import java.util.Locale;

/**
 * The canonical spell-school vocabulary shared by every ANS subsystem: affinity, progression,
 * scaling, cooldowns, LP cost, and UI.
 *
 * <p><b>Why an enum.</b> School used to be a bare {@code String} produced by a substring
 * heuristic, which let subsystems disagree in ways nothing could catch. The heuristic could
 * return {@code "aqua"}, {@code "geo"} or {@code "wind"} — values with no
 * {@link com.otectus.arsnspells.affinity.AffinityType} constant and no entry in
 * {@code SpellScalingUtil}'s attribute map — so a Conjure Water spell silently received no
 * affinity bonus and no elemental scaling, with no error anywhere. Restricting the vocabulary
 * to a closed set makes that class of bug unrepresentable: every value here maps 1:1 onto both
 * an {@code AffinityType} and an Iron's spell-power attribute, except {@link #GENERIC}, which
 * explicitly means "no school".
 *
 * <p><b>Why these values.</b> They mirror Iron's Spellbooks' schools, because that is what the
 * downstream consumers are keyed on — Iron's {@code AttributeRegistry} elemental powers and
 * ANS's own {@code AffinityType}. Ars Nouveau's own school vocabulary
 * ({@code abjuration/conjuration/manipulation/air/earth/fire/water/elemental}) is a different
 * axis and is translated onto this one by {@link SchoolResolver}.
 *
 * <p>Declaration order is the deterministic tie-break for glyphs that declare several schools;
 * see {@link SchoolResolver}.
 */
public enum SpellSchoolId {
    FIRE,
    ICE,
    LIGHTNING,
    NATURE,
    HOLY,
    ENDER,
    BLOOD,
    EVOCATION,
    ELDRITCH,
    /** No resolvable school. Carries no affinity and no elemental scaling. */
    GENERIC;

    private final String id = name().toLowerCase(Locale.ROOT);

    /** Lowercase string form, the value legacy call sites and NBT use. */
    public String id() {
        return id;
    }

    public boolean isGeneric() {
        return this == GENERIC;
    }

    /**
     * Parse a lowercase school id, returning {@link #GENERIC} for null, unknown, or legacy
     * orphan values ({@code aqua}, {@code geo}, {@code wind}) rather than throwing.
     */
    public static SpellSchoolId fromId(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return GENERIC;
        }
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GENERIC;
        }
    }
}
