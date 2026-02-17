package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.event.SpellResolveEvent;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.otectus.arsnspells.aura.AuraManager;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.CasterContext;
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
 * Handles Virtue Ring aura consumption for Ars Nouveau spells.
 * When the Ring of Seven Virtues is equipped, mana costs are replaced with aura costs.
 * Mirrors the architecture of CursedRingHandler for LP.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class VirtueRingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtueRingHandler.class);

    private static final Map<UUID, PendingAuraCost> pendingCosts = new HashMap<>();

    /**
     * Calculate aura cost and replace mana cost when Virtue Ring is equipped.
     * Runs at HIGHEST priority to ensure it processes before other cost modifiers.
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

        if (!SanctifiedLegacyCompat.isWearingVirtueRing(player)) {
            return;
        }

        int manaCost = event.currentCost;
        if (manaCost <= 0) {
            return;
        }

        LOGGER.debug("Virtue Ring detected on {} - Spell will use Aura instead of mana",
            player.getName().getString());

        // Get spell part for tier calculation
        AbstractSpellPart spellPart = CasterContext.getSpell()
            .filter(spell -> spell.recipe != null && !spell.recipe.isEmpty())
            .map(spell -> spell.recipe.get(0))
            .orElse(null);

        // Calculate aura cost
        int auraCost = AuraManager.calculateAuraCost(manaCost, spellPart);

        // Apply Blasphemy discount to aura cost
        String spellSchool = SanctifiedLegacyCompat.determineSpellSchool(spellPart);
        double blasphemyMultiplier = SanctifiedLegacyCompat.getBlasphemyMultiplier(player, spellSchool);
        if (blasphemyMultiplier < 1.0) {
            int originalCost = auraCost;
            auraCost = (int) Math.max(AnsConfig.AURA_MINIMUM_COST.get(),
                Math.round(auraCost * blasphemyMultiplier));
            LOGGER.debug("Blasphemy discount applied to aura: {} -> {}", originalCost, auraCost);
        }

        LOGGER.debug("Spell will cost {} aura (base mana: {})", auraCost, manaCost);

        // Store the aura cost for consumption on spell resolve
        pendingCosts.put(player.getUUID(), new PendingAuraCost(auraCost, System.currentTimeMillis()));

        // Set mana cost to 0 so Ars Nouveau doesn't consume mana
        event.currentCost = 0;
    }

    /**
     * Get the pending aura cost for a player. Returns -1 if none.
     */
    public static int getPendingAuraCost(Player player) {
        if (player == null) {
            return -1;
        }
        PendingAuraCost pending = pendingCosts.get(player.getUUID());
        if (pending == null) {
            return -1;
        }
        if (System.currentTimeMillis() - pending.timestamp > 5000) {
            pendingCosts.remove(player.getUUID());
            return -1;
        }
        return pending.auraCost;
    }

    /**
     * Clear any pending aura cost for the player.
     */
    public static void clearPendingAuraCost(Player player) {
        if (player != null) {
            pendingCosts.remove(player.getUUID());
        }
    }

    /**
     * Consume aura when the spell resolves.
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

        PendingAuraCost pending = pendingCosts.get(player.getUUID());
        if (pending == null) {
            return;
        }

        if (System.currentTimeMillis() - pending.timestamp > 5000) {
            LOGGER.warn("Pending aura cost expired for {}", player.getName().getString());
            pendingCosts.remove(player.getUUID());
            return;
        }

        if (pending.consumed) {
            return;
        }

        LOGGER.debug("Consuming {} aura from {}", pending.auraCost, player.getName().getString());

        boolean success = AuraManager.consumeAura(player, pending.auraCost);

        if (!success) {
            LOGGER.warn("Insufficient aura - cancelling spell");
            event.setCanceled(true);
            pendingCosts.remove(player.getUUID());

            if (AnsConfig.SHOW_AURA_MESSAGES.get()) {
                int currentAura = AuraManager.getAura(player);
                player.displayClientMessage(
                    Component.literal("\u00a7bInsufficient Aura: Need " + pending.auraCost
                        + ", have " + currentAura),
                    true
                );
            }
        } else {
            pending.consumed = true;

            if (AnsConfig.SHOW_AURA_MESSAGES.get()) {
                int remaining = AuraManager.getAura(player);
                player.displayClientMessage(
                    Component.literal("\u00a7bConsumed " + pending.auraCost + " Aura (" + remaining + " remaining)"),
                    true
                );
            }
        }
    }

    /**
     * Clean up expired pending costs.
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        if (event.player.tickCount % 100 == 0) {
            long now = System.currentTimeMillis();
            pendingCosts.entrySet().removeIf(entry -> now - entry.getValue().timestamp > 5000);
        }
    }

    private static class PendingAuraCost {
        final int auraCost;
        final long timestamp;
        boolean consumed = false;

        PendingAuraCost(int auraCost, long timestamp) {
            this.auraCost = auraCost;
            this.timestamp = timestamp;
        }
    }
}
