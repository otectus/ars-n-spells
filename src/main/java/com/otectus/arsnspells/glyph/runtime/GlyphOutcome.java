package com.otectus.arsnspells.glyph.runtime;

/**
 * What one imported-effect execution came to. Kept distinct on purpose: a projectile accepted for
 * delivery is not proof its effect happened, and a started charge is not a completed spell.
 */
public enum GlyphOutcome {
    /** Refused before any foreign effect; anything reserved for it was released. */
    REJECTED,
    /** Admitted and paid for; the effect runs later (a charge or channel in progress). */
    ACCEPTED_PENDING,
    /** The native effect ran (an instant effect, or a completed charge). */
    EFFECT_APPLIED,
    /** A charge or channel finished its whole lifecycle. */
    COMPLETED,
    /** A pending charge or channel was cancelled; unspent reservations were released. */
    CANCELED,
    /**
     * 3.4.1 review CAST-04: the native effect began and then threw. It stays paid, counts against the
     * emission budget and gets its cooldown: an effect may already have happened.
     */
    FAILED_AFTER_EFFECT
}
