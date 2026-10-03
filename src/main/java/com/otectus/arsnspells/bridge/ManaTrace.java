package com.otectus.arsnspells.bridge;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.LogPrivacy;
import com.otectus.arsnspells.util.LogThrottle;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;
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
        return AnsConfig.debugEnabled();
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
            var key = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
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

    /** Iron's regeneration proposed a lower routed balance than the pool holds; it was refused. */
    public static void regenClampRefused(Player player, double current, double proposed) {
        if (!enabled() || !LogThrottle.allow(player.getUUID(), WINDOW_MS)) return;
        LOG.info("[ManaTrace] player={} tick={} refused Iron's regeneration write {} below routed Ars balance {} (Iron's ceiling {})",
            LogPrivacy.token(player.getUUID()), player.level().getGameTime(), proposed, current,
            BridgeManager.getNativeIronsBridge() == null ? Double.NaN : BridgeManager.getNativeIronsBridge().getMaxMana(player));
    }
}
