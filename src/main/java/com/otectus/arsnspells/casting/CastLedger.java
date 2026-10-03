package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.contract.AttemptLedger;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceMovement;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The server-side home of the open cast attempts, and the loader's bridge to them (audit V01).
 *
 * <p>Closes the finding that a cost query could charge mana. Upstream Ars builds a fresh
 * {@code SpellCostCalcEvent} on <em>every</em> {@code getResolveCost()} call, and both
 * {@code canCast()} and {@code expendMana()} call it, so the old "apply the multiplier once
 * per attempt" flag made the first query answer 200 and the second answer 100 - and the
 * SEPARATE-mode Iron's share was taken inside that cost calculation, which is a debit
 * performed by a question.
 *
 * <p>The split is now: a cost query reads the open attempt's already-computed
 * {@link CostQuote} and answers, always the same, never moving a resource. Resources move at
 * two named points only - {@link AttemptLedger#reserve} at the pre-cast gate, and the commit
 * at the verified native payment boundary.
 *
 * <p>Not thread-safe, by the same rule the contract states: server thread only.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public final class CastLedger {

    private static final Logger LOGGER = LoggerFactory.getLogger(CastLedger.class);

    /**
     * How long an attempt may stay open before the sweep cancels it.
     *
     * <p>100 ticks, five seconds, matching {@code CrossCastContext}'s TTL. A long or recast
     * cast legitimately outlives a single tick, so the attempt is keyed by its own id and
     * closed at the native finish or cancel boundary; this is only the leak guard for the
     * paths that reach neither.
     */
    public static final long ATTEMPT_TTL_TICKS = 100L;

    private static final AttemptLedger LEDGER = new AttemptLedger();

    private CastLedger() {
    }

    /** The one ledger. Exposed so tests can drive it with a fake {@link ResourceAccess}. */
    public static AttemptLedger ledger() {
        return LEDGER;
    }

    /**
     * The opaque carrier identity the contract keys an attempt on.
     *
     * <p>The item's registry name, not the stack instance: a stack is copied freely between
     * the pre-cast gate and the payment boundary, and an identity that did not survive the
     * copy would orphan the attempt and leave its reservation held.
     */
    public static String carrierIdentity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "ans:no_carrier";
        }
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()) != null
            ? net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString()
            : "ans:unknown_carrier";
    }

    /** Open an attempt holding {@code quote}, in {@link com.otectus.arsnspells.contract.AttemptState#REQUESTED}. */
    public static CastAttempt open(UUID playerId, String carrierIdentity, int payloadRevision,
                                   CostQuote quote, long gameTick) {
        return LEDGER.open(playerId, carrierIdentity, payloadRevision, quote, gameTick);
    }

    /** The open attempt for this player and carrier, if any. */
    public static Optional<CastAttempt> findOpen(UUID playerId, String carrierIdentity) {
        return LEDGER.findOpen(playerId, carrierIdentity);
    }

    /**
     * Move an attempt to RESERVED, debiting every leg.
     *
     * @return what was actually taken, per leg; empty when the attempt could not be reserved
     */
    public static List<ResourceAmount> reserve(CastAttempt attempt, ResourceAccess access) {
        for (var leg : attempt.quote().legs()) access.prepare(attempt.playerId(), leg.unit());
        attempt.validate();
        attempt.markQuoted();
        return LEDGER.reserve(attempt, access);
    }

    /**
     * Turn the reservation into payment and settle, exactly once.
     *
     * <p>Called at the verified native payment boundary and nowhere else. A second call
     * throws rather than paying twice: the state machine has no COMMITTED -> COMMITTED edge.
     */
    public static void commitAndComplete(CastAttempt attempt) {
        LEDGER.commit(attempt);
        LEDGER.complete(attempt);
    }

    /** Release a reservation and fail the attempt. Idempotent: a second call refunds nothing. */
    public static List<ResourceAmount> fail(CastAttempt attempt, ResourceAccess access) {
        return LEDGER.fail(attempt, access);
    }

    /** Release a reservation and cancel the attempt. Idempotent. */
    public static List<ResourceAmount> cancel(CastAttempt attempt, ResourceAccess access) {
        return LEDGER.cancel(attempt, access);
    }

    /**
     * The TTL leak guard. Recast and long-cast paths can open an attempt that no exit path
     * reaches; without this sweep its reservation stays held against the player forever.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }
        if (event.getServer().overworld().getGameTime() % 20 == 0) AlternativePayment.retryReleases();
        if (LEDGER.openCount() == 0 || event.getServer().overworld().getGameTime() % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        ResourceAccess access = forServer(server);
        List<CastAttempt> expired = LEDGER.expireOlderThan(
            server.overworld().getGameTime(), ATTEMPT_TTL_TICKS, access);
        for (CastAttempt attempt : expired) {
            if (!attempt.isReleased()) releaseIfCapped(LEDGER, attempt, access);
            if (attempt.isReleased()) LOGGER.debug("Released expired attempt {} for {}", attempt.attemptId(), attempt.playerId());
        }
    }

    /**
     * Whether an earlier attempt still owes this player a refund that must be settled before
     * anything new is taken. What can be settled now is settled first.
     *
     * <p>A refund lands in a native pool that clamps at its ceiling. Once the pool has refilled
     * to its ceiling (by regeneration, or because the ceiling fell under the balance), the rest
     * of the refund has nowhere to go. Before this, that remainder stayed owed for good: each
     * retry credited nothing, each new cast was refused as {@code INCOMPLETE_COMPENSATION}, and
     * the recovery journal carried it across restarts, so the player could not cast at all. A
     * remainder a full pool cannot hold is now released and logged as capped. A pool that still
     * has room, or a movement whose outcome is unknown, keeps blocking as before.
     */
    public static boolean blocksPayment(Player player) {
        return blocksPayment(LEDGER, player.getUUID(), () -> forPlayer(player));
    }

    /** {@link #blocksPayment(Player)} against any ledger and pool; the unit-test seam. */
    static boolean blocksPayment(AttemptLedger ledger, UUID player, java.util.function.Supplier<ResourceAccess> pools) {
        ResourceAccess access = null;
        for (CastAttempt attempt : ledger.openFor(player)) {
            if (!attempt.state().isTerminal() || attempt.isReleased()) continue;
            if (access == null) access = pools.get();
            ledger.cancel(attempt, access);
            if (!attempt.isReleased()) releaseIfCapped(ledger, attempt, access);
        }
        return ledger.openFor(player).stream().anyMatch(a -> a.state().isTerminal() && !a.isReleased());
    }

    /** Release a terminal attempt whose known remainder only fails to land because its pool is full. */
    private static void releaseIfCapped(AttemptLedger ledger, CastAttempt attempt, ResourceAccess access) {
        if (!attempt.state().isTerminal() || !attempt.unknownUnits().isEmpty()) return;
        List<ResourceAmount> owed = attempt.remainingRefunds();
        if (owed.isEmpty()) return;
        try {
            for (ResourceAmount leg : owed) {
                if (access.current(attempt.playerId(), leg.unit()) < access.max(attempt.playerId(), leg.unit())) return;
            }
        } catch (RuntimeException unavailable) {
            // Offline or unreadable: the remainder may still fit later, so it stays owed.
            return;
        }
        if (!attempt.tryMarkReleased()) return;
        ledger.cancel(attempt, access); // A released attempt is only forgotten.
        LOGGER.info("[CastPayment] refund for attempt {} capped: {} did not fit under the player's full pool and was released",
            attempt.attemptId(), owed);
    }

    /**
     * Whether a native payment write took effect, as the native mod and its listeners shaped it.
     *
     * <p>Both native pools clamp every write to their ceiling, and {@code ChangeManaEvent}
     * listeners may change the new balance (a channel discount, a reprieve at zero). Native
     * casting keeps whatever balance results, so ANS does too, and the reservation records
     * exactly what moved. Only a write that left the pool unchanged is refused: a cancelled
     * debit must not become a free spell. Requiring the arithmetic result instead refused every
     * cast those rules touched and refunded it as a fault.
     */
    static boolean acceptsNativeWrite(ResourceMovement move) {
        if (move.requested() == 0) return move.error() == null;
        return move.error() == null && move.known() && Double.isFinite(move.reported()) && move.reported() >= 0
            && move.before() >= move.requested() && move.after() != move.before();
    }

    /** A {@link ResourceAccess} bound to one player. */
    public static ResourceAccess forPlayer(Player player) {
        return new BridgeResourceAccess(uuid -> uuid.equals(player.getUUID()) ? player : null);
    }

    /** A {@link ResourceAccess} that resolves any online player, for the TTL sweep. */
    public static ResourceAccess forServer(MinecraftServer server) {
        return new BridgeResourceAccess(uuid -> server.getPlayerList().getPlayer(uuid));
    }

    /**
     * The loader implementation of the contract's resource port.
     *
     * <p>Every method reports what actually happened rather than what was asked for. The
     * pools clamp, and a reservation released for more than it took is a mana duplication
     * bug; reading the pool either side of the move is the only honest way to know.
     */
    private static final class BridgeResourceAccess implements ResourceAccess {

        private final Function<UUID, Player> resolver;

        BridgeResourceAccess(Function<UUID, Player> resolver) {
            this.resolver = resolver;
        }

        @Override
        public double current(UUID player, ResourceUnit unit) {
            Player p = resolver.apply(player);
            return BridgeManager.getNativeBridge(unit).transactionMana(requirePlayer(p));
        }

        @Override
        public double max(UUID player, ResourceUnit unit) {
            Player p = resolver.apply(player);
            return BridgeManager.getNativeBridge(unit).transactionMax(requirePlayer(p));
        }

        private Player requirePlayer(Player player) {
            if (player == null || player.level().isClientSide() || !player.getServer().isSameThread())
                throw new IllegalStateException("Native resource requires an available player on the server thread");
            return player;
        }
        @Override public void prepare(UUID id, ResourceUnit unit) {
            Player player = requirePlayer(resolver.apply(id));
            if (unit == ResourceUnit.IRONS_MANA)
                com.otectus.arsnspells.equipment.EquipmentIntegration.ensureSharedPoolCeiling(player);
        }
        @Override public double expectedAfterDebit(ResourceUnit unit, double before, double amount) {
            return unit == ResourceUnit.IRONS_MANA ? (double) ((float) before - (float) amount) : before - amount;
        }

        /** See {@link CastLedger#acceptsNativeWrite}. */
        @Override
        public boolean acceptsDebit(ResourceUnit unit, ResourceMovement move) {
            return acceptsNativeWrite(move);
        }

        /**
         * Pay through the native write, including when the pool holds more than its ceiling.
         *
         * <p>A pool can sit above its ceiling for a while: Iron's {@code max_mana} or Ars's
         * maximum can fall under a full pool when gear, curios, effects or another mod's
         * modifiers change, and each mod clamps the surplus on its own next write. The payment is
         * such a write and runs exactly as the native cast would: the price is subtracted and
         * the result clamped. When the surplus is smaller than the price, only the price moves.
         * 3.3.4 refused to pay in this state, and nothing ever cleared it, so every cast billed to
         * that pool failed for as long as the ceiling stayed below the balance.
         */
        @Override
        public double debit(UUID player, ResourceUnit unit, double amount) {
            Player p = resolver.apply(player);
            if (p == null || amount <= 0.0d) {
                return 0.0d;
            }
            requirePlayer(p);
            double before = current(player, unit);
            double ceiling = max(player, unit);
            if (!BridgeManager.getNativeBridge(unit).transactionDebit(p, amount))
                throw new IllegalStateException("Native debit refused");
            double after = current(player, unit);
            if (before > ceiling) com.otectus.arsnspells.bridge.ManaTrace.paidAboveCeiling(p, unit, before, ceiling, amount, after);
            return Math.max(0.0d, before - after);
        }

        /**
         * Return up to {@code amount}. A pool at or above its ceiling cannot hold more, and a
         * native write there would clamp the balance down rather than raise it, so nothing is
         * written; {@link CastLedger#blocksPayment} then releases the remainder as capped.
         */
        @Override
        public double credit(UUID player, ResourceUnit unit, double amount) {
            Player p = resolver.apply(player);
            if (p == null || amount <= 0.0d) {
                return 0.0d;
            }
            requirePlayer(p);
            double before = current(player, unit);
            if (before >= max(player, unit)) return 0.0d;
            BridgeManager.getNativeBridge(unit).transactionCredit(p, amount);
            double after = current(player, unit);
            return Math.max(0.0d, after - before);
        }
    }
}
