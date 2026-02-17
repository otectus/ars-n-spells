package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.CasterContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles mana cost discounts from Covenant of the Seven curios.
 * Applies discounts from Ring of Virtue and Blasphemy curios to Ars Nouveau spells.
 * 
 * Priority: LOW - Applied after other cost modifiers to ensure proper stacking
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class CurioDiscountHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurioDiscountHandler.class);
    
    /**
     * Apply curio discounts to Ars Nouveau spell costs.
     * Uses LOW priority to apply discounts after other modifiers.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onSpellCostCalc(SpellCostCalcEvent event) {
        // Check if curio discounts are enabled
        if (!AnsConfig.ENABLE_CURIO_DISCOUNTS.get()) {
            return;
        }
        
        // Check if Sanctified Legacy is available
        if (!SanctifiedLegacyCompat.isAvailable()) {
            return;
        }
        
        // Only apply to player casters
        LivingEntity caster = event.context != null ? event.context.getUnwrappedCaster() : null;
        if (!(caster instanceof Player player)) {
            return;
        }
        
        // Skip if player is in creative mode
        if (player.isCreative()) {
            return;
        }
        
        // Get the current spell cost
        int currentCost = event.currentCost;
        if (currentCost <= 0) {
            return; // No cost to discount
        }
        
        // Calculate total discount multiplier
        double discountMultiplier = calculateDiscountMultiplier(player, event);
        
        // Apply discount if any
        if (discountMultiplier < 1.0) {
            int discountedCost = (int) Math.max(1, Math.round(currentCost * discountMultiplier));
            int savedMana = currentCost - discountedCost;
            
            event.currentCost = discountedCost;
            
            logDebug("Applied curio discount to {}: {} mana -> {} mana (saved {} mana, {:.1f}% discount)",
                player.getName().getString(), currentCost, discountedCost, savedMana, 
                (1.0 - discountMultiplier) * 100);
        }
    }
    
    /**
     * Calculate the total discount multiplier from all equipped curios.
     * 
     * @param player The player
     * @param event The spell cost event (for spell school detection)
     * @return Discount multiplier (1.0 = no discount, 0.5 = 50% discount)
     */
    private static double calculateDiscountMultiplier(Player player, SpellCostCalcEvent event) {
        double multiplier = 1.0;
        
        // Check for Ring of Virtue discount
        boolean hasVirtue = SanctifiedLegacyCompat.hasVirtueRing(player);
        if (hasVirtue) {
            double virtueDiscount = AnsConfig.VIRTUE_RING_DISCOUNT.get();
            multiplier *= (1.0 - virtueDiscount);
            
            logDebug("Ring of Virtue discount applied: {:.1f}%", virtueDiscount * 100);
        }
        
        // Check for Blasphemy discount
        String spellSchool = determineSpellSchool(event);
        BlasphemyDiscountResult blasphemyResult = calculateBlasphemyDiscount(player, spellSchool);
        
        if (blasphemyResult.hasBlasphemy) {
            if (AnsConfig.ALLOW_DISCOUNT_STACKING.get() || !hasVirtue) {
                // Apply Blasphemy discount (stacking multiplicatively if allowed)
                multiplier *= (1.0 - blasphemyResult.totalDiscount);
                
                logDebug("Blasphemy discount applied: {:.1f}% (school: {}, matching: {})",
                    blasphemyResult.totalDiscount * 100, spellSchool, blasphemyResult.isMatching);
            } else {
                logDebug("Blasphemy discount skipped (stacking disabled and Virtue Ring active)");
            }
        }
        
        return multiplier;
    }
    
    /**
     * Determine the spell school from the spell cost event.
     * 
     * @param event The spell cost event
     * @return The spell school identifier
     */
    private static String determineSpellSchool(SpellCostCalcEvent event) {
        // Try to get spell from CasterContext (set by MixinSpellResolverContext)
        return CasterContext.getSpell().map(spell -> {
            if (spell.recipe != null && !spell.recipe.isEmpty()) {
                AbstractSpellPart firstPart = spell.recipe.get(0);
                return SanctifiedLegacyCompat.determineSpellSchool(firstPart);
            }
            return "generic";
        }).orElse("generic");
    }
    
    /**
     * Calculate Blasphemy discount for the player.
     * 
     * @param player The player
     * @param spellSchool The spell school
     * @return Blasphemy discount result
     */
    private static BlasphemyDiscountResult calculateBlasphemyDiscount(Player player, String spellSchool) {
        // Check if player has any Blasphemy curio
        if (!SanctifiedLegacyCompat.hasAnyBlasphemy(player)) {
            return new BlasphemyDiscountResult(false, 0.0, false);
        }
        
        // Get base Blasphemy discount
        double baseDiscount = AnsConfig.BLASPHEMY_DISCOUNT.get();
        
        // Check if the Blasphemy matches the spell school
        String matchingBlasphemy = SanctifiedLegacyCompat.getMatchingBlasphemyType(spellSchool);
        boolean isMatching = matchingBlasphemy != null && 
            SanctifiedLegacyCompat.hasBlasphemyType(player, matchingBlasphemy);
        
        // Apply matching school bonus if applicable
        double totalDiscount = baseDiscount;
        if (isMatching) {
            double matchingBonus = AnsConfig.BLASPHEMY_MATCHING_SCHOOL_BONUS.get();
            totalDiscount += matchingBonus;
            
            // Cap at 95% discount
            totalDiscount = Math.min(0.95, totalDiscount);
        }
        
        return new BlasphemyDiscountResult(true, totalDiscount, isMatching);
    }
    
    /**
     * Log debug message if debug mode is enabled.
     */
    private static void logDebug(String message, Object... args) {
        if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
            LOGGER.info("[CurioDiscount] [DEBUG] " + message, args);
        }
    }
    
    /**
     * Container for Blasphemy discount calculation results.
     */
    private static class BlasphemyDiscountResult {
        final boolean hasBlasphemy;
        final double totalDiscount;
        final boolean isMatching;
        
        BlasphemyDiscountResult(boolean hasBlasphemy, double totalDiscount, boolean isMatching) {
            this.hasBlasphemy = hasBlasphemy;
            this.totalDiscount = totalDiscount;
            this.isMatching = isMatching;
        }
    }
}
