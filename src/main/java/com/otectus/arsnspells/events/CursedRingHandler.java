package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.event.SpellResolveEvent;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Cursed Ring LP consumption for Ars Nouveau spells.
 * Uses event-based approach instead of mixins for better compatibility.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class CursedRingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CursedRingHandler.class);
    
    // Track which spells have had LP consumed (to prevent double-consumption)
    private static final Map<UUID, PendingLPCost> pendingCosts = new HashMap<>();
    
    /**
     * Calculate and validate LP cost when Cursed Ring is equipped.
     * This runs BEFORE the spell resolves.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpellCostCalc(SpellCostCalcEvent event) {
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }

        LivingEntity caster = event.context != null ? event.context.getUnwrappedCaster() : null;
        if (!(caster instanceof Player player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        // Check if wearing Cursed Ring - handle it regardless of mana unification setting
        // This prevents Sanctified Legacy's native (buggy) handling from interfering
        if (!SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            return;
        }

        
        LOGGER.debug("Cursed Ring detected on {} - Spell will use LP instead of mana",
            player.getName().getString());
        
        int manaCost = event.currentCost;
        if (manaCost <= 0) {
            LOGGER.debug("Zero cost spell - allowing");
            return;
        }

        // Get spell part for tier calculation from CasterContext
        AbstractSpellPart spellPart = com.otectus.arsnspells.util.CasterContext.getSpell()
            .filter(spell -> spell.recipe != null && !spell.recipe.isEmpty())
            .map(spell -> spell.recipe.get(0))
            .orElse(null);
        
        // Calculate LP cost
        int lpCost = SanctifiedLegacyCompat.calculateLPCost(manaCost, spellPart);
        
        // Apply Blasphemy multiplier
        String spellSchool = SanctifiedLegacyCompat.determineSpellSchool(spellPart);
        double blasphemyMultiplier = SanctifiedLegacyCompat.getBlasphemyMultiplier(player, spellSchool);
        if (blasphemyMultiplier < 1.0) {
            int originalCost = lpCost;
            lpCost = (int) Math.max(100, Math.round(lpCost * blasphemyMultiplier));
            LOGGER.debug("Blasphemy discount applied: {} LP -> {} LP", originalCost, lpCost);
        }
        
        LOGGER.debug("Spell will cost {} LP (base mana: {})", lpCost, manaCost);
        
        // Store the LP cost for consumption in the resolve event
        pendingCosts.put(player.getUUID(), new PendingLPCost(lpCost, System.currentTimeMillis()));
        
        // Set mana cost to 0 so Ars Nouveau doesn't consume mana
        event.currentCost = 0;
        
        LOGGER.debug("Mana cost set to 0 (LP will be consumed on spell resolve)");
    }

    /**
     * Get the pending LP cost for a player if it is still valid.
     * Returns -1 if no pending cost is available.
     */
    public static int getPendingLPCost(Player player) {
        if (player == null) {
            return -1;
        }

        PendingLPCost pending = pendingCosts.get(player.getUUID());
        if (pending == null) {
            return -1;
        }

        if (System.currentTimeMillis() - pending.timestamp > 5000) {
            pendingCosts.remove(player.getUUID());
            return -1;
        }

        return pending.lpCost;
    }

    /**
     * Clear any pending LP cost for the player.
     */
    public static void clearPendingLPCost(Player player) {
        if (player != null) {
            pendingCosts.remove(player.getUUID());
        }
    }
    
    /**
     * Consume LP when the spell actually resolves.
     * This runs AFTER cost calculation but BEFORE spell effects.
     * 
     * CRITICAL: We must consume LP but NOT cancel the event, otherwise spell effects won't apply.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpellResolve(SpellResolveEvent.Pre event) {
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }

        if (event.context == null) {
            return;
        }

        LivingEntity caster = event.context.getUnwrappedCaster();
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }
        
        // Check if we have a pending LP cost for this player
        PendingLPCost pending = pendingCosts.get(player.getUUID());
        if (pending == null) {
            return; // No LP cost pending (not wearing Cursed Ring)
        }
        
        // Check if the pending cost is still valid (within 5 seconds)
        if (System.currentTimeMillis() - pending.timestamp > 5000) {
            LOGGER.warn("   Pending LP cost expired for {}", player.getName().getString());
            pendingCosts.remove(player.getUUID());
            return;
        }
        
        // Only consume once per spell cast
        if (pending.consumed) {
            return;
        }
        
        LOGGER.debug("Consuming {} LP from {}'s Soul Network", pending.lpCost, player.getName().getString());
        
        // Attempt to consume LP
        boolean success = SanctifiedLegacyCompat.consumeLP(player, pending.lpCost);
        
        if (!success) {
            // LP consumption failed
            LOGGER.warn("   ❌ LP consumption failed");
            
            boolean deathPenalty = AnsConfig.DEATH_ON_INSUFFICIENT_LP.get();
            
            if (deathPenalty) {
                // Death penalty mode: Allow spell to cast but kill the player
                LOGGER.warn("   💀 Death penalty enabled - player will die but spell will cast");
                pending.consumed = true;
                
                // Kill the player
                player.hurt(player.damageSources().magic(), Float.MAX_VALUE);
                
                if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                    player.displayClientMessage(Component.literal("§4§lDEATH: Insufficient LP (" + pending.lpCost + " LP required)"), true);
                }
                
                // Don't cancel - let spell execute
            } else {
                // Safe mode: Cancel spell and apply minor damage
                LOGGER.warn("   🛡️ Safe mode - cancelling spell, applying minor damage");
                event.setCanceled(true);
                pendingCosts.remove(player.getUUID());
                
                // Apply minor health penalty silently (1 heart)
                SanctifiedLegacyCompat.applySilentHealthLoss(player, 2.0f);
                
                if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                    player.displayClientMessage(Component.literal("§cInsufficient LP - Spell Cancelled"), true);
                }
            }
        } else {
            LOGGER.debug("LP consumed successfully - spell will execute");
            pending.consumed = true;
            
            if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                player.displayClientMessage(Component.literal("§6Consumed " + pending.lpCost + " LP"), true);
            }
            // Don't remove from map yet - let it expire naturally to prevent double-consumption
        }
    }
    
    /**
     * Clean up expired pending costs periodically.
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        
        if (event.player.tickCount % 100 == 0) { // Every 5 seconds
            long now = System.currentTimeMillis();
            pendingCosts.entrySet().removeIf(entry -> now - entry.getValue().timestamp > 5000);
        }
    }
    
    /**
     * Container for pending LP costs.
     */
    private static class PendingLPCost {
        final int lpCost;
        final long timestamp;
        boolean consumed = false;
        
        PendingLPCost(int lpCost, long timestamp) {
            this.lpCost = lpCost;
            this.timestamp = timestamp;
        }
    }
}
