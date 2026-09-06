package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceUnit;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where a mutation of Ars's mana capability actually lands (audit V06).
 *
 * <p>Closes the finding that {@code MixinManaCapability} suppressed Ars's regeneration by making
 * {@code ManaCap.addMana} a blanket no-op. {@code addMana} is not the regeneration tick; it is
 * the capability's whole additive surface. Every third-party mana grant - a potion, a ritual, an
 * artifact, another integration mod - travelled through the same method and was silently
 * discarded, so in a shared-pool mode those effects did nothing at all and gave no sign of it.
 *
 * <p>The two concerns are now separated:
 *
 * <ul>
 *   <li>{@code MixinManaCapEventsRegen} suppresses <b>only</b> the native regeneration tick, at
 *       the one call site in {@code ManaCapEvents.playerOnTick} that performs it.</li>
 *   <li>This class routes every other mutation to the authoritative pool, so a {@code +50} really
 *       does become {@code +50} in whichever units that pool counts in.</li>
 * </ul>
 *
 * <p>The routing is written against {@link ResourceAccess} rather than a bridge so it can be
 * exercised against a fake pool, with no Minecraft in the way.
 *
 * <h2>The guard</h2>
 *
 * <p>Per-player <em>and</em> per-direction. Per-player alone was already a fix (ANS-HIGH-010: a
 * thread-global flag suppressed interception for every player while any player's bridge call was
 * in flight, so an AoE spell reading another player's mana bypassed the bridge). Per-direction is
 * the other half: routing a write ends up reading the pool back to report the new balance, and a
 * single flag makes that read look like recursion and drop it through to stale native data. A
 * read may nest inside a write; only a write inside a write is recursion.
 */
public final class ManaMutationRouter {

    /** Which way a guarded call is moving mana, or which call it is nested inside. */
    public enum Direction {
        /** Reading a balance or a maximum. */
        READ,
        /** Adding to or removing from a pool. */
        WRITE,
        /**
         * Ars's regeneration tick is in flight for this player.
         *
         * <p>This is the scoping that makes regen suppression site-specific without an
         * instruction-level injection point. {@code MixinManaCapEventsRegen} brackets
         * {@code ManaCapEvents.playerOnTick} at HEAD and RETURN, and an {@code addMana} arriving
         * while the bracket is held is - in the pinned Ars 5.13.1.1400 - necessarily the regen
         * call, because {@code playerOnTick} contains exactly one {@code addMana} invocation.
         * An {@code addMana} arriving outside it is a third-party grant and is routed.
         *
         * <p>A {@code @Redirect} on that single invocation would say the same thing more
         * directly, but instruction-level injection points are rejected outright when another
         * mod {@code @Overwrite}-merges the target, and {@code require = 0} does not soften it -
         * the load aborts for every mod in the chain. HEAD and RETURN are structurally immune.
         */
        REGEN_TICK
    }

    private static final ThreadLocal<Map<UUID, EnumSet<Direction>>> ACTIVE =
        ThreadLocal.withInitial(HashMap::new);

    private ManaMutationRouter() {}

    // ------------------------------------------------------------------
    //  Reentrancy guard
    // ------------------------------------------------------------------

    /** Whether a call of {@code direction} is already in flight for {@code player}. */
    public static boolean isGuarded(UUID player, Direction direction) {
        EnumSet<Direction> active = ACTIVE.get().get(player);
        return active != null && active.contains(direction);
    }

    /** Claim the guard. Always paired with {@link #exit} in a {@code finally}. */
    public static void enter(UUID player, Direction direction) {
        ACTIVE.get().computeIfAbsent(player, k -> EnumSet.noneOf(Direction.class)).add(direction);
    }

    /** Release the guard, and the thread-local itself once nothing is in flight. */
    public static void exit(UUID player, Direction direction) {
        Map<UUID, EnumSet<Direction>> map = ACTIVE.get();
        EnumSet<Direction> active = map.get(player);
        if (active != null) {
            active.remove(direction);
            if (active.isEmpty()) {
                map.remove(player);
            }
        }
        if (map.isEmpty()) {
            // Avoid a ThreadLocal leak on long-lived server threads.
            ACTIVE.remove();
        }
    }

    // ------------------------------------------------------------------
    //  Policy
    // ------------------------------------------------------------------

    /**
     * Whether Ars's own regeneration tick must be suppressed.
     *
     * <p>Only in the shared-pool modes, where Ars's pool is a view of Iron's and regenerating it
     * separately would mint mana. SEPARATE and DISABLED both keep two real pools, and each must
     * go on regenerating on its own.
     */
    public static boolean suppressNativeRegen(ManaUnificationMode mode, boolean unificationEnabled) {
        return unificationEnabled && mode != null && mode.usesSharedPool();
    }

    // ------------------------------------------------------------------
    //  Routing
    // ------------------------------------------------------------------

    /**
     * Add {@code amount} of Ars mana to the authoritative pool.
     *
     * <p>The amount is a quantity of Ars mana as the caller understands it; {@code unit} is the
     * pool it lands in. No conversion is applied, matching every other shared-pool seam in this
     * mod - a shared pool is one pool counted once, not two pools with an exchange rate.
     *
     * @return the pool's balance after the add, which is what {@code ManaCap.addMana} contracts
     *         to return
     */
    public static double routeAdd(UUID player, ResourceUnit unit, ResourceAccess access,
                                  double amount) {
        if (isGuarded(player, Direction.WRITE)) {
            // Our own write, re-entering through the bridge. Report the balance and move nothing.
            return access.current(player, unit);
        }
        enter(player, Direction.WRITE);
        try {
            if (amount > 0.0d) {
                access.credit(player, unit, amount);
            } else if (amount < 0.0d) {
                access.debit(player, unit, -amount);
            }
            return access.current(player, unit);
        } finally {
            exit(player, Direction.WRITE);
        }
    }

    /**
     * Remove {@code amount} of Ars mana from the authoritative pool.
     *
     * @return the pool's balance after the removal
     */
    public static double routeRemove(UUID player, ResourceUnit unit, ResourceAccess access,
                                     double amount) {
        return routeAdd(player, unit, access, -amount);
    }

    /** The pool a routed mutation lands in. */
    public static ResourceUnit authoritativeUnit() {
        BridgeRouting routing = BridgeManager.routing();
        return routing != null ? routing.snapshot().authoritativeUnit() : ResourceUnit.ARS_MANA;
    }
}
