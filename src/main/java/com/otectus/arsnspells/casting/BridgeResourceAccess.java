package com.otectus.arsnspells.casting;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.IManaBridge;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.function.Function;

/**
 * The {@link ResourceAccess} port, backed by the named mana bridges.
 *
 * <p>The contract speaks in {@link UUID}s because it has no {@code Player} type. Two resolvers
 * cover every caller: the cast path has exactly one player in hand and binds to it, while the
 * per-tick leak sweep can settle any player's expired attempt and therefore resolves through the
 * server's player list.
 *
 * <p>{@link #debit} and {@link #credit} report the amount <em>actually</em> moved, measured
 * either side of the call. That is the whole point of the port (audit V24): Iron's clamps every
 * mana write down to the {@code max_mana} attribute, so a drain can move less than it was asked
 * for. Returning the requested amount would let {@link com.otectus.arsnspells.contract.AttemptLedger}
 * refund mana that was never taken.
 */
public final class BridgeResourceAccess implements ResourceAccess {

    private final Function<UUID, Player> resolver;

    private BridgeResourceAccess(Function<UUID, Player> resolver) {
        this.resolver = resolver;
    }

    /** Bound to one player; any other id resolves to nothing and moves nothing. */
    public static BridgeResourceAccess of(Player player) {
        UUID owner = player == null ? null : player.getUUID();
        return new BridgeResourceAccess(id -> id != null && id.equals(owner) ? player : null);
    }

    /** Resolves through the server's player list, for the sweep that settles stale attempts. */
    public static BridgeResourceAccess ofServer(MinecraftServer server) {
        return new BridgeResourceAccess(
            id -> server == null || id == null ? null : server.getPlayerList().getPlayer(id));
    }

    /** Moves nothing and reads zero. For a release whose player is no longer reachable. */
    public static ResourceAccess none() {
        return new BridgeResourceAccess(id -> null);
    }

    private static IManaBridge bridge(ResourceUnit unit) {
        return unit == ResourceUnit.IRONS_MANA
            ? BridgeManager.getNativeIronsBridge()
            : BridgeManager.getNativeArsBridge();
    }

    @Override
    public double current(UUID playerId, ResourceUnit unit) {
        Player player = resolver.apply(playerId);
        IManaBridge bridge = bridge(unit);
        return player != null && bridge != null ? bridge.getMana(player) : 0.0d;
    }

    @Override
    public double max(UUID playerId, ResourceUnit unit) {
        Player player = resolver.apply(playerId);
        IManaBridge bridge = bridge(unit);
        return player != null && bridge != null ? bridge.getMaxMana(player) : 0.0d;
    }

    @Override
    public double debit(UUID playerId, ResourceUnit unit, double amount) {
        Player player = resolver.apply(playerId);
        IManaBridge bridge = bridge(unit);
        if (player == null || bridge == null || amount <= 0.0d) {
            return 0.0d;
        }
        // Measure, do not assume: consumeMana reports whether it ran, not how much it moved.
        double before = bridge.getMana(player);
        if (!bridge.consumeMana(player, (float) amount)) {
            return 0.0d;
        }
        return Math.max(0.0d, before - bridge.getMana(player));
    }

    @Override
    public double credit(UUID playerId, ResourceUnit unit, double amount) {
        Player player = resolver.apply(playerId);
        IManaBridge bridge = bridge(unit);
        if (player == null || bridge == null || amount <= 0.0d) {
            return 0.0d;
        }
        double before = bridge.getMana(player);
        bridge.addMana(player, (float) amount);
        return Math.max(0.0d, bridge.getMana(player) - before);
    }
}
