package com.otectus.arsnspells.affinity;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;

/**
 * Canonical affinity-key translation shared by the Ars-side and Iron's-side
 * affinity handlers and by the spell-scaling damage-bonus site, so all three
 * agree on exactly one {@code String} key per school.
 *
 * <p><b>Deliberately free of any Iron's Spellbooks import</b> so it is safe to
 * load on an Ars-only (Iron's-absent) install: the Ars-side {@code AffinityHandler}
 * and the client {@code AffinitySyncPayload} both call into it without a
 * mod-presence gate.
 *
 * <p>Keys are full {@code "namespace:path"} school ids. The nine elemental
 * schools shared with Iron's resolve to the stock {@code irons_spellbooks:*}
 * ids, so an Ars fireball and an Iron's fireball accrue the same affinity track.
 * The three Ars-only heuristic words with no Iron's stock school
 * ({@code aqua}/{@code geo}/{@code wind}) resolve to {@code ars_n_spells:*} so
 * they are tracked rather than silently dropped (the pre-2.5.0 bug where
 * {@code AffinityType.valueOf("AQUA")} threw and the cast was lost).
 *
 * @since 2.5.0
 */
public final class SchoolKeys {
    public static final String IRONS_NS = "irons_spellbooks";
    public static final String ANS_NS = "ars_n_spells";

    /**
     * Maps the heuristic vocabulary emitted by
     * {@link com.otectus.arsnspells.util.SpellAnalysis#deriveSchool} to a
     * canonical full school id. The nine Iron's-stock elements point at
     * {@code irons_spellbooks:*}; the three Ars-only words point at
     * {@code ars_n_spells:*}. {@code "generic"} is intentionally absent (callers
     * skip it), so {@link #fromArsSchool} returns {@code null} for it.
     */
    private static final Map<String, String> ARS_WORD_TO_ID = Map.ofEntries(
        Map.entry("fire", IRONS_NS + ":fire"),
        Map.entry("ice", IRONS_NS + ":ice"),
        Map.entry("lightning", IRONS_NS + ":lightning"),
        Map.entry("holy", IRONS_NS + ":holy"),
        Map.entry("ender", IRONS_NS + ":ender"),
        Map.entry("blood", IRONS_NS + ":blood"),
        Map.entry("evocation", IRONS_NS + ":evocation"),
        Map.entry("nature", IRONS_NS + ":nature"),
        Map.entry("eldritch", IRONS_NS + ":eldritch"),
        Map.entry("aqua", ANS_NS + ":aqua"),
        Map.entry("geo", ANS_NS + ":geo"),
        Map.entry("wind", ANS_NS + ":wind")
    );

    private SchoolKeys() {}

    /** Canonical key for an Iron's school id (e.g. {@code irons_spellbooks:fire}). */
    public static String fromResourceLocation(ResourceLocation schoolId) {
        return schoolId == null ? null : schoolId.toString();
    }

    /**
     * Canonical key for an Ars heuristic school word (the output of
     * {@link com.otectus.arsnspells.util.SpellAnalysis#deriveSchool}). Returns
     * {@code null} for {@code "generic"}, {@code null}, or any unrecognised word
     * so callers skip them — matching the existing {@code !"generic".equals(...)}
     * guards at the two call sites.
     */
    public static String fromArsSchool(String word) {
        if (word == null) {
            return null;
        }
        return ARS_WORD_TO_ID.get(word.toLowerCase(Locale.ROOT));
    }
}
