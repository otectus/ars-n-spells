package com.otectus.arsnspells.events;

import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
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
import java.util.Set;
import java.util.UUID;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents death from insufficient LP when death penalty is disabled.
 * Uses a flag-based immunity system to ensure damage is fully blocked at
 * the LivingHurtEvent stage, preventing LivingDeathEvent from firing.
 * This avoids race conditions with mods like Corpse that process inventory
 * on death events.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class LPDeathPrevention {
    private static final Logger LOGGER = LoggerFactory.getLogger(LPDeathPrevention.class);

    // Flag-based immunity: set BEFORE any LP-related damage can occur
    private static final Set<UUID> lpDamageImmune = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // Safety timeout: tracks when each flag was set for cleanup
    private static final Map<UUID, Long> immuneTimestamps = new ConcurrentHashMap<>();
    private static final long IMMUNE_TIMEOUT_MS = 3000; // 3 second safety timeout

    /**
     * Mark a player as immune to LP-related damage.
     * Call this BEFORE any LP consumption or damage can occur.
     */
    public static void setLPImmune(Player player) {
        if (player != null) {
            lpDamageImmune.add(player.getUUID());
            immuneTimestamps.put(player.getUUID(), System.currentTimeMillis());
            LOGGER.debug("Set LP immune for {}", player.getName().getString());
        }
    }

    /**
     * Clear LP damage immunity for a player.
     * Call this after LP processing is complete (typically next tick).
     */
    public static void clearLPImmune(Player player) {
        if (player != null) {
            lpDamageImmune.remove(player.getUUID());
            immuneTimestamps.remove(player.getUUID());
            LOGGER.debug("Cleared LP immune for {}", player.getName().getString());
        }
    }

    /**
     * Check if a player is currently immune to LP-related damage.
     */
    public static boolean isLPImmune(Player player) {
        return player != null && lpDamageImmune.contains(player.getUUID());
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
     * This runs at HIGHEST priority to catch damage before it's applied.
     * By canceling damage here, LivingDeathEvent never fires, which prevents
     * Corpse mod from creating a corpse entity and moving inventory.
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

        // Check the deterministic immune flag instead of timestamp window
        if (!isLPImmune(player)) {
            return;
        }

        // Player is LP-immune: cancel all magic/sacrifice damage unconditionally
        String damageType = event.getSource().getMsgId();
        if (damageType.contains("magic") ||
            damageType.contains("indirectMagic") ||
            damageType.contains("sacrifice")) {

            LOGGER.debug("Canceling LP-related damage for {} (type: {}, amount: {})",
                player.getName().getString(), damageType, event.getAmount());
            event.setCanceled(true);
        }
    }

    /**
     * Safety net: intercept death events if damage interception somehow fails.
     * With the flag-based system, this should rarely fire since damage is blocked
     * at the LivingHurtEvent stage.
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

        if (!isLPImmune(player)) {
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
     * Safety cleanup: remove immune flags that were not cleared properly.
     * This prevents permanent immunity if something goes wrong.
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }

        if (event.player.tickCount % 60 == 0) { // Every 3 seconds
            long now = System.currentTimeMillis();
            immuneTimestamps.entrySet().removeIf(entry -> {
                if (now - entry.getValue() > IMMUNE_TIMEOUT_MS) {
                    lpDamageImmune.remove(entry.getKey());
                    LOGGER.debug("Safety cleanup: removed stale LP immune flag for {}", entry.getKey());
                    return true;
                }
                return false;
            });
        }
    }
}
