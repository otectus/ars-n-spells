package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.ManaUnificationMode;

/**
 * Reaches {@link BridgeManager}'s package-private test seams from tests in other packages.
 *
 * <p>The seams stay package-private in production: they publish a routing snapshot without
 * constructing real bridges, which is only ever correct in a test. Widening them to {@code public}
 * to satisfy a test in {@code ..spell} would put a "set the mode to anything, with no bridges
 * behind it" method on the mod's public surface, which is the split state audit V04 removed.
 */
public final class BridgeTestSupport {

    private BridgeTestSupport() {}

    /** Publish a routing snapshot for {@code mode}, reporting Iron's present. */
    public static void setMode(ManaUnificationMode mode) {
        BridgeManager.testSetMode(mode);
    }

    /** Publish {@code routing}, or {@code null} to return to the pre-init state. */
    public static void setRouting(BridgeRouting routing) {
        BridgeManager.testSetRouting(routing);
    }

    /** Drop any published snapshot. Call from an {@code @AfterEach}: the field is static. */
    public static void clear() {
        BridgeManager.testSetRouting(null);
    }
}
