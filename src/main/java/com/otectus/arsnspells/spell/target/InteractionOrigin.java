package com.otectus.arsnspells.spell.target;

/**
 * How the intent behind an Ars cast reached the server.
 *
 * <p>The server always computes the target itself; the origin only says which input produced
 * the request, so traces, admission and the request discriminator can tell them apart. On Forge
 * 1.20.1 a right-click against a block or an entity that the block or entity did not consume
 * still reaches {@code RightClickItem}, so {@link #ITEM_USE} covers aimed and unaimed clicks
 * alike; the client's aim at that moment travels as a {@link TargetHint}.
 */
public enum InteractionOrigin {
    /** A right-click with an inscribed carrier that no block or entity consumed. */
    ITEM_USE,
    /** An Iron's spell-wheel, hotkey or staff cast delegated through a native proxy spell. */
    NATIVE_WHEEL,
    /** A server-side caller with no player input: a command, a ritual, a GameTest. */
    SERVER;

    public static InteractionOrigin fromWire(int ordinal) {
        InteractionOrigin[] values = values();
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Unknown interaction origin " + ordinal);
        return values[ordinal];
    }
}
