package com.otectus.arsnspells.compat;

import com.otectus.arsnspells.contract.CompatibilityStatus;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.UUID;
import java.util.function.Function;

/**
 * The Living-Point and aura payment ports, and the only place they are constructed
 * (audit V23, V24).
 *
 * <p>Closes two findings. V23: a Covenant ring charge was decided by one query queue and paid
 * from another, with nothing tying the answer to the payment - the pre-cast check asked
 * "does this player have enough?" and a later handler asked "take this much", and neither
 * knew the other's amount. V24: both of those later handlers reported a boolean, so a partial
 * drain or a silently failed syphon was indistinguishable from a completed payment, and the
 * cast went ahead as if it had been paid for.
 *
 * <p>Both accesses report the amount <em>actually</em> moved, which is what
 * {@link ResourceAccess} requires and what lets a reservation be released for exactly what it
 * took. Each also carries a {@link CompatibilityStatus} rather than an {@code isAvailable}
 * boolean: "the mod is loaded" is not "its API resolved and its methods are callable", and the
 * difference between those two is what turned a drifted adapter into free spells.
 *
 * <p><b>Class-loading safety.</b> Covenant of the Seven and Nature's Aura are optional. No type
 * from either appears in this file, or in {@code contract} - every call goes through
 * {@link SanctifiedLegacyCompat}, which is reflective. The factory methods below are
 * nonetheless the only construction site and are the place the {@code ModList} gate lives, so
 * the pattern matches {@code IronsProxyCastDriver}: the guard is outside the method whose body
 * would resolve the optional types, never inside it.
 */
public final class AlternativeResourceAccess {

    /** Adapter id reported by the LP access, matching the naming on {@code ModeRoutingSnapshot}. */
    public static final String LP_ADAPTER_ID = "ans:sanctified_lp";

    /** Adapter id reported by the aura access. */
    public static final String AURA_ADAPTER_ID = "ans:covenant_aura";

    private AlternativeResourceAccess() {
    }

    /**
     * The LP access, or {@code null} when no mod that grants LP costs is installed.
     *
     * <p>Callers must null-check rather than assume: a null access means there is nothing to
     * charge, which is different from an access that is present and degraded.
     */
    public static LpAccess lp(Function<UUID, Player> resolver) {
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return null;
        }
        return new LpAccess(resolver);
    }

    /** The aura access, or {@code null} when Covenant of the Seven is not installed. */
    public static AuraAccess aura(Function<UUID, Player> resolver) {
        if (!ModList.get().isLoaded("covenant_of_the_seven")) {
            return null;
        }
        return new AuraAccess(resolver);
    }

    /** What the LP adapter knows about itself, without constructing one. */
    public static CompatibilityStatus lpStatus() {
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return new CompatibilityStatus(LP_ADAPTER_ID, CompatibilityStatus.State.ABSENT,
                "neither covenant_of_the_seven nor enigmaticlegacy is installed");
        }
        if (SanctifiedLegacyCompat.getLPSourceMode() == SanctifiedLegacyCompat.LPSourceMode.BLOOD_MAGIC_ONLY
            && !SanctifiedLegacyCompat.isBloodMagicAvailable()) {
            return new CompatibilityStatus(LP_ADAPTER_ID, CompatibilityStatus.State.DEGRADED,
                "lp_source_mode is blood_magic_only but Blood Magic did not resolve");
        }
        return CompatibilityStatus.verified(LP_ADAPTER_ID);
    }

    /** What the aura adapter knows about itself, without constructing one. */
    public static CompatibilityStatus auraStatus() {
        if (!ModList.get().isLoaded("covenant_of_the_seven")) {
            return new CompatibilityStatus(AURA_ADAPTER_ID, CompatibilityStatus.State.ABSENT,
                "covenant_of_the_seven is not installed");
        }
        if (!SanctifiedLegacyCompat.isAuraBridgeComplete()) {
            return new CompatibilityStatus(AURA_ADAPTER_ID, CompatibilityStatus.State.DEGRADED,
                "the Nature's Aura reflection bridge is incomplete; a drain cannot be released");
        }
        return CompatibilityStatus.verified(AURA_ADAPTER_ID);
    }

    /** Common plumbing: resolve a player, and refuse to answer for units that are not ours. */
    private abstract static class PlayerBoundAccess implements ResourceAccess {

        private final Function<UUID, Player> resolver;
        private final ResourceUnit unit;

        PlayerBoundAccess(Function<UUID, Player> resolver, ResourceUnit unit) {
            this.resolver = resolver;
            this.unit = unit;
        }

        /** The unit this access is the authority for. */
        public final ResourceUnit unit() {
            return unit;
        }

        final Player player(UUID id) {
            return id == null ? null : resolver.apply(id);
        }

        final boolean handles(ResourceUnit asked) {
            return asked == unit;
        }
    }

    /**
     * Living Points, as spent by the Ring of the Seven Curses.
     *
     * <p>The debit takes Blood Magic first and health second, exactly as the historical
     * {@code consumeLP} did; the difference is that it reports the number of points that
     * actually moved instead of a boolean that was true for a short move.
     */
    public static final class LpAccess extends PlayerBoundAccess {

        LpAccess(Function<UUID, Player> resolver) {
            super(resolver, ResourceUnit.LP);
        }

        /** What this adapter knows about itself right now. */
        public CompatibilityStatus status() {
            return lpStatus();
        }

        @Override
        public double current(UUID playerId, ResourceUnit askedUnit) {
            Player player = player(playerId);
            if (player == null || !handles(askedUnit)) {
                return 0.0d;
            }
            if (SanctifiedLegacyCompat.isBloodMagicAvailable()) {
                return SanctifiedLegacyCompat.getBloodMagicLP(player);
            }
            // The health fallback's spendable balance, in the same 100 LP = 10 HP units the
            // cost calculator uses.
            return Math.max(0.0f, player.getHealth() - 1.0f) * 10.0d;
        }

        @Override
        public double max(UUID playerId, ResourceUnit askedUnit) {
            Player player = player(playerId);
            if (player == null || !handles(askedUnit)) {
                return 0.0d;
            }
            if (SanctifiedLegacyCompat.isBloodMagicAvailable()) {
                // Blood Magic exposes no per-player ceiling on this reflection surface, so the
                // current balance is the only honest answer.
                return SanctifiedLegacyCompat.getBloodMagicLP(player);
            }
            return Math.max(0.0f, player.getMaxHealth() - 1.0f) * 10.0d;
        }

        @Override
        public double debit(UUID playerId, ResourceUnit askedUnit, double amount) {
            Player player = player(playerId);
            if (player == null || !handles(askedUnit) || amount <= 0.0d) {
                return 0.0d;
            }
            return SanctifiedLegacyCompat.debitLP(player, (int) Math.ceil(amount));
        }

        @Override
        public double credit(UUID playerId, ResourceUnit askedUnit, double amount) {
            Player player = player(playerId);
            if (player == null || !handles(askedUnit) || amount <= 0.0d) {
                return 0.0d;
            }
            return SanctifiedLegacyCompat.creditLP(player, (int) Math.floor(amount));
        }
    }

    /**
     * Covenant aura, as spent by the Ring of the Seven Virtues.
     *
     * <p>Covenant 2.2.6 has no per-player aura API at all - what it calls aura is a sample of
     * the world's ambient Nature's Aura within a fixed radius of the player. Reads and writes
     * therefore go to Nature's Aura at the same radius Covenant samples at, which is what makes
     * the green HUD bar agree with what was charged. A drain that comes up short reports the
     * short number, and it is the payment policy - not this class - that decides what that
     * means for the cast.
     */
    public static final class AuraAccess extends PlayerBoundAccess {

        AuraAccess(Function<UUID, Player> resolver) {
            super(resolver, ResourceUnit.AURA);
        }

        /** What this adapter knows about itself right now. */
        public CompatibilityStatus status() {
            return auraStatus();
        }

        @Override
        public double current(UUID playerId, ResourceUnit askedUnit) {
            Player player = player(playerId);
            if (player == null || !handles(askedUnit)) {
                return 0.0d;
            }
            return SanctifiedLegacyCompat.getCovenantAura(player);
        }

        @Override
        public double max(UUID playerId, ResourceUnit askedUnit) {
            // Ambient aura has no ceiling this bridge can read. Reporting the current sample
            // is the only claim that is true.
            return current(playerId, askedUnit);
        }

        @Override
        public double debit(UUID playerId, ResourceUnit askedUnit, double amount) {
            Player player = player(playerId);
            if (player == null || !handles(askedUnit) || amount <= 0.0d) {
                return 0.0d;
            }
            return SanctifiedLegacyCompat.debitCovenantAura(player, (int) Math.ceil(amount));
        }

        @Override
        public double credit(UUID playerId, ResourceUnit askedUnit, double amount) {
            Player player = player(playerId);
            if (player == null || !handles(askedUnit) || amount <= 0.0d) {
                return 0.0d;
            }
            return SanctifiedLegacyCompat.creditCovenantAura(player, (int) Math.floor(amount));
        }
    }
}
