package com.otectus.arsnspells.config;

/**
 * How a spell that resolves to several canonical schools picks the Iron's Spellbooks elemental
 * spell-power attribute it scales with.
 *
 * <p>An Ars spell is not limited to one school: addon glyphs declare dual and compound elements,
 * and a recipe can chain effects from different schools. Scaling therefore has to choose between
 * several of the caster's elemental power attributes, and the choice has to be deterministic and
 * stated rather than left to whichever school happened to sort first.
 *
 * <p><b>There is deliberately no {@code SUM} mode.</b> Adding every matching elemental bonus
 * together would make a spell stronger purely for carrying more school labels — an all-element
 * glyph would collect the caster's fire, ice, lightning and nature power at once, which is a
 * balance change disguised as compatibility. Aggregation is bounded by construction here: every
 * policy returns a value drawn from the matched set, never a total of it.
 */
public enum MultiSchoolPowerPolicy {
    /**
     * Scale only with the primary (first-resolved) school — the same school affinity and
     * progression credit. The most conservative option: multi-school spells behave exactly as
     * they did before multi-school resolution existed. If the primary school has no Iron's
     * counterpart, no elemental bonus applies at all.
     */
    PRIMARY("primary", "Only the primary (first-resolved) school scales the spell"),

    /**
     * Scale with the single largest matching elemental attribute (DEFAULT). A dual-element
     * spell rewards the caster's better element without stacking both.
     */
    MAX("max", "The single strongest matching elemental attribute scales the spell"),

    /**
     * Scale with the arithmetic mean of the matching elemental attributes. Rewards broad
     * investment rather than one specialisation, and penalises a spell whose extra schools the
     * caster has no gear for.
     */
    AVERAGE("average", "The mean of all matching elemental attributes scales the spell");

    private final String configName;
    private final String description;

    MultiSchoolPowerPolicy(String configName, String description) {
        this.configName = configName;
        this.description = description;
    }

    public String getConfigName() {
        return configName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get the policy from a config string value, case-insensitively.
     *
     * <p>Falls back to {@link #MAX} for null, empty, misspelled, or removed values (notably
     * {@code "sum"}, which never existed and must not resolve to anything additive).
     */
    public static MultiSchoolPowerPolicy fromString(String value) {
        for (MultiSchoolPowerPolicy policy : values()) {
            if (policy.configName.equalsIgnoreCase(value)) {
                return policy;
            }
        }
        return MAX; // Default fallback
    }
}
