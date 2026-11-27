package com.otectus.arsnspells.glyph.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 3.4.3: a session's lifetime bound follows Ars's projectile lifespan, within its published bounds. */
class GlyphSessionLifetimeTest {
    @Test
    void theDefaultLifespanKeepsTheNinetySecondFloor() {
        // Ars's default max_lifespan is 60 seconds: 1,200 ticks of flight plus the 300-tick margin is under the floor.
        assertEquals(GlyphSessions.MAX_SESSION_TICKS, GlyphSessions.lifetimeFor(60));
        assertEquals(GlyphSessions.MAX_SESSION_TICKS, GlyphSessions.lifetimeFor(0));
        assertEquals(GlyphSessions.MAX_SESSION_TICKS, GlyphSessions.lifetimeFor(-5), "a negative lifespan counts as none");
        assertEquals(GlyphSessions.MAX_SESSION_TICKS, GlyphSessions.lifetimeFor(75));
        assertEquals(1820L, GlyphSessions.lifetimeFor(76));
    }

    @Test
    void aLongLifespanIsFollowedUpToTheTenMinuteCeiling() {
        assertEquals(3300L, GlyphSessions.lifetimeFor(150));
        assertEquals(GlyphSessions.MAX_LIFETIME_TICKS, GlyphSessions.lifetimeFor(585));
        assertEquals(GlyphSessions.MAX_LIFETIME_TICKS, GlyphSessions.lifetimeFor(600));
        // Ars accepts any int; the product must not overflow into a short or negative lifetime.
        assertEquals(GlyphSessions.MAX_LIFETIME_TICKS, GlyphSessions.lifetimeFor(Integer.MAX_VALUE));
    }
}
