package com.otectus.arsnspells.events;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles equipment change events to update unified mana bonuses
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class EquipmentHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EquipmentHandler.class);
    
    /**
     * Handle equipment changes
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        
        Player player = (Player) event.getEntity();
        
        DIRTY.add(player.getUUID());
        // Clear equipment cache to force recalculation
        EquipmentIntegration.clearCache(player);
        
        // Recalculate max mana based on new equipment
        updatePlayerMaxMana(player);
        
        logDebug("Equipment changed for {}, recalculating mana bonuses", player.getName().getString());
    }
    
    /**
     * Handle player login to initialize equipment bonuses
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        
        Player player = event.getEntity();
        
        // Initialize equipment bonuses
        updatePlayerMaxMana(player);
        
        logDebug("Player {} logged in, initializing equipment bonuses", player.getName().getString());
    }
    
    /**
     * Handle player respawn to restore equipment bonuses
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        
        Player player = event.getEntity();
        
        // Clear cache and recalculate
        EquipmentIntegration.clearCache(player);
        updatePlayerMaxMana(player);
        
        logDebug("Player {} respawned, recalculating equipment bonuses", player.getName().getString());
    }

    /**
     * Handle a dimension change.
     *
     * <p>The Ars-to-Iron's mana modifiers are <em>transient</em>, and travelling between
     * dimensions rebuilds the {@code ServerPlayer} — attribute map included — so they are
     * silently dropped. Nothing else re-applied them until the next equipment change,
     * leaving the Iron's mana ceiling at its bare base value while the pool still held its
     * pre-portal amount. Since Iron's clamps every mana write down to that ceiling, the
     * surplus was destroyed by the next write, typically the first spell cast on the far
     * side. Every other lifecycle handler in the mod already listens for this event.
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {

        Player player = event.getEntity();
        EquipmentIntegration.clearCache(player);
        updatePlayerMaxMana(player);

        logDebug("Player {} changed dimension, re-applying mana bonuses",
            player.getName().getString());
    }

    /**
     * Handle player logout to clear cached equipment data
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EquipmentIntegration.clearCache(event.getEntity());
        DIRTY.remove(event.getEntity().getUUID());
    }

    private static final java.util.Set<java.util.UUID> DIRTY = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onCurioChange(top.theillusivec4.curios.api.event.CurioChangeEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            EquipmentIntegration.clearCache(player);
            com.otectus.arsnspells.compat.SanctifiedLegacyCompat.clearCacheFor(player.getUUID());
            DIRTY.add(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || event.player.level().isClientSide()) return;
        Player player = event.player;
        // Curios changes settle before END; the one-second reconciliation also observes
        // effect, perk, learned-glyph, book-tier and dynamic attribute changes.
        if (DIRTY.remove(player.getUUID()) || player.tickCount % 20 == 0) {
            EquipmentIntegration.clearCache(player);
            updatePlayerMaxMana(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        DIRTY.clear();
        EquipmentIntegration.clearAllCaches();
    }
    /**
     * Re-apply whatever mana contributions the current mode and config actually enable.
     *
     * <p>V14: the reconciler in {@code ModeChangeCleanup} needs step 2 of its sequence, and
     * step 2 is exactly what this handler already does on an equipment change. Exposed rather
     * than duplicated so a mode switch and a helmet swap cannot drift apart.
     */
    public static void recomputeContributions(Player player) {
        if (player == null) {
            return;
        }
        updatePlayerMaxMana(player);
    }

    /**
     * Update player's max mana based on equipment
     */
    private static void updatePlayerMaxMana(Player player) {
        try {
            if (player.level().isClientSide()) {
                return;
            }

            ManaUnificationMode mode = BridgeManager.getCurrentMode();
            if (!BridgeManager.isUnificationEnabled() || mode == null) {
                EquipmentIntegration.clearArsBonusesFromIrons(player);
                return;
            }

            // HYBRID is exempt from respect_armor_bonuses on purpose. That toggle governs
            // whether Ars *gear* bonuses are carried across; in HYBRID the Iron's MAX_MANA
            // attribute is not a bonus but the ceiling every mana write is clamped to, so
            // leaving it below Ars's real max does not withhold a bonus — it deletes mana
            // the player already has. See EquipmentIntegration.syncIronsMaxToArs.
            if (!AnsConfig.respectArmorBonuses.get() && !mode.isHybrid()) {
                EquipmentIntegration.clearArsBonusesFromIrons(player);
                return;
            }

            if (mode.isArsPrimary() || mode.isHybrid()) {
                // One ceiling for the shared pool: drive Iron's MAX_MANA to cover Ars's real
                // max (base + glyph bonus + book tier + perks), which is what the Ars side
                // reports and what the player sees. HYBRID previously received only the
                // gear-derived slice, so a spell book or glyph bonus raised the displayed
                // pool without raising the ceiling that governs writes — and Iron's clamps
                // every write down to that ceiling, so the surplus vanished on the next cast.
                float arsMax = EquipmentIntegration.arsRealMaxMana(player);
                EquipmentIntegration.syncIronsMaxToArs(player, arsMax);
                if (mode.isHybrid()) {
                    if (AnsConfig.respectArmorBonuses.get()) {
                        // Ars's real max already includes its gear bonuses, so only the regen
                        // half of applyArsBonusesToIrons is still wanted here.
                        EquipmentIntegration.applyArsRegenBonusToIrons(
                            player, AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get());
                    } else {
                        EquipmentIntegration.clearArsRegenBonusFromIrons(player);
                    }
                }
                logDebug("Synced Iron's max mana to Ars max for {} (mode {}): arsMax={}",
                    player.getName().getString(), mode, arsMax);
            } else if (mode.isIssPrimary()) {
                double conversionRate = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
                EquipmentIntegration.applyArsBonusesToIrons(player, conversionRate);
                EquipmentIntegration.ManaBonus arsBonus = EquipmentIntegration.getArsManaBonuses(player);
                logDebug("Applied Ars gear bonuses to Iron's mana for {}: max={}, regen={}",
                    player.getName().getString(), arsBonus.maxMana, arsBonus.manaRegen);
            } else {
                EquipmentIntegration.clearArsBonusesFromIrons(player);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to update max mana for player {}", player.getName().getString(), e);
        }
    }
    
    /**
     * Log debug message if debug mode is enabled
     */
    private static void logDebug(String message, Object... args) {
        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[EquipmentHandler] [DEBUG] " + message, args);
        }
    }
}
