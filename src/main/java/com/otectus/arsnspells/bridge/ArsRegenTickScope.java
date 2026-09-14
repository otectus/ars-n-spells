package com.otectus.arsnspells.bridge;

import java.util.UUID;

/** Server-thread scope identifying native Ars regeneration; ordinary mutations remain usable. */
public final class ArsRegenTickScope {

    private static final ThreadLocal<UUID> IN_REGEN_TICK = new ThreadLocal<>();

    private ArsRegenTickScope() {
    }

    /** Called at the head of Ars's regen tick for {@code player}. */
    public static void enter(UUID player) {
        IN_REGEN_TICK.set(player);
    }

    /** Called at every return of Ars's regen tick. */
    public static void exit() {
        IN_REGEN_TICK.remove();
    }

    /** Whether the mutation currently being processed came from Ars's regen tick for this player. */
    public static boolean isInRegenTickFor(UUID player) {
        UUID inTick = IN_REGEN_TICK.get();
        return inTick != null && inTick.equals(player);
    }
}
