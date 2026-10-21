package com.otectus.arsnspells.spell.target;

/** What an Ars cast resolved against: nothing, a block face, or an entity. */
public enum CastTargetKind {
    AIR,
    BLOCK,
    ENTITY;

    /** Bounded wire form: the ordinal, rejected when out of range. */
    public static CastTargetKind fromWire(int ordinal) {
        CastTargetKind[] values = values();
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Unknown target kind " + ordinal);
        return values[ordinal];
    }
}
