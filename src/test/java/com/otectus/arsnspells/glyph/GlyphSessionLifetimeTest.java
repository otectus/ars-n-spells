package com.otectus.arsnspells.glyph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 3.4.3: a session's lifetime follows Ars's projectile lifespan, within its published bounds. */
class GlyphSessionLifetimeTest {
    @Test
    void theDefaultLifespanIsOutlivedByTheMargin() {
        // Ars's default max_lifespan is 60 seconds: 1,200 ticks of flight plus the 300-tick margin.
        assertEquals(1500L, GlyphSessions.lifetimeFor(60));
    }

    @Test
    void aShortLifespanKeepsTheSixtySecondFloor() {
        assertEquals(GlyphSessions.LIFETIME_TICKS, GlyphSessions.lifetimeFor(0));
        assertEquals(GlyphSessions.LIFETIME_TICKS, GlyphSessions.lifetimeFor(45));
        assertEquals(1220L, GlyphSessions.lifetimeFor(46));
        assertEquals(GlyphSessions.LIFETIME_TICKS, GlyphSessions.lifetimeFor(-5), "a negative lifespan counts as none");
    }

    @Test
    void aLongLifespanIsFollowedUpToTheTenMinuteCeiling() {
        assertEquals(2100L, GlyphSessions.lifetimeFor(90));
        assertEquals(GlyphSessions.MAX_LIFETIME_TICKS, GlyphSessions.lifetimeFor(585));
        assertEquals(GlyphSessions.MAX_LIFETIME_TICKS, GlyphSessions.lifetimeFor(600));
        // Ars accepts any int; the product must not overflow into a short or negative lifetime.
        assertEquals(GlyphSessions.MAX_LIFETIME_TICKS, GlyphSessions.lifetimeFor(Integer.MAX_VALUE));
    }
}
