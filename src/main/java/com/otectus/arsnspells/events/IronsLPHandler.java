package com.otectus.arsnspells.events;

import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Cursed Ring LP consumption for Iron's Spellbooks spells.
 * Mirrors Ars Nouveau Cursed Ring behavior across all modes.
 *
 * NOT @Mod.EventBusSubscriber — would auto-load this class (which imports Iron's
 * APIs) on Iron's-less servers and crash at classload. Registered as an instance
 * by ArsNSpells behind ModList.isLoaded("irons_spellbooks").
 */
public class IronsLPHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(IronsLPHandler.class);

    // Track LP costs for message display and post-cast consumption.
    // ConcurrentHashMap because event handlers can fire from network/tick threads.
    private static final Map<UUID, PendingIronsLP> pendingCosts = new ConcurrentHashMap<>();

    /**
     * Validate LP cost for Iron's spells.
     * This runs BEFORE the spell actually casts.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onIronsSpellPreCast(SpellPreCastEvent event) {
        Player player = event.getEntity();
        LOGGER.debug("[IronsLPHandler] PreCast event received from Iron's (player={}, spell={}, level={}, side={})",
            player == null ? "null" : player.getName().getString(),
            event.getSpellId(), event.getSpellLevel(),
            player == null ? "?" : (player.level().isClientSide() ? "CLIENT" : "SERVER"));

        if (player == null || player.level().isClientSide()) {
            return;
        }

        if (!SanctifiedLegacyCompat.isAvailable()) {
            LOGGER.debug("[IronsLPHandler] PreCast skip: Sanctified Legacy compat not available");
            return;
        }

        if (!AnsConfig.ENABLE_LP_SYSTEM.get()) {
            LOGGER.debug("[IronsLPHandler] PreCast skip: enable_lp_system=false");
            return;
        }

        if (!SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            // Don't log per-cast for non-ring wearers — too noisy.
            return;
        }

        if (player.isCreative()) {
            LOGGER.debug("[IronsLPHandler] PreCast skip: player {} is creative", player.getName().getString());
            return;
        }

        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        if (spell == null) {
            LOGGER.warn("[IronsLPHandler] Spell not found in registry: {}", event.getSpellId());
            return;
        }

        int spellLevel = event.getSpellLevel();
        int manaCost = spell.getManaCost(spellLevel);
        if (manaCost <= 0) {
            LOGGER.debug("[IronsLPHandler] PreCast: zero-cost spell {} — skipping LP charge", event.getSpellId());
            return;
        }

        SpellRarity rarity = spell.getRarity(spellLevel);
        if (rarity == null) {
            LOGGER.warn("[IronsLPHandler] Null rarity for spell {} level {} - skipping LP cost",
                event.getSpellId(), spellLevel);
            return;
        }
        int lpCost = SanctifiedLegacyCompat.calculateIronsLPCost(manaCost, spellLevel, rarity.name());

        boolean hasEnough = SanctifiedLegacyCompat.hasEnoughLP(player, lpCost);

        LOGGER.debug("[IronsLPHandler] PreCast fired: player={}, spell={}, level={}, rarity={}, mana={}, lpCost={}, sufficient={}, deathMode={}",
            player.getName().getString(), event.getSpellId(), spellLevel, rarity.name(),
            manaCost, lpCost, hasEnough, AnsConfig.DEATH_ON_INSUFFICIENT_LP.get());

        if (!hasEnough) {
            if (AnsConfig.DEATH_ON_INSUFFICIENT_LP.get()) {
                // Allow cast; death penalty handled on cast
                pendingCosts.put(player.getUUID(), new PendingIronsLP(lpCost, manaCost, System.currentTimeMillis()));
                LPDeathPrevention.markSpellCast(player);
                return;
            }

            // Safe mode: Set immune flag to block residual damage from native Cursed Ring
            LPDeathPrevention.setLPImmune(player);
            LOGGER.warn("Insufficient LP - cancelling spell");
            event.setCanceled(true);
            pendingCosts.remove(player.getUUID());

            // Apply minor health penalty silently (bypasses damage events entirely)
            SanctifiedLegacyCompat.applySilentHealthLoss(player, 2.0f);

            if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                player.displayClientMessage(
                    Component.literal(ChatFormatting.RED + "Insufficient LP - Spell Cancelled"),
                    true
                );
            }

            LPDeathPrevention.clearLPImmune(player);
            return;
        }

        pendingCosts.put(player.getUUID(), new PendingIronsLP(lpCost, manaCost, System.currentTimeMillis()));
        LPDeathPrevention.markSpellCast(player);
    }

    /**
     * Consume LP when the spell actually casts.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onIronsSpellCast(SpellOnCastEvent event) {
        Player player = event.getEntity();
        int manaCostBefore = event.getManaCost();
        LOGGER.debug("[IronsLPHandler] OnCast event received from Iron's (player={}, spell={}, manaCost={})",
            player == null ? "null" : player.getName().getString(),
            event.getSpellId(), manaCostBefore);

        if (player == null || player.level().isClientSide()) {
            return;
        }

        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }

        if (!AnsConfig.ENABLE_LP_SYSTEM.get()) {
            return;
        }

        if (!SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            return;
        }

        PendingIronsLP pending = pendingCosts.remove(player.getUUID());
        if (pending == null) {
            LOGGER.debug("[IronsLPHandler] OnCast: no pending LP cost staged for {} (PreCast may have skipped)",
                player.getName().getString());
            return;
        }

        if (System.currentTimeMillis() - pending.timestamp > 5000) {
            LOGGER.warn("Pending LP cost expired for {}", player.getName().getString());
            return;
        }

        // Prevent Iron's mana consumption when using Cursed Ring
        event.setManaCost(0);

        boolean success = SanctifiedLegacyCompat.consumeLP(player, pending.lpCost);
        LOGGER.debug("[IronsLPHandler] OnCast fired: player={}, spell={}, pending={}, consumed={}, manaCostBefore={}, manaCostAfter=0",
            player.getName().getString(), event.getSpellId(), pending.lpCost, success, manaCostBefore);
        if (!success) {
            LOGGER.warn("[IronsLPHandler] LP consumption failed for {}", player.getName().getString());

            if (AnsConfig.DEATH_ON_INSUFFICIENT_LP.get()) {
                LOGGER.warn("Death penalty enabled - player will die but spell will cast");
                player.hurt(player.damageSources().magic(), Float.MAX_VALUE);
                if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                    player.displayClientMessage(
                        Component.literal(
                            ChatFormatting.DARK_RED.toString() + ChatFormatting.BOLD
                                + "DEATH: Insufficient LP (" + pending.lpCost + " LP required)"
                        ),
                        true
                    );
                }
                return;
            }

            // Safe mode: Set immune flag to block residual damage from native Cursed Ring
            LPDeathPrevention.setLPImmune(player);
            event.setCanceled(true);
            SanctifiedLegacyCompat.applySilentHealthLoss(player, 2.0f);
            if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                player.displayClientMessage(
                    Component.literal(ChatFormatting.RED + "Insufficient LP - Spell Cancelled"),
                    true
                );
            }

            // Clear immune flag next tick
            LPDeathPrevention.clearLPImmune(player);
            return;
        }

        if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
            player.displayClientMessage(
                Component.literal(ChatFormatting.GOLD + "Consumed " + pending.lpCost + " LP"),
                true
            );
        }
        LOGGER.debug("Iron's spell cast - {} LP consumed", pending.lpCost);
    }

    /**
     * Clean up expired pending costs.
     */
    @SubscribeEvent
    public void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }

        if (event.player.tickCount % 100 == 0) { // Every 5 seconds
            long now = System.currentTimeMillis();
            pendingCosts.entrySet().removeIf(entry -> now - entry.getValue().timestamp > 5000);
        }
    }

    /**
     * Evict per-player state on disconnect.
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        pendingCosts.remove(event.getEntity().getUUID());
    }

    /**
     * Store a pending LP cost for scroll-based casting.
     * Called by MixinScrollItem to integrate with the LP tracking system.
     */
    public static void storePendingScrollLP(Player player, int lpCost, int manaCost) {
        if (player != null) {
            pendingCosts.put(player.getUUID(), new PendingIronsLP(lpCost, manaCost, System.currentTimeMillis()));
        }
    }

    /**
     * Container for pending Iron's spell LP costs.
     */
    private static class PendingIronsLP {
        final int lpCost;
        final int manaCost;
        final long timestamp;

        PendingIronsLP(int lpCost, int manaCost, long timestamp) {
            this.lpCost = lpCost;
            this.manaCost = manaCost;
            this.timestamp = timestamp;
        }
    }
}
