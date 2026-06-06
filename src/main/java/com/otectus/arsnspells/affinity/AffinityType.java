package com.otectus.arsnspells.affinity;

import java.util.Locale;
import java.util.Map;

/**
 * Legacy/display helper for the affinity system.
 *
 * <p><b>2.5.0:</b> affinity is no longer keyed by this enum. Player affinity is
 * stored in {@link com.otectus.arsnspells.data.AffinityData} as a
 * {@code Map<String,Integer>} keyed by the full school id (see
 * {@link SchoolKeys}), so every Iron's addon school is tracked, not just the 16
 * hardcoded values below. This type now serves only two purposes:
 *
 * <ol>
 *   <li><b>Save migration.</b> {@link #legacyKeyToId(String)} maps the old NBT
 *       enum-name keys ({@code "FIRE"}, …) onto canonical school ids so 2.0.x
 *       saves migrate losslessly. The nine elemental values map onto stock
 *       {@code irons_spellbooks:*} ids; the seven category/source buckets
 *       (OFFENSIVE/DEFENSIVE/UTILITY/MOVEMENT/ARCANE/PRIMAL/HYBRID) were never
 *       written by either affinity handler — {@code deriveSchool} cannot return
 *       them and the Iron's handler only mapped real school paths — so they map
 *       to {@code null} and are dropped on migration.</li>
 *   <li><b>Display.</b> {@link #displayName(String)} gives the nine elementals a
 *       capitalised label for {@code /ans info}; addon schools fall back to a
 *       capitalised path.</li>
 * </ol>
 */
public enum AffinityType {
    // Elemental shared between Ars and Iron's
    FIRE, ICE, LIGHTNING, NATURE,
    // Iron's-only elementals
    HOLY, ENDER, BLOOD, EVOCATION, ELDRITCH,
    // Tactical / source buckets (Ars-side) — legacy only; dropped on migration
    OFFENSIVE, DEFENSIVE, UTILITY, MOVEMENT,
    ARCANE, PRIMAL, HYBRID;

    /** Legacy enum-name (NBT key) -> canonical school id. Buckets are absent (dropped). */
    private static final Map<String, String> LEGACY_TO_ID = Map.ofEntries(
        Map.entry("FIRE", SchoolKeys.IRONS_NS + ":fire"),
        Map.entry("ICE", SchoolKeys.IRONS_NS + ":ice"),
        Map.entry("LIGHTNING", SchoolKeys.IRONS_NS + ":lightning"),
        Map.entry("NATURE", SchoolKeys.IRONS_NS + ":nature"),
        Map.entry("HOLY", SchoolKeys.IRONS_NS + ":holy"),
        Map.entry("ENDER", SchoolKeys.IRONS_NS + ":ender"),
        Map.entry("BLOOD", SchoolKeys.IRONS_NS + ":blood"),
        Map.entry("EVOCATION", SchoolKeys.IRONS_NS + ":evocation"),
        Map.entry("ELDRITCH", SchoolKeys.IRONS_NS + ":eldritch")
    );

    /** Canonical id -> pretty display name for the schools we ship names for. */
    private static final Map<String, String> ID_TO_DISPLAY = Map.ofEntries(
        Map.entry(SchoolKeys.IRONS_NS + ":fire", "Fire"),
        Map.entry(SchoolKeys.IRONS_NS + ":ice", "Ice"),
        Map.entry(SchoolKeys.IRONS_NS + ":lightning", "Lightning"),
        Map.entry(SchoolKeys.IRONS_NS + ":nature", "Nature"),
        Map.entry(SchoolKeys.IRONS_NS + ":holy", "Holy"),
        Map.entry(SchoolKeys.IRONS_NS + ":ender", "Ender"),
        Map.entry(SchoolKeys.IRONS_NS + ":blood", "Blood"),
        Map.entry(SchoolKeys.IRONS_NS + ":evocation", "Evocation"),
        Map.entry(SchoolKeys.IRONS_NS + ":eldritch", "Eldritch"),
        Map.entry(SchoolKeys.ANS_NS + ":aqua", "Aqua"),
        Map.entry(SchoolKeys.ANS_NS + ":geo", "Geo"),
        Map.entry(SchoolKeys.ANS_NS + ":wind", "Wind")
    );

    /**
     * Maps a legacy NBT key (an old {@code AffinityType.name()}) to its canonical
     * school id, or {@code null} if the key was a category/source bucket or is
     * otherwise unrecognised (and should be dropped on migration).
     */
    public static String legacyKeyToId(String legacyName) {
        return legacyName == null ? null : LEGACY_TO_ID.get(legacyName);
    }

    /**
     * Human-readable label for a canonical school id. Known schools get a fixed
     * name; everything else falls back to a capitalised registry path
     * (e.g. {@code cataclysm_spellbooks:abyssal} -> {@code "Abyssal"}).
     */
    public static String displayName(String schoolId) {
        if (schoolId == null || schoolId.isEmpty()) {
            return "Unknown";
        }
        String known = ID_TO_DISPLAY.get(schoolId);
        if (known != null) {
            return known;
        }
        int colon = schoolId.indexOf(':');
        String path = colon >= 0 ? schoolId.substring(colon + 1) : schoolId;
        if (path.isEmpty()) {
            return schoolId;
        }
        return Character.toUpperCase(path.charAt(0)) + path.substring(1).toLowerCase(Locale.ROOT);
    }
}
