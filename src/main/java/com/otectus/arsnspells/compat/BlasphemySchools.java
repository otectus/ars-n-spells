package com.otectus.arsnspells.compat;

import java.util.Set;

/** Exact school identities for Covenant's declared school-tag adapter. */
public final class BlasphemySchools {
    public static final Set<String> SUPPORTED = Set.of("fire", "ice", "lightning", "holy", "ender",
        "blood", "evocation", "nature", "eldritch", "aqua", "geo", "wind");

    private BlasphemySchools() {}

    public static String canonical(String school) {
        if (school == null) return null;
        String value = school.toLowerCase(java.util.Locale.ROOT);
        if (value.startsWith("irons_spellbooks:")) value = value.substring("irons_spellbooks:".length());
        return SUPPORTED.contains(value) ? value : null;
    }
}
