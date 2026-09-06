package com.otectus.arsnspells.contract;

/**
 * The only legal vocabulary of spell-payment units.
 *
 * <p>Closes the audit finding that costs were passed around as bare {@code float}s whose unit
 * was implied by the call site. An Ars mana cost and an Iron's mana cost are not the same
 * quantity, and a Living-Point or aura cost is neither; conflating them is what let a
 * conversion be applied twice, or not at all, depending on which seam you entered through.
 * Every amount in this package carries its unit.
 */
public enum ResourceUnit {
    /** Ars Nouveau source/mana, as reported by the Ars mana capability. */
    ARS_MANA,
    /** Iron's Spellbooks mana, as reported by the Iron's magic data. */
    IRONS_MANA,
    /** Sanctified Legacy Living Points, spent by the Ring of the Seven Curses. */
    LP,
    /** Covenant of the Seven aura, spent by the Ring of the Seven Virtues. */
    AURA
}
