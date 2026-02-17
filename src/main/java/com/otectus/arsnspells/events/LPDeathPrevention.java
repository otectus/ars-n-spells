package com.otectus.arsnspells.events;

import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Prevents death from insufficient LP when death penalty is disabled.
 * Intercepts Sanctified Legacy's death penalty and applies our config settings.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class LPDeathPrevention {
    private static final Logger LOGGER = LoggerFactory.getLogger(LPDeathPrevention.class);
    
    // Track recent spell casts to detect LP-related damage
    private static final Map<UUID, Long> recentSpellCasts = new HashMap<>();
    private static final long DAMAGE_WINDOW_MS = 2000; // 2 second window after spell cast (increased for multiple death events)
    
    /**
     * Mark when a player casts a spell with Cursed Ring.
     */
    public static void markSpellCast(Player player) {
        if (player != null) {
            long timestamp = System.currentTimeMillis();
            recentSpellCasts.put(player.getUUID(), timestamp);
            LOGGER.info("🔖 Marked spell cast for {} at timestamp {}", player.getName().getString(), timestamp);
        }
    }
    
    /**
     * Intercept damage events to prevent death from insufficient LP.
     * This runs at HIGHEST priority to catch damage before it's applied.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        // Log ALL damage to this player for debugging
        LOGGER.info("🩸 Player damage event: {} damage from {}", 
            event.getAmount(), event.getSource().getMsgId());
        
        // Only intercept if death penalty is disabled
        if (AnsConfig.DEATH_ON_INSUFFICIENT_LP.get()) {
            LOGGER.info("   Death penalty enabled - allowing damage");
            return; // Death penalty enabled - allow death
        }
        
        // Check if wearing Cursed Ring
        if (!SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            LOGGER.info("   Not wearing Cursed Ring - ignoring");
            return;
        }
        
        LOGGER.info("   Wearing Cursed Ring - checking if LP-related damage");
        
        // Check if this damage is from LP penalty
        DamageSource source = event.getSource();
        String damageType = source.getMsgId();
        
        LOGGER.info("   Damage type: {}", damageType);
        
        // Check for LP-related damage types
        // Blood Magic uses "sacrifice" damage type for LP costs
        // Also check for magic damage as a fallback
        if (!damageType.contains("magic") && 
            !damageType.contains("indirectMagic") && 
            !damageType.contains("sacrifice")) {
            LOGGER.info("   Not LP-related damage (not magic or sacrifice) - ignoring");
            return;
        }
        
        LOGGER.info("   ✅ LP-related damage detected (type: {})", damageType);
        
        // Check if this is within the spell cast window
        Long lastCast = recentSpellCasts.get(player.getUUID());
        if (lastCast == null) {
            LOGGER.info("   No recent spell cast - ignoring");
            return;
        }
        
        long timeSinceCast = System.currentTimeMillis() - lastCast;
        LOGGER.info("   Time since spell cast: {}ms", timeSinceCast);
        
        if (timeSinceCast > DAMAGE_WINDOW_MS) {
            LOGGER.info("   Outside damage window - ignoring");
            return; // Too long ago - not related to spell cast
        }
        
        float damage = event.getAmount();
        float playerHealth = player.getHealth();

        LOGGER.info("🛡️ LP damage intercepted: {} damage, player health: {}", damage, playerHealth);

        // CANCEL ALL sacrifice/magic damage from native Cursed Ring
        // Our custom LP consumption already happened, so any additional damage is from
        // Enigmatic Legacy's native Cursed Ring system and should be completely blocked
        LOGGER.info("   ❌ Canceling native Cursed Ring damage (our custom LP already consumed)");
        event.setCanceled(true);

        LOGGER.info("   Damage event cancelled - player protected from native ring effects");
    }
    
    /**
     * Intercept death events to prevent LP-related deaths when death penalty is disabled.
     * This is the FINAL safety net - if damage interception fails, we prevent death here.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        LOGGER.info("💀 Player death event for {}", player.getName().getString());
        LOGGER.info("   Death source: {}", event.getSource().getMsgId());
        
        // Only intercept if death penalty is disabled
        if (AnsConfig.DEATH_ON_INSUFFICIENT_LP.get()) {
            LOGGER.info("   Death penalty enabled - allowing death");
            return;
        }
        
        // Check if wearing Cursed Ring
        if (!SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            LOGGER.info("   Not wearing Cursed Ring - allowing death");
            return;
        }
        
        LOGGER.info("   Wearing Cursed Ring - checking if LP-related death");
        
        // Check if this death is from sacrifice (LP cost)
        String deathType = event.getSource().getMsgId();
        if (!deathType.contains("sacrifice") && !deathType.contains("magic")) {
            LOGGER.info("   Not LP-related death (type: {}) - allowing", deathType);
            return;
        }
        
        // Check if this is within the spell cast window
        Long lastCast = recentSpellCasts.get(player.getUUID());
        long currentTime = System.currentTimeMillis();
        
        LOGGER.info("   Checking spell cast marker:");
        LOGGER.info("      Current time: {}", currentTime);
        LOGGER.info("      Last cast time: {}", lastCast);
        LOGGER.info("      Marker exists: {}", lastCast != null);
        
        if (lastCast == null) {
            LOGGER.warn("   ❌ No recent spell cast marker found - allowing death");
            LOGGER.warn("      This means markSpellCast() was never called or marker was cleared");
            return;
        }
        
        long timeSinceCast = currentTime - lastCast;
        LOGGER.info("      Time since cast: {}ms (window: {}ms)", timeSinceCast, DAMAGE_WINDOW_MS);
        
        if (timeSinceCast > DAMAGE_WINDOW_MS) {
            LOGGER.warn("   ❌ Outside spell cast window ({}ms > {}ms) - allowing death", timeSinceCast, DAMAGE_WINDOW_MS);
            return;
        }
        
        LOGGER.info("   ✅ Within spell cast window - will prevent death");
        
        // This is an LP-related death from insufficient LP - PREVENT IT
        LOGGER.warn("🛡️ PREVENTING LP-RELATED DEATH (safe mode active)");
        event.setCanceled(true);
        
        // Set player health to 1 heart to prevent death
        player.setHealth(2.0f);
        
        // Show message (only once)
        if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
            player.displayClientMessage(
                Component.literal("§cInsufficient LP - Spell Cancelled"), 
                true
            );
        }
        
        LOGGER.info("   Death cancelled - player health set to 2.0 (1 heart)");
        
        // DON'T remove the spell cast marker yet!
        // Blood Magic fires multiple death events, so we need to keep the marker
        // to intercept all of them. It will be cleaned up by the tick handler.
    }
    
    /**
     * Clean up old spell cast timestamps.
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        
        if (event.player.tickCount % 100 == 0) { // Every 5 seconds
            long now = System.currentTimeMillis();
            recentSpellCasts.entrySet().removeIf(entry -> now - entry.getValue() > DAMAGE_WINDOW_MS);
        }
    }
}
