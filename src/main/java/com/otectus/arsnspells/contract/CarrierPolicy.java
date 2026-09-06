package com.otectus.arsnspells.contract;

/**
 * How the thing a spell was cast from behaves (audit V02).
 *
 * <p>Closes the finding that carrier semantics were read from a serialized cast-source value
 * riding along in the spell's own data. That value is edit-controllable and it survives being
 * copied onto a different item, so a scroll could claim book semantics and never be consumed.
 * A {@code CarrierPolicy} is derived by a loader adapter from the carrier's item kind at the
 * moment of the cast, and from nothing else.
 */
public enum CarrierPolicy {
    /** A reusable book or focus. Never consumed by casting. */
    REUSABLE_BOOK_SEMANTICS,
    /** A single-use scroll. Consumed on a successful cast. */
    CONSUMABLE_SCROLL_SEMANTICS,
    /** A native cast with no ANS carrier involved. The owning mod prices and charges it. */
    NATIVE_ONLY
}
