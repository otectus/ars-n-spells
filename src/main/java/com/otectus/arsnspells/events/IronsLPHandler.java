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
 * Handles Cursed Ring LP consumption for Iron's Spellbooks spells.
 * Mirrors Ars Nouveau Cursed Ring behavior across all modes.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class IronsLPHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(IronsLPHandler.class);

    // Track LP costs for message display and post-cast consumption
    private static final Map<UUID, PendingIronsLP> pendingCosts = new HashMap<>();

    /**
     * Validate LP cost for Iron's spells.
     * This runs BEFORE the spell actually casts.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIronsSpellPreCast(SpellPreCastEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) {
            return;
        }

        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }

        if (!SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            return;
        }

        if (player.isCreative()) {
            return;
        }

        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        if (spell == null) {
            LOGGER.warn("Spell not found in registry: {}", event.getSpellId());
            return;
        }

        int spellLevel = event.getSpellLevel();
        int manaCost = spell.getManaCost(spellLevel);
        if (manaCost <= 0) {
            return;
        }

        SpellRarity rarity = spell.getRarity(spellLevel);
        int lpCost = SanctifiedLegacyCompat.calculateIronsLPCost(manaCost, spellLevel, rarity.name());

        LOGGER.debug("Cursed Ring active for Iron's spell on {}", player.getName().getString());
        LOGGER.debug("Iron's spell: {}, Level: {}, Rarity: {}", event.getSpellId(), spellLevel, rarity.name());
        LOGGER.debug("Calculated LP cost: {} (base mana: {})", lpCost, manaCost);

        boolean hasEnough = SanctifiedLegacyCompat.hasEnoughLP(player, lpCost);
        if (!hasEnough) {
            if (AnsConfig.DEATH_ON_INSUFFICIENT_LP.get()) {
                // Allow cast; death penalty handled on cast
                pendingCosts.put(player.getUUID(), new PendingIronsLP(lpCost, manaCost, System.currentTimeMillis()));
                LPDeathPrevention.markSpellCast(player);
                return;
            }

            LOGGER.warn("Insufficient LP - cancelling spell");
            event.setCanceled(true);
            pendingCosts.remove(player.getUUID());

            // Apply minor health penalty silently (1 heart)
            SanctifiedLegacyCompat.applySilentHealthLoss(player, 2.0f);

            if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                player.displayClientMessage(
                    Component.literal(ChatFormatting.RED + "Insufficient LP - Spell Cancelled"),
                    true
                );
            }
            return;
        }

        pendingCosts.put(player.getUUID(), new PendingIronsLP(lpCost, manaCost, System.currentTimeMillis()));
        LPDeathPrevention.markSpellCast(player);
    }

    /**
     * Consume LP when the spell actually casts.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIronsSpellCast(SpellOnCastEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) {
            return;
        }

        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }

        if (!SanctifiedLegacyCompat.isWearingCursedRing(player)) {
            return;
        }

        PendingIronsLP pending = pendingCosts.remove(player.getUUID());
        if (pending == null) {
            return;
        }

        if (System.currentTimeMillis() - pending.timestamp > 5000) {
            LOGGER.warn("Pending LP cost expired for {}", player.getName().getString());
            return;
        }

        // Prevent Iron's mana consumption when using Cursed Ring
        event.setManaCost(0);

        boolean success = SanctifiedLegacyCompat.consumeLP(player, pending.lpCost);
        if (!success) {
            LOGGER.warn("LP consumption failed");

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

            // Safe mode: cancel if possible and apply silent penalty
            event.setCanceled(true);
            SanctifiedLegacyCompat.applySilentHealthLoss(player, 2.0f);
            if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                player.displayClientMessage(
                    Component.literal(ChatFormatting.RED + "Insufficient LP - Spell Cancelled"),
                    true
                );
            }
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
