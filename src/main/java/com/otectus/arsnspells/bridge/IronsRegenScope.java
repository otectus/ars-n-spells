package com.otectus.arsnspells.bridge;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Marks Iron's own mana regeneration tick ({@code MagicManager.regenPlayerMana}) for one player.
 *
 * <p>In ARS_PRIMARY, Iron's {@code MagicData} mana accessors are routed to the Ars pool, so
 * Iron's regeneration reads the Ars balance and writes
 * {@code clamp(balance + increment, 0, (int) max_mana)} straight back into it. Iron's
 * {@code max_mana} attribute is only a mirror of the Ars ceiling: it is re-applied after the
 * equipment settles, it truncates to an integer, and until it catches up it can sit below the
 * real Ars maximum. A regeneration tick in that window used to write the lower ceiling into the
 * Ars pool, which players saw as mana vanishing while they merely scrolled their hotbar.
 * Inside this scope a routed write may raise the Ars pool, never lower it.
 *
 * <p>Lexical and exception-safe: the previous owner is restored however the tick exits.
 */
public final class IronsRegenScope {
    private static final ThreadLocal<UUID> ACTIVE = new ThreadLocal<>();

    private IronsRegenScope() {}

    public static <T> T run(UUID player, Supplier<T> tick) {
        UUID previous = ACTIVE.get();
        ACTIVE.set(player);
        try { return tick.get(); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }

    public static boolean isRegenTickFor(UUID player) {
        UUID active = ACTIVE.get();
        return active != null && active.equals(player);
    }

    /** The only rule the scope enforces: regeneration never moves a balance down. */
    public static boolean suppresses(double current, double proposed) {
        return proposed < current;
    }
}
