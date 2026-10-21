package com.otectus.arsnspells.glyph.runtime;

/**
 * A running continuous Iron's spell started by an imported glyph. Driven one server tick at a
 * time by its continuation; never run in one tick.
 */
public interface GlyphChannel {

    /** Advance one tick (native per-tick behaviour, cast timer). */
    void tick();

    /** Whether a pulse is due this tick (Iron's pulses continuous spells every 10 ticks). */
    boolean pulseDue();

    /** Run one paid pulse. False when the native pulse could not run (target gone, refused). */
    boolean pulse();

    /** Whether the channel has used its whole duration. */
    boolean finished();

    /** End the channel: clean up native cast state, then commit the native cooldown if asked. */
    void end(boolean cancelled, boolean applyCooldown);

    /** The Iron's-unit price of one pulse at the channel's level. */
    int pulseCost();
}
