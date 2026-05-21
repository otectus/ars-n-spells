package com.otectus.arsnspells.events;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ANS-HIGH-011 — verifies that {@code CursedRingHandler.PendingLPCost} uses
 * a {@code long gameTimeStamp} (server-global) instead of the old
 * {@code int tickStamp} (per-player).
 *
 * <p>The bug fixed: a long-uptime player A's periodic sweep ran with
 * {@code now = playerA.tickCount} (a huge value), and evicted player B's
 * pending cost whose {@code tickStamp} had been captured from B's much
 * smaller {@code tickCount}. With server-global {@code level.getGameTime()},
 * all entries share a comparable time axis.
 */
class CursedRingHandlerGameTimeStampTest {

    @Test
    void pendingLPCost_hasGameTimeStampField_asLong() {
        Class<?> pendingClass = findInnerClass(CursedRingHandler.class, "PendingLPCost");
        try {
            Field f = pendingClass.getDeclaredField("gameTimeStamp");
            assertEquals(long.class, f.getType(),
                "gameTimeStamp must be a long (capacity for server-global game time)");
        } catch (NoSuchFieldException e) {
            fail("CursedRingHandler.PendingLPCost must declare a long gameTimeStamp "
                + "field per ANS-HIGH-011 (previously used int tickStamp)");
        }
    }

    @Test
    void pendingLPCost_doesNotHaveTickStampField() {
        Class<?> pendingClass = findInnerClass(CursedRingHandler.class, "PendingLPCost");
        try {
            pendingClass.getDeclaredField("tickStamp");
            fail("PendingLPCost should not still expose the old int tickStamp field");
        } catch (NoSuchFieldException expected) {
            // good — field has been renamed to gameTimeStamp
        }
    }

    @Test
    void virtueRingPendingAuraCost_hasGameTimeStampField() {
        Class<?> pendingClass = findInnerClass(VirtueRingHandler.class, "PendingAuraCost");
        try {
            Field f = pendingClass.getDeclaredField("gameTimeStamp");
            assertEquals(long.class, f.getType());
        } catch (NoSuchFieldException e) {
            fail("VirtueRingHandler.PendingAuraCost must declare a long gameTimeStamp "
                + "field per ANS-HIGH-011");
        }
    }

    private static Class<?> findInnerClass(Class<?> outer, String simpleName) {
        for (Class<?> inner : outer.getDeclaredClasses()) {
            if (simpleName.equals(inner.getSimpleName())) {
                return inner;
            }
        }
        fail(outer.getSimpleName() + " has no inner class named " + simpleName);
        return null; // unreachable
    }
}
