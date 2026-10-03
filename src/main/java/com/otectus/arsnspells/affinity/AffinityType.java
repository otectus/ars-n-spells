package com.otectus.arsnspells.affinity;

/**
 * Spell-school affinity dimensions tracked per player by {@link com.otectus.arsnspells.data.AffinityData}.
 *
 * <p>The five "Iron's-only" elements (HOLY, ENDER, BLOOD, EVOCATION, ELDRITCH)
 * are added in 1.9.0 so that the Iron's-side affinity hook can map every Iron's
 * school onto an enum entry. Existing player NBT only writes keys for enum
 * values it has touched, so adding values here is forward and backward
 * compatible — pre-1.9.0 saves load cleanly with the new values defaulting to 0.
 */
public enum AffinityType {
    // Elemental shared between Ars and Iron's
    FIRE, ICE, LIGHTNING, NATURE,
    // Iron's-only elementals
    HOLY, ENDER, BLOOD, EVOCATION, ELDRITCH,
    // Tactical (Ars-side)
    OFFENSIVE, DEFENSIVE, UTILITY, MOVEMENT,
    // Source (Ars-side)
    ARCANE, PRIMAL, HYBRID;

    /** Canonical id -> display name for the schools we ship names for (same table as NeoForge). */
    private static final java.util.Map<String, String> ID_TO_DISPLAY = java.util.Map.ofEntries(
        java.util.Map.entry("irons_spellbooks:fire", "Fire"),
        java.util.Map.entry("irons_spellbooks:ice", "Ice"),
        java.util.Map.entry("irons_spellbooks:lightning", "Lightning"),
        java.util.Map.entry("irons_spellbooks:nature", "Nature"),
        java.util.Map.entry("irons_spellbooks:holy", "Holy"),
        java.util.Map.entry("irons_spellbooks:ender", "Ender"),
        java.util.Map.entry("irons_spellbooks:blood", "Blood"),
        java.util.Map.entry("irons_spellbooks:evocation", "Evocation"),
        java.util.Map.entry("irons_spellbooks:eldritch", "Eldritch"),
        java.util.Map.entry("ars_n_spells:aqua", "Aqua"),
        java.util.Map.entry("ars_n_spells:geo", "Geo"),
        java.util.Map.entry("ars_n_spells:wind", "Wind")
    );

    /**
     * Human-readable label for a canonical school id. Known schools get a fixed name;
     * everything else falls back to a capitalised registry path
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
        return Character.toUpperCase(path.charAt(0)) + path.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}
