package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.contract.AttemptLedger;
import com.otectus.arsnspells.contract.AttemptState;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * The server-side home of the one {@link AttemptLedger} (audit V01, V03).
 *
 * <p>Closes the finding that a cast's price and its payment had no shared identity. A cost query
 * could charge mana, a long cast that was interrupted <em>and</em> ended normally refunded twice,
 * and a recast could open state no exit path ever closed. Here an attempt is opened once, quoted
 * once, reserved once, and settled exactly once - and release is idempotent because
 * {@link AttemptLedger#fail} goes through {@link CastAttempt#tryMarkReleased()}.
 *
 * <p><b>Server thread only.</b> {@link AttemptLedger} says so in its own javadoc and this does
 * not add locking; every caller below is on the cast path or the server tick.
 */
public final class AttemptLedgerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttemptLedgerService.class);

    /**
     * How long an attempt may stay open before the tick sweep cancels it.
     *
     * <p>Long casts and recasts legitimately span many ticks, so this is generous - it is a leak
     * guard, not a cast timer. It matches the order of magnitude of Iron's longest channelled
     * spells rather than {@code CrossCastContext}'s much shorter staging TTL.
     */
    public static final long ATTEMPT_TTL_TICKS = 20L * 60L;

    private static final AttemptLedger LEDGER = new AttemptLedger();

    private AttemptLedgerService() {}

    /** The live ledger. Exposed for tests and for the tick sweep; prefer the helpers below. */
    public static AttemptLedger ledger() {
        return LEDGER;
    }

    /** A {@link ResourceAccess} bound to {@code player}. */
    public static ResourceAccess accessFor(Player player) {
        return BridgeResourceAccess.of(player);
    }

    // ------------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------------

    /**
     * Open an attempt for {@code player} against {@code carrierIdentity}, already quoted.
     *
     * <p>The attempt is walked straight to {@link AttemptState#QUOTED}: the loader has nothing
     * meaningful to do between "the payload parsed" and "the price is known", and leaving it in
     * {@code REQUESTED} would only let a later caller take an illegal edge.
     */
    public static CastAttempt open(Player player, String carrierIdentity, int payloadRevision,
                                   CostQuote quote, long gameTime) {
        CastAttempt attempt =
            LEDGER.open(player.getUUID(), carrierIdentity, payloadRevision, quote, gameTime);
        attempt.validate();
        attempt.markQuoted();
        return attempt;
    }

    /** The open attempt for this player and carrier, if there is one. */
    public static Optional<CastAttempt> findOpen(Player player, String carrierIdentity) {
        if (player == null || carrierIdentity == null) {
            return Optional.empty();
        }
        return LEDGER.findOpen(player.getUUID(), carrierIdentity);
    }

    /** Every open attempt for a player, in the order they opened. */
    public static List<CastAttempt> openFor(Player player) {
        return player == null ? List.of() : LEDGER.openFor(player.getUUID());
    }

    /** Debit the quoted legs and hold them. Returns what was actually taken. */
    public static List<ResourceAmount> reserve(CastAttempt attempt, Player player) {
        return LEDGER.reserve(attempt, accessFor(player));
    }

    /** Turn a reservation into a payment. The attempt stays open until {@link #complete}. */
    public static void commit(CastAttempt attempt) {
        if (attempt != null && attempt.state() == AttemptState.RESERVED) {
            LEDGER.commit(attempt);
        }
    }

    /** Settle a committed attempt. Nothing is refunded. */
    public static void complete(CastAttempt attempt) {
        if (attempt == null || attempt.state().isTerminal()) {
            return;
        }
        if (attempt.state() == AttemptState.COMMITTED) {
            LEDGER.complete(attempt);
            return;
        }
        // The cast reported success without ever reaching the payment boundary. Nothing was
        // committed, so this is a release, not a settlement.
        fail(attempt, null);
    }

    /**
     * Release any reservation and fail the attempt. Idempotent: a second call credits nothing,
     * which is the double-refund a long cast that both cancelled and ended used to produce.
     */
    public static List<ResourceAmount> fail(CastAttempt attempt, Player player) {
        if (attempt == null) {
            return List.of();
        }
        ResourceAccess access = player != null ? accessFor(player) : BridgeResourceAccess.none();
        return LEDGER.fail(attempt, access);
    }

    /** As {@link #fail}, for an attempt abandoned rather than failed. */
    public static List<ResourceAmount> cancel(CastAttempt attempt, Player player) {
        if (attempt == null) {
            return List.of();
        }
        ResourceAccess access = player != null ? accessFor(player) : BridgeResourceAccess.none();
        return LEDGER.cancel(attempt, access);
    }

    /**
     * The leak guard. Called once per server tick; cancels and releases every attempt older
     * than {@link #ATTEMPT_TTL_TICKS}.
     *
     * <p>The sweep is global, so it resolves each attempt's owner through the server's player
     * list rather than against one bound player - a sweep that ran with the wrong player bound
     * would refund nothing and leave the reservation held.
     */
    public static void sweep(MinecraftServer server, long gameTime) {
        if (server == null || LEDGER.openCount() == 0) {
            return;
        }
        List<CastAttempt> expired = LEDGER.expireOlderThan(
            gameTime, ATTEMPT_TTL_TICKS, BridgeResourceAccess.ofServer(server));
        for (CastAttempt attempt : expired) {
            LOGGER.debug("Swept a cast attempt that no exit path closed: {} ({})",
                attempt.attemptId(), attempt.state());
        }
    }

    // ------------------------------------------------------------------
    //  Queries
    // ------------------------------------------------------------------

    /**
     * Whether {@code player} has an open attempt that has already committed a payment in
     * {@code unit}.
     *
     * <p>Audit V02: the Iron's SEPARATE path zeroes its event cost on the strength of the Ars
     * leg having pre-paid. Zeroing it when nothing has been committed is how a cross-cast became
     * free, so the zeroing is now conditional on this.
     */
    public static boolean hasCommittedLeg(Player player, ResourceUnit unit) {
        if (player == null) {
            return false;
        }
        for (CastAttempt attempt : LEDGER.openFor(player.getUUID())) {
            if (attempt.state() != AttemptState.COMMITTED) {
                continue;
            }
            for (ResourceAmount leg : attempt.paidLegs()) {
                if (leg.unit() == unit && !leg.isZero()) {
                    return true;
                }
            }
        }
        return false;
    }

}
