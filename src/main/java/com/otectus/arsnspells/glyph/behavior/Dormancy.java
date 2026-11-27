package com.otectus.arsnspells.glyph.behavior;

/**
 * Why an imported glyph cannot be used right now, or {@link #AVAILABLE}.
 *
 * <p>A dormant glyph keeps its registered id, stays in every spellbook and known-glyph list that
 * holds it, and becomes usable again as soon as the reason goes away. Ordinals are written to the
 * client sync payload, so constants are only ever appended.
 */
public enum Dormancy {
    AVAILABLE,
    /** {@code enable_iron_glyphs} is off on this server. */
    FEATURE_DISABLED,
    /** Iron's Spells 'n Spellbooks is not installed. */
    IRONS_ABSENT,
    /** The spell is not in the loaded Iron's registry. */
    SPELL_NOT_REGISTERED,
    /** Iron's own spell config disables the spell. */
    SPELL_DISABLED_BY_IRONS,
    /** A data pack mapping disables the spell or this level. */
    DISABLED_BY_PACK,
    /** The server's Iron's config caps the spell below this glyph's level. */
    LEVEL_ABOVE_SERVER_MAX,
    /** The catalog marks the spell unsupported. */
    UNSUPPORTED,
    /** The client has not received the server's glyph settings yet. */
    NOT_SYNCED,
    /** 3.4.1: the catalog retired the spell; its identities stay registered and dormant. */
    RETIRED,
    /** 3.4.1 review CATALOG-02: the level is below the spell's live minimum (a minimum that rose after it was registered). */
    LEVEL_BELOW_SERVER_MIN,
    /** 3.4.1 review CATALOG-02: the catalog names an adapter contract version newer than this build implements. */
    ADAPTER_TOO_NEW,
    /** 3.4.1 review SAVE-01: only ANS's retained copy keeps this identity registered; its catalog package is not installed. */
    PACKAGE_MISSING;

    public boolean available() {
        return this == AVAILABLE;
    }

    public String translationKey() {
        return "ars_n_spells.glyph.dormant." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Dormancy byOrdinal(int ordinal) {
        Dormancy[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NOT_SYNCED;
    }
}
