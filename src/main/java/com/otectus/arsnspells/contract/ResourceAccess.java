package com.otectus.arsnspells.contract;

import java.util.UUID;

/**
 * The port through which the contract reads and moves a player's resources (audit V24).
 *
 * <p>Closes the finding that a debit was assumed to have succeeded because the balance check
 * before it passed. Between the check and the drain the pool could have been reduced by regen
 * ticks, another mod, or a second concurrent cast, and the loader's own drain call silently
 * clamps to what is there. The old code then refunded, or charged a second leg against, an
 * amount that was never actually moved.
 *
 * <p>{@link #debit} and {@link #credit} therefore return the amount <em>actually</em> moved, not
 * the amount requested. That single change is what makes a partial drain honest and lets
 * {@link AttemptLedger} refund exactly what it took.
 *
 * <p>Implemented per loader, outside this package, against the real native pool API.
 */
public interface ResourceAccess {

    /** The player's current balance in {@code unit}. */
    double current(UUID player, ResourceUnit unit);

    /** The player's maximum balance in {@code unit}. */
    double max(UUID player, ResourceUnit unit);

    /**
     * Remove up to {@code amount} from the player's pool.
     *
     * @return the amount actually removed, between {@code 0} and {@code amount}
     */
    double debit(UUID player, ResourceUnit unit, double amount);

    /**
     * Return up to {@code amount} to the player's pool.
     *
     * @return the amount actually added, which may be less than {@code amount} if the pool is
     *         capped below its maximum
     */
    double credit(UUID player, ResourceUnit unit, double amount);
}
