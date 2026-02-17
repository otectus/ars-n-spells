package com.otectus.arsnspells.aura;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central utility for aura resource operations.
 * Provides cost calculation, validation, and consumption for the Virtue Ring aura system.
 */
public class AuraManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuraManager.class);

    /**
     * Calculate aura cost from a mana cost using configurable formula.
     * Formula: Aura = (ManaCost x BaseMultiplier) x TierMultiplier
     */
    public static int calculateAuraCost(int manaCost, AbstractSpellPart spellPart) {
        double baseMultiplier = AnsConfig.AURA_BASE_MULTIPLIER.get();
        double baseCost = manaCost * baseMultiplier;

        int tier = spellPart != null ? spellPart.getConfigTier().value : 1;
        double tierMultiplier;
        switch (tier) {
            case 1:
                tierMultiplier = AnsConfig.AURA_TIER1_MULTIPLIER.get();
                break;
            case 2:
                tierMultiplier = AnsConfig.AURA_TIER2_MULTIPLIER.get();
                break;
            case 3:
                tierMultiplier = AnsConfig.AURA_TIER3_MULTIPLIER.get();
                break;
            default:
                tierMultiplier = 1.0;
                break;
        }

        int finalCost = (int) Math.round(baseCost * tierMultiplier);
        int minimumCost = AnsConfig.AURA_MINIMUM_COST.get();
        return Math.max(minimumCost, finalCost);
    }

    /**
     * Check if a player has enough aura for a given cost.
     */
    public static boolean hasEnoughAura(Player player, int cost) {
        if (player == null || cost <= 0) {
            return true;
        }
        return player.getCapability(AuraCapabilityProvider.AURA_CAP)
            .map(cap -> cap.getAura() >= cost)
            .orElse(false);
    }

    /**
     * Consume aura from a player. Returns true if successful.
     */
    public static boolean consumeAura(Player player, int cost) {
        if (player == null || cost <= 0) {
            return true;
        }
        return player.getCapability(AuraCapabilityProvider.AURA_CAP)
            .map(cap -> {
                boolean success = cap.consumeAura(cost);
                if (success) {
                    LOGGER.debug("Consumed {} aura from {} ({} remaining)",
                        cost, player.getName().getString(), cap.getAura());
                }
                return success;
            })
            .orElse(false);
    }

    /**
     * Get the current aura of a player.
     */
    public static int getAura(Player player) {
        if (player == null) {
            return 0;
        }
        return player.getCapability(AuraCapabilityProvider.AURA_CAP)
            .map(IAuraCapability::getAura)
            .orElse(0);
    }

    /**
     * Get the max aura of a player.
     */
    public static int getMaxAura(Player player) {
        if (player == null) {
            return 0;
        }
        return player.getCapability(AuraCapabilityProvider.AURA_CAP)
            .map(IAuraCapability::getMaxAura)
            .orElse(0);
    }
}
