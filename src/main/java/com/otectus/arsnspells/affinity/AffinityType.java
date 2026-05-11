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
    ARCANE, PRIMAL, HYBRID
}
