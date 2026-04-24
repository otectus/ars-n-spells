package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.event.SpellResolveEvent;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.otectus.arsnspells.aura.AuraManager;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.CasterContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Virtue Ring aura consumption for Ars Nouveau spells.
 * When the Ring of Seven Virtues is equipped, mana costs are replaced with aura costs.
 * Mirrors the architecture of CursedRingHandler for LP.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class VirtueRingHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtueRingHandler.class);

    // ConcurrentHashMap because event handlers can fire from network/tick threads.
    private static final Map<UUID, PendingAuraCost> pendingCosts = new ConcurrentHashMap<>();

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

        // Get first effect glyph for tier calculation
        com.otectus.arsnspells.util.SpellAnalysis.Result analysis = CasterContext.getSpell()
            .map(com.otectus.arsnspells.util.SpellAnalysis::analyze)
            .orElse(null);
        AbstractSpellPart spellPart = analysis != null ? analysis.firstEffect() : null;

        // Calculate aura cost
        int auraCost = AuraManager.calculateAuraCost(manaCost, spellPart);

        // Apply Blasphemy discount to aura cost
        String spellSchool = analysis != null ? analysis.dominantSchool() : "generic";
        double blasphemyMultiplier = SanctifiedLegacyCompat.getBlasphemyAuraMultiplier(player, spellSchool);
        if (blasphemyMultiplier < 1.0) {
            int originalCost = auraCost;
            auraCost = (int) Math.max(AnsConfig.AURA_MINIMUM_COST.get(),
                Math.round(auraCost * blasphemyMultiplier));
            LOGGER.debug("Blasphemy discount applied to aura: {} -> {}", originalCost, auraCost);
        }

        LOGGER.debug("Spell will cost {} aura (base mana: {})", auraCost, manaCost);

        // Store the aura cost for consumption on spell resolve
        pendingCosts.put(player.getUUID(), new PendingAuraCost(auraCost, player.tickCount));

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
        if (player.tickCount - pending.tickStamp > PENDING_COST_TTL_TICKS) {
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

        if (player.tickCount - pending.tickStamp > PENDING_COST_TTL_TICKS) {
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
                    Component.translatable("message.ars_n_spells.aura.insufficient", pending.auraCost, currentAura)
                        .withStyle(ChatFormatting.AQUA),
                    true
                );
            }
        } else {
            pending.consumed = true;

            if (AnsConfig.SHOW_AURA_MESSAGES.get()) {
                int remaining = AuraManager.getAura(player);
                player.displayClientMessage(
                    Component.translatable("message.ars_n_spells.aura.consumed", pending.auraCost, remaining)
                        .withStyle(ChatFormatting.AQUA),
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
            int now = event.player.tickCount;
            pendingCosts.entrySet().removeIf(entry -> now - entry.getValue().tickStamp > PENDING_COST_TTL_TICKS);
        }
    }

    /**
     * Evict per-player state on disconnect.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        pendingCosts.remove(event.getEntity().getUUID());
    }

    private static final int PENDING_COST_TTL_TICKS = 100; // 5 seconds at 20 TPS

    private static class PendingAuraCost {
        final int auraCost;
        final int tickStamp;
        boolean consumed = false;

        PendingAuraCost(int auraCost, int tickStamp) {
            this.auraCost = auraCost;
            this.tickStamp = tickStamp;
        }
    }
}
