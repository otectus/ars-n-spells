package com.otectus.arsnspells.casting;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central authority for spell casting validation.
 * Validates resource availability BEFORE spell execution.
 * Handles mana, health, aura, and other alternate resource costs.
 * Integrates with Sanctified Legacy for Cursed Ring / Virtue Ring support.
 */
public class CastingAuthority {
    private static final Logger LOGGER = LoggerFactory.getLogger(CastingAuthority.class);

    /**
     * Validate if a player can cast an Ars Nouveau spell.
     * This is the HARD GATE - if this returns false, the spell MUST NOT execute.
     * 
     * @param player The player attempting to cast
     * @param resolver The spell resolver containing cost information
     * @return true if the player has sufficient resources, false otherwise
     */
    public static boolean canCastArsSpell(Player player, SpellResolver resolver) {
        if (player == null || resolver == null) {
            logDebug("canCastArsSpell: player or resolver is null");
            return false;
        }

        // Creative mode bypass
        if (player.isCreative()) {
            logDebug("canCastArsSpell: Creative mode - allowing cast");
            return true;
        }

        // Get the spell cost
        int manaCost = resolver.getResolveCost();
        logDebug("canCastArsSpell: Spell cost = {} mana for player {}", manaCost, player.getName().getString());
        
        if (manaCost <= 0) {
            // Zero cost spells are always allowed
            logDebug("canCastArsSpell: Zero cost spell - allowing cast");
            return true;
        }

        // SANCTIFIED LEGACY INTEGRATION: Check for Cursed Ring
        if (SanctifiedLegacyCompat.isAvailable()) {
            boolean hasCursed = SanctifiedLegacyCompat.isWearingCursedRing(player);
            logDebug("canCastArsSpell: Cursed Ring check = {}", hasCursed);
            
            if (hasCursed) {
                // Cursed Ring replaces mana cost with Blood Magic LP
                LOGGER.debug("Cursed Ring detected - Using LP instead of mana for {}", player.getName().getString());
                return validateCursedRingCost(player, resolver, manaCost);
            }
        } else {
            logDebug("canCastArsSpell: Sanctified Legacy not available");
        }

        // SANCTIFIED LEGACY INTEGRATION: Virtue Ring is handled by VirtueRingHandler
        // (mana cost zeroed at SpellCostCalcEvent; aura consumed at SpellResolveEvent.Post).
        // The aura sufficiency check happens in MixinSpellResolverPreCast.

        // Alternate-resource rings (Cursed/Virtue) are handled by their dedicated event
        // handlers (CursedRingHandler / VirtueRingHandler), not here.
        // Standard mana validation
        logDebug("canCastArsSpell: Using standard mana validation");
        return validateManaResource(player, manaCost, true);
    }
    
    /**
     * Validate and consume LP cost for Cursed Ring users.
     * 
     * @param player The player
     * @param resolver The spell resolver
     * @param manaCost The base mana cost
     * @return true if LP was successfully consumed
     */
    private static boolean validateCursedRingCost(Player player, SpellResolver resolver, int manaCost) {
        LOGGER.debug("validateCursedRingCost called for player: {}, mana cost: {}",
            player.getName().getString(), manaCost);

        // Get first effect glyph for tier/rarity info
        com.otectus.arsnspells.util.SpellAnalysis.Result analysis =
            com.otectus.arsnspells.util.SpellAnalysis.analyze(resolver.spell);
        AbstractSpellPart spellPart = analysis.firstEffect();
        LOGGER.debug("Spell part: {}", spellPart != null ? spellPart.getRegistryName() : "none");

        // Calculate LP cost
        int lpCost = SanctifiedLegacyCompat.calculateLPCost(manaCost, spellPart);
        LOGGER.debug("Calculated LP cost: {} (base mana: {})", lpCost, manaCost);

        // Determine spell school for Blasphemy multiplier
        String spellSchool = analysis.dominantSchool();
        LOGGER.debug("Detected spell school: {}", spellSchool);

        // Apply Blasphemy multiplier if applicable
        double blasphemyMultiplier = SanctifiedLegacyCompat.getBlasphemyMultiplier(player, spellSchool);
        if (blasphemyMultiplier < 1.0) {
            int originalCost = lpCost;
            lpCost = (int) Math.max(100, Math.round(lpCost * blasphemyMultiplier));
            LOGGER.debug("Blasphemy multiplier applied: {} ({} LP -> {} LP)",
                blasphemyMultiplier, originalCost, lpCost);
        }

        LOGGER.debug("Final LP cost: {}", lpCost);

        // Check if player has enough LP (don't consume yet - event handlers will do that)
        boolean hasEnough = SanctifiedLegacyCompat.hasEnoughLP(player, lpCost);

        if (hasEnough) {
            LOGGER.debug("Player has sufficient LP");
        } else {
            LOGGER.debug("Insufficient LP");
            sendDenialMessage(player, "\u00a7cInsufficient Life Points (LP): Need " + lpCost + " LP");
        }

        return hasEnough;
    }

    /**
     * Validate if a player can cast an Iron's Spellbooks spell.
     * 
     * @param player The player attempting to cast
     * @param manaCost The mana cost of the spell
     * @return true if the player has sufficient resources, false otherwise
     */
    public static boolean canCastIronsSpell(Player player, int manaCost) {
        if (player == null) {
            return false;
        }

        // Creative mode bypass
        if (player.isCreative()) {
            return true;
        }

        if (manaCost <= 0) {
            return true;
        }

        return validateManaResource(player, manaCost, false);
    }

    /**
     * ANS-MED-043: consume the mana previously validated by
     * {@link #canCastIronsSpell}. Iron's scrolls never deduct mana natively, so
     * "full" scroll cost mode validated the cost and then charged nothing. The
     * conversion here mirrors {@link #validateManaResource} exactly so the
     * amount deducted equals the amount validated.
     */
    public static boolean consumeIronsSpellMana(Player player, int manaCost) {
        if (player == null) {
            return false;
        }
        if (player.isCreative() || manaCost <= 0) {
            return true;
        }
        float effectiveCost = manaCost;
        if (BridgeManager.isUnificationEnabled()) {
            effectiveCost = (float) (manaCost * AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get());
        }
        return BridgeManager.consumeManaForMode(player, effectiveCost, false);
    }

    /**
     * Validate mana resource availability.
     *
     * @param player The player
     * @param cost The mana cost
     * @param fromArs True if this is an Ars spell, false for Iron's
     * @return true if player has sufficient mana
     */
    private static boolean validateManaResource(Player player, int cost, boolean fromArs) {
        float availableMana;
        float effectiveCost = cost;

        if (!BridgeManager.isUnificationEnabled()) {
            // No unification - use native Ars mana for Ars spells
            if (fromArs) {
                availableMana = BridgeManager.getBridge().getMana(player);
            } else {
                // For Iron's spells without unification, use Iron's mana
                availableMana = BridgeManager.isIronsSpellbooksLoaded() ?
                    BridgeManager.getManaForMode(player, false) : 0;
            }
        } else {
            // Apply conversion rate if needed
            double conversionRate = fromArs ?
                AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get() :
                AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();

            effectiveCost = (float) (cost * conversionRate);
            availableMana = BridgeManager.getManaForMode(player, fromArs);
        }

        boolean canAfford = availableMana >= effectiveCost;

        if (!canAfford) {
            logDebug("Mana validation failed for {}: cost={}, available={}, fromArs={}",
                player.getName().getString(), effectiveCost, availableMana, fromArs);

            // Send denial message
            sendDenialMessage(player, "§cNot Enough Mana: Need " + (int)effectiveCost + ", have " + (int)availableMana);
        }

        return canAfford;
    }

    /**
     * Send a denial message to the player.
     * 
     * @param player The player
     * @param reason The reason for denial
     */
    public static void sendDenialMessage(Player player, String reason) {
        if (player != null && !player.level().isClientSide()) {
            player.displayClientMessage(Component.literal(reason), true);
        }
    }

    /**
     * Log debug message if debug mode is enabled.
     */
    private static void logDebug(String message, Object... args) {
        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[CastingAuthority] [DEBUG] " + message, args);
        }
    }

}
