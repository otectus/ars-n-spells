package com.otectus.arsnspells.contract;

/**
 * What kind of item takes part in an inscription (audit V18).
 *
 * <p>Closes the finding that the inscription table classified an item by asking whether it
 * carried ANS spell data. Absence of ANS data is not the same as blank: a scroll filled by Ars
 * Nouveau or by Iron's Spellbooks has no ANS data either, so it was read as blank parchment and
 * overwritten. The kinds below are derived from the item itself, and a filled scroll is its own
 * kind rather than an absence.
 */
public enum InscriptionSourceKind {
    /** A spellbook. Reusable: inscribing from it never consumes it. */
    REUSABLE_BOOK,
    /** A casting focus. Reusable: inscribing from it never consumes it. */
    REUSABLE_FOCUS,
    /** A single-use inscribed scroll. Consumed when it is the source of an inscription. */
    CONSUMABLE_SCROLL,
    /** Empty parchment, the only legal blank target. */
    BLANK_PARCHMENT,
    /** A scroll that already carries a spell, from any mod. Never blank. */
    FILLED_SCROLL;

    /** Whether an item of this kind survives being used as an inscription source. */
    public boolean isReusable() {
        return this == REUSABLE_BOOK || this == REUSABLE_FOCUS;
    }
}
