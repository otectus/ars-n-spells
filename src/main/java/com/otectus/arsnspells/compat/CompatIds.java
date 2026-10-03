package com.otectus.arsnspells.compat;

/**
 * String constants for every mod id Ars 'n Spells probes for at runtime. Kept in
 * one place so presence checks, config gates, and datapack {@code mod_loaded}
 * conditions all reference the same canonical id.
 *
 * <p>Only ids the mod actually integrates with today are listed; add a constant
 * when a new {@code compat/<modid>} integration lands.
 *
 * @since 2.5.0
 */
public final class CompatIds {
    public static final String ARS_NOUVEAU = "ars_nouveau";
    public static final String IRONS_SPELLBOOKS = "irons_spellbooks";
    public static final String CURIOS = "curios";
    /**
     * Ars Nouveau addons ANS is verified against. Never compiled against: their glyphs reach
     * ANS through Ars' own {@code GlyphRegistry}, and the only ANS-side knowledge of them is
     * datapack data plus the opt-in GameTest profiles ({@code -PwithArsElemental},
     * {@code -PwithArsZero}, {@code -PwithArsElemancy}).
     */
    public static final String ARS_ELEMENTAL = "ars_elemental";
    public static final String ARS_ZERO = "ars_zero";
    /**
     * Ars Elemancy is equipment-only - armor, bangles and foci - and registers no glyphs of its
     * own, so ANS knows it purely as an id to probe for. It has no Minecraft 1.20.1 release, so
     * nothing on this build probes for it; the constant keeps the two builds' id tables equal.
     */
    public static final String ARS_ELEMANCY = "ars_elemancy";

    private CompatIds() {}
}
