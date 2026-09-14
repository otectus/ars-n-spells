package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.contract.AttemptLedger;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.CostQuote;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
@EventBusSubscriber(modid = "ars_n_spells")
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
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()) != null
            ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
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
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null) {
            return;
        }
        if (LEDGER.openCount() == 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<CastAttempt> expired = LEDGER.expireOlderThan(
            server.overworld().getGameTime(), ATTEMPT_TTL_TICKS, forServer(server));
        for (CastAttempt attempt : expired) {
            LOGGER.warn("Swept a cast attempt that outlived its {}-tick TTL for {}; "
                    + "its reservation has been released.",
                ATTEMPT_TTL_TICKS, attempt.playerId());
        }
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
            return p == null ? 0.0d : BridgeManager.getNativeBridge(unit).getMana(p);
        }

        @Override
        public double max(UUID player, ResourceUnit unit) {
            Player p = resolver.apply(player);
            return p == null ? 0.0d : BridgeManager.getNativeBridge(unit).getMaxMana(p);
        }

        @Override
        public double debit(UUID player, ResourceUnit unit, double amount) {
            Player p = resolver.apply(player);
            if (p == null || amount <= 0.0d) {
                return 0.0d;
            }
            double before = BridgeManager.getNativeBridge(unit).getMana(p);
            if (!BridgeManager.getNativeBridge(unit).consumeMana(p, (float) amount)) {
                return 0.0d;
            }
            double after = BridgeManager.getNativeBridge(unit).getMana(p);
            return Math.max(0.0d, before - after);
        }

        @Override
        public double credit(UUID player, ResourceUnit unit, double amount) {
            Player p = resolver.apply(player);
            if (p == null || amount <= 0.0d) {
                return 0.0d;
            }
            double before = BridgeManager.getNativeBridge(unit).getMana(p);
            BridgeManager.getNativeBridge(unit).addMana(p, (float) amount);
            double after = BridgeManager.getNativeBridge(unit).getMana(p);
            return Math.max(0.0d, after - before);
        }
    }
}
