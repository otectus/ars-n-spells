package com.otectus.arsnspells.casting;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.otectus.arsnspells.compat.AlternativeResourceAccess;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The ring handlers' view of the alternative payment leg their cast opened (audit V23).
 *
 * <p>Closes the finding that the ring handlers ran their own charge at
 * {@code SpellResolveEvent.Post}, from a FIFO deque that nothing correlated with the pre-cast
 * check. The charge now happens once, at the pre-cast gate, against the cast's own
 * {@link CastAttempt}; the handlers reach that same leg through this class, by the same
 * (player, carrier) key the mana legs use, and can only settle or release it.
 *
 * <p>The methods here take a {@link SpellContext} rather than an item stack because that is
 * what the resolve events carry, and the carrier identity has to be derived the same way on
 * both sides of the transaction or the two would look up different attempts.
 *
 * <p><b>Deliberately not built on one SpellResolveEvent per cast.</b> Ars posts Pre and Post
 * around a resolve, and a delayed or repeated resolve posts them again; nothing here assumes
 * exactly one of either. Commit and release are both idempotent, which is the only assumption
 * that survives contact with a projectile that resolves on impact.
 */
public final class RingPaymentLegs {

    private static final Logger LOGGER = LoggerFactory.getLogger(RingPaymentLegs.class);

    private RingPaymentLegs() {
    }

    /**
     * Settle the leg this cast reserved.
     *
     * <p>Charges nothing: the resource moved at the pre-cast gate. This reports whether the
     * cast was in fact paid for, which is what the handlers' failure branches are asking.
     *
     * @param quotedAmount what the handler thinks the cast was priced at, for the log only
     * @return {@code true} when a leg was settled, or when one had already been settled by the
     *         expend boundary; {@code false} when this cast never held one
     */
    public static boolean commit(Player player, SpellContext context, int quotedAmount) {
        Optional<CastAttempt> open = findAttempt(player, context);
        if (open.isEmpty()) {
            LOGGER.debug("No open attempt for {}; the alternative leg was already settled at the "
                + "expend boundary, or this cast never opened one", player.getName().getString());
            // The expend boundary settles and then closes the attempt, so "no attempt" is the
            // normal successful path, not a failure. A cast that genuinely never reserved is
            // caught at the gate, which denies before it gets here.
            return true;
        }
        CastAttempt attempt = open.get();
        double settled = AlternativePayment.commit(attempt.attemptId());
        if (!attempt.state().isTerminal()) {
            // The attempt carries no mana reservation - the ring zeroed the mana cost - so
            // cancelling it refunds an empty list and simply closes it.
            CastLedger.cancel(attempt, CastLedger.forPlayer(player));
        }
        if (settled <= 0.0d) {
            LOGGER.debug("Nothing was held for {}'s cast (quoted {})",
                player.getName().getString(), quotedAmount);
            return false;
        }
        return true;
    }

    /**
     * Give back the leg this cast reserved, because the cast is not going to happen: the ring
     * came off, the carrier changed, or the cast was interrupted.
     *
     * @return the amount actually returned
     */
    public static double release(Player player, SpellContext context) {
        Optional<CastAttempt> open = findAttempt(player, context);
        if (open.isEmpty()) {
            return 0.0d;
        }
        CastAttempt attempt = open.get();
        AlternativePayment.Leg leg = AlternativePayment.peek(attempt.attemptId());
        double back = 0.0d;
        if (leg != null) {
            back = AlternativePayment.release(attempt.attemptId(), accessFor(player, leg.unit()));
        }
        if (!attempt.state().isTerminal()) {
            CastLedger.cancel(attempt, CastLedger.forPlayer(player));
        }
        return back;
    }

    private static Optional<CastAttempt> findAttempt(Player player, SpellContext context) {
        if (player == null) {
            return Optional.empty();
        }
        ItemStack carrier = context == null ? null : context.getCasterTool();
        return CastLedger.findOpen(player.getUUID(), CastLedger.carrierIdentity(carrier));
    }

    private static ResourceAccess accessFor(Player player, ResourceUnit unit) {
        Function<UUID, Player> resolver = id -> id.equals(player.getUUID()) ? player : null;
        return unit == ResourceUnit.LP
            ? AlternativeResourceAccess.lp(resolver)
            : AlternativeResourceAccess.aura(resolver);
    }
}
