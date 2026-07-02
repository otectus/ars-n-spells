package com.otectus.arsnspells.events;

import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents death from insufficient LP when death penalty is disabled.
 * Uses a scoped cast transaction to ensure only LP-related damage from the
 * current spell cast is blocked, not unrelated magic damage from other sources.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class LPDeathPrevention {
    private static final Logger LOGGER = LoggerFactory.getLogger(LPDeathPrevention.class);

    private static final Map<UUID, CastTransaction> activeTransactions = new ConcurrentHashMap<>();
    private static final long IMMUNE_TIMEOUT_MS = 1000; // 1 second safety timeout

    private static class CastTransaction {
        final long timestampMs;
        final int playerTickCount;

        CastTransaction(long timestampMs, int playerTickCount) {
            this.timestampMs = timestampMs;
            this.playerTickCount = playerTickCount;
        }
    }

    /**
     * Mark a player as immune to LP-related damage for the current tick only.
     * Call this BEFORE any LP consumption or damage can occur.
     */
    public static void setLPImmune(Player player) {
        if (player != null) {
            activeTransactions.put(player.getUUID(),
                new CastTransaction(System.currentTimeMillis(), player.tickCount));
            LOGGER.debug("Set LP immune for {} at tick {}", player.getName().getString(), player.tickCount);
        }
    }

    /**
     * Clear LP damage immunity for a player.
     * Call this immediately after LP processing is complete.
     */
    public static void clearLPImmune(Player player) {
        if (player != null) {
            activeTransactions.remove(player.getUUID());
            LOGGER.debug("Cleared LP immune for {}", player.getName().getString());
        }
    }

    /**
     * Check if a player is currently immune to LP-related damage.
     */
    public static boolean isLPImmune(Player player) {
        return player != null && activeTransactions.containsKey(player.getUUID());
    }

    /**
     * Mark when a player casts a spell with Cursed Ring.
     * Backward-compatible wrapper that sets LP immunity.
     */
    public static void markSpellCast(Player player) {
        setLPImmune(player);
    }

    /**
     * Intercept damage events to prevent death from insufficient LP.
     * Only blocks damage that occurs in the same tick as the LP cast transaction.
     *
     * <p>ANS-HIGH-022: damage-type filter simplified. The previous filter checked
     * {@code contains("magic") || contains("indirectMagic") || contains("sacrifice")} —
     * "indirectMagic" was dead (already matched by "magic") and the explicit list
     * missed mod-added magic damage types (Mahou Tsukai "witherCurse",
     * Apotheosis "magicArrow"), defeating the safe-mode design. The same-tick scope
     * ({@code player.tickCount != transaction.playerTickCount}) is the real guard;
     * trusting it lets us block any damage that lands in the cast tick, regardless
     * of the source's msgId.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!AnsConfig.ENABLE_LP_SYSTEM.get()) {
            return;
        }

        // Only intercept if death penalty is disabled (safe mode)
        if (AnsConfig.DEATH_ON_INSUFFICIENT_LP.get()) {
            return;
        }

        CastTransaction transaction = activeTransactions.get(player.getUUID());
        if (transaction == null) {
            return;
        }

        // Only block damage that occurs in the same tick as the cast transaction.
        // This is the real safety guard — same-tick scope means any damage landing
        // here is a downstream effect of the cast we just intercepted.
        if (player.tickCount != transaction.playerTickCount) {
            return;
        }

        LOGGER.debug("Canceling same-tick damage for {} (type: {}, amount: {}, tick: {})",
            player.getName().getString(), event.getSource().getMsgId(), event.getAmount(),
            player.tickCount);
        event.setCanceled(true);
    }

    /**
     * Safety net: intercept death events if damage interception somehow fails.
     *
     * <p>ANS-HIGH-028: scoped to the cast tick, like {@link #onPlayerHurt}. The
     * immune flag is not cleared on successful casts (only the 1s TTL sweep or
     * logout removes it), so gating on {@code isLPImmune} alone cancelled ANY
     * lethal magic/sacrifice damage for up to ~3s after every Cursed-Ring cast —
     * a repeatable post-cast invulnerability exploit in safe mode. The same-tick
     * check restricts the net to deaths actually caused by the intercepted cast.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!AnsConfig.ENABLE_LP_SYSTEM.get()) {
            return;
        }

        if (AnsConfig.DEATH_ON_INSUFFICIENT_LP.get()) {
            return;
        }

        CastTransaction transaction = activeTransactions.get(player.getUUID());
        if (transaction == null || player.tickCount != transaction.playerTickCount) {
            return;
        }

        String deathType = event.getSource().getMsgId();
        if (deathType.contains("sacrifice") || deathType.contains("magic")) {
            LOGGER.warn("Safety net: Preventing LP-related death for {} (damage should have been blocked at hurt stage)",
                player.getName().getString());
            event.setCanceled(true);
            player.setHealth(2.0f);
            clearLPImmune(player);

            if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                player.displayClientMessage(
                    Component.literal("\u00a7cInsufficient LP - Spell Cancelled"),
                    true
                );
            }
        }
    }

    /**
     * ANS-HIGH-021: evict per-player state on disconnect so a stale immune flag
     * does not survive until another player's periodic sweep would catch it.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            activeTransactions.remove(event.getEntity().getUUID());
        }
    }

    /**
     * Safety cleanup: remove stale transaction flags.
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }

        if (event.player.tickCount % 60 == 0) {
            long now = System.currentTimeMillis();
            activeTransactions.entrySet().removeIf(entry -> {
                if (now - entry.getValue().timestampMs > IMMUNE_TIMEOUT_MS) {
                    LOGGER.debug("Safety cleanup: removed stale LP immune flag for {}", entry.getKey());
                    return true;
                }
                return false;
            });
        }
    }
}
