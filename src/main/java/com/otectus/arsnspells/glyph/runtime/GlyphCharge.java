package com.otectus.arsnspells.glyph.runtime;

import com.otectus.arsnspells.glyph.IronGlyphBridge;

/**
 * A charged (Iron's LONG) Iron's spell started by an imported glyph, on a detached casting slot, in
 * Iron's own order (3.4.1 review CAST-04): {@code SpellPreCastEvent} and the spell's
 * {@code checkPreCastConditions} at initiation, then {@code initiateCast} and {@code onServerPreCast}
 * (so a spell or addon that prepares cast data at the start finds it at the release),
 * {@code onServerCastTick} every tick of the charge, and at the release the final cast event, the
 * payment and {@code onCast}. Ending, released or cancelled, resets the detached slot; a cancelled
 * charge gets no cooldown, as a native interrupted charge does not. Driven one tick at a time by its
 * continuation; never touches the player's own slot.
 */
public interface GlyphCharge {

    /** One tick of the charge: the native per-tick hook. Contained: a failure marks {@link #failed()}. */
    void tick();

    /** Release the charge: the native cast at its event-final level and cost, paid through {@code gate}. */
    IronGlyphBridge.ExecutionResult complete(IronGlyphBridge.PaymentGate gate);

    /** End the charge before its release: no effect, no cooldown, the detached slot reset. Idempotent. */
    void cancel();

    /** Whether native code threw during the charge, before any effect. */
    boolean failed();
}
