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

    private CompatIds() {}
}
