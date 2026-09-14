package com.otectus.arsnspells.util;

import java.util.Locale;
import java.util.Set;

/** Stable school identity and conservative migration of historical short built-in keys. */
public final class SchoolKeys {
    public static final String GENERIC = "ars_n_spells:generic";
    private static final Set<String> BUILTINS = Set.of("fire", "ice", "lightning", "nature",
        "holy", "ender", "blood", "evocation", "eldritch");

    private SchoolKeys() {}

    /** Unknown short names stay unresolved and recoverable; they are never assigned to Iron's. */
    public static String normalize(String raw) {
        if (raw == null) return GENERIC;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (BUILTINS.contains(key)) return "irons_spellbooks:" + key;
        return "generic".equals(key) || key.isEmpty() ? GENERIC : key;
    }

    public static boolean isNamespaced(String raw) {
        return raw != null && raw.length() <= 256 && raw.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
    }

    /** Legacy built-in projection only. A custom namespace with the same path is still custom. */
    public static SpellSchoolId builtin(String raw) {
        String key = normalize(raw);
        return key.startsWith("irons_spellbooks:")
            ? SpellSchoolId.fromId(key.substring("irons_spellbooks:".length())) : SpellSchoolId.GENERIC;
    }
}
