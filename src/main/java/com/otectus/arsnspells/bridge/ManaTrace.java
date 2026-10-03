package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.LogPrivacy;
import com.otectus.arsnspells.util.LogThrottle;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Debug-mode trace of the two native pools around equipment reconciliation.
 *
 * <p>Answers the question a "my mana bar dropped while scrolling" report needs answered: did the
 * authoritative server balance move, did only a maximum move, or did nothing move at all. Each
 * line carries both native pools, the Iron's ceiling and the held stack, and is written only
 * when {@code debug_mode} is on, only when something changed, and at most ten times per second
 * per player.
 */
public final class ManaTrace {
    private static final Logger LOG = LoggerFactory.getLogger("ArsNSpells/ManaTrace");
    private static final long WINDOW_MS = 100;

    private ManaTrace() {}

    public static boolean enabled() {
        try {
            return AnsConfig.DEBUG_MODE.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    /** Native balances read inside their own adapters, so no mode routing can disguise them. */
    public record Snapshot(double arsCurrent, double arsMax, double ironsCurrent, double ironsMax,
                           int slot, String held) {
        public static Snapshot of(Player player) {
            double arsCurrent = Double.NaN, arsMax = Double.NaN, ironsCurrent = Double.NaN, ironsMax = Double.NaN;
            try {
                arsCurrent = BridgeManager.getNativeArsBridge().transactionMana(player);
                arsMax = BridgeManager.getNativeArsBridge().transactionMax(player);
            } catch (RuntimeException unavailable) { /* reported as NaN */ }
            IManaBridge irons = BridgeManager.getNativeIronsBridge();
            if (irons != null) {
                try {
                    ironsCurrent = irons.transactionMana(player);
                    ironsMax = irons.transactionMax(player);
                } catch (RuntimeException unavailable) { /* reported as NaN */ }
            }
            var key = ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());
            return new Snapshot(arsCurrent, arsMax, ironsCurrent, ironsMax,
                player.getInventory().selected, key == null ? "?" : key.toString());
        }

        boolean sameAs(Snapshot other) {
            return same(arsCurrent, other.arsCurrent) && same(arsMax, other.arsMax)
                && same(ironsCurrent, other.ironsCurrent) && same(ironsMax, other.ironsMax)
                && slot == other.slot && held.equals(other.held);
        }

        private static boolean same(double a, double b) {
            return Double.compare(a, b) == 0 || Math.abs(a - b) < 1.0e-6;
        }
    }

    /** One line when equipment reconciliation changed either pool, its ceiling, or the held stack. */
    public static void reconciled(Player player, Snapshot before) {
        if (!enabled()) return;
        Snapshot after = Snapshot.of(player);
        if (after.sameAs(before) || !LogThrottle.allow(player.getUUID(), WINDOW_MS)) return;
        LOG.info("[ManaTrace] player={} tick={} mode={} slot={}->{} held={}->{} ars={}/{} -> {}/{} irons={}/{} -> {}/{}",
            LogPrivacy.token(player.getUUID()), player.level().getGameTime(), BridgeManager.getCurrentMode(),
            before.slot(), after.slot(), before.held(), after.held(),
            before.arsCurrent(), before.arsMax(), after.arsCurrent(), after.arsMax(),
            before.ironsCurrent(), before.ironsMax(), after.ironsCurrent(), after.ironsMax());
    }

    /** Players already reported by {@link #paidAboveCeiling} this server run. */
    private static final java.util.Set<java.util.UUID> REPORTED_ABOVE_CEILING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * A payment found its pool above that pool's own ceiling.
     *
     * <p>The payment still runs, as the native write it replaces would, but a ceiling that sits
     * under a full pool at the moment of casting means some bonus to it was missing then. The
     * first occurrence per player and server run is always logged, with the Iron's ceiling's
     * modifiers so a pack author can see which one was absent; debug mode logs every occurrence.
     * WARN when the clamp removed mana beyond the price, INFO when only the price moved.
     */
    public static void paidAboveCeiling(Player player, com.otectus.arsnspells.contract.ResourceUnit unit,
                                        double before, double ceiling, double price, double after) {
        // Bounded by casts, so debug mode needs no throttle; the shared LogThrottle would also
        // swallow the payment-failure warning that can follow within the same second.
        if (!REPORTED_ABOVE_CEILING.add(player.getUUID()) && !enabled()) return;
        if (REPORTED_ABOVE_CEILING.size() > 4096) REPORTED_ABOVE_CEILING.clear();
        boolean clamped = after < before - price - 1.0e-3;
        String modifiers = unit == com.otectus.arsnspells.contract.ResourceUnit.IRONS_MANA ? ironsCeilingModifiers(player) : "n/a";
        String message = "[ManaTrace] player={} tick={} mode={} paid {} {} while the pool ({}) was above its ceiling ({}); "
            + "balance after the native write: {}{}. max_mana modifiers: {}";
        Object[] args = {LogPrivacy.token(player.getUUID()), player.level().getGameTime(), BridgeManager.getCurrentMode(),
            price, unit, before, ceiling, after,
            clamped ? " (more than the price left the pool: the native ceiling clamp removed the surplus)" : "", modifiers};
        if (clamped) LOG.warn(message, args); else LOG.info(message, args);
    }

    private static String ironsCeilingModifiers(Player player) {
        try {
            var attribute = ForgeRegistries.ATTRIBUTES.getValue(new net.minecraft.resources.ResourceLocation("irons_spellbooks", "max_mana"));
            var instance = attribute == null ? null : player.getAttribute(attribute);
            if (instance == null) return "unavailable";
            StringBuilder out = new StringBuilder("base=").append(instance.getBaseValue());
            for (var modifier : instance.getModifiers())
                out.append(", ").append(modifier.getName()).append('=').append(modifier.getAmount()).append(' ').append(modifier.getOperation());
            return out.toString();
        } catch (RuntimeException unavailable) {
            return "unavailable";
        }
    }

    /** Server stop: the next world reports its first occurrence again. */
    public static void clearAll() {
        REPORTED_ABOVE_CEILING.clear();
    }

    /** Iron's regeneration proposed a lower routed balance than the pool holds; it was refused. */
    public static void regenClampRefused(Player player, double current, double proposed) {
        if (!enabled() || !LogThrottle.allow(player.getUUID(), WINDOW_MS)) return;
        LOG.info("[ManaTrace] player={} tick={} refused Iron's regeneration write {} below routed Ars balance {} (Iron's ceiling {})",
            LogPrivacy.token(player.getUUID()), player.level().getGameTime(), proposed, current,
            BridgeManager.getNativeIronsBridge() == null ? Double.NaN : BridgeManager.getNativeIronsBridge().getMaxMana(player));
    }
}
