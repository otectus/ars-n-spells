package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.compat.CompatIds;
import com.otectus.arsnspells.compat.ModPresence;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.compat.curios.CuriosAccess;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.util.SpellAnalysis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles curio mana cost discounts on Ars Nouveau spells.
 *
 * <p>Two discounts, applied in this order:
 * <ol>
 *   <li>the tagged-curio discount, identical to the NeoForge 1.21.1 build: each worn curio in
 *       {@code #ars_n_spells:curio_spell_discount} multiplies the cost by
 *       {@code 1 - virtue_ring_discount}, the combined factor is floored at
 *       {@code 1 - max_total_curio_discount}, and a spell that cost mana never rounds to free.
 *       {@link com.otectus.arsnspells.compat.curios.IronsCurioDiscountHandler} applies the same
 *       discount to Iron's casts;</li>
 *   <li>with Covenant of the Seven installed, its Blasphemy curio discount (Forge 1.20.1 only;
 *       Covenant has no 1.21.1 release).</li>
 * </ol>
 *
 * Priority: LOW - Applied after other cost modifiers to ensure proper stacking
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class CurioDiscountHandler {

    /** Item tag - any worn-curio stack matching this tag grants the discount. */
    public static final TagKey<Item> CURIO_SPELL_DISCOUNT_TAG = ItemTags.create(
        new ResourceLocation(ArsNSpells.MODID, "curio_spell_discount"));
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

        // Only apply to player casters
        LivingEntity caster = event.context != null ? event.context.getUnwrappedCaster() : null;
        if (!(caster instanceof Player player)) {
            return;
        }

        applyTaggedCurioDiscount(event, player);

        // Check if Sanctified Legacy is available
        if (!SanctifiedLegacyCompat.isAvailable()) {
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
            
            // ANS-MED-026: SLF4J does not accept the `{:.1f}` placeholder — it took it as
            // a literal and silently dropped the trailing arg. Use String.format for the
            // formatted percent and SLF4J `{}` for the rest.
            logDebug("Applied curio discount to {}: {} mana -> {} mana (saved {} mana, {} discount)",
                player.getName().getString(), currentCost, discountedCost, savedMana,
                String.format("%.1f%%", (1.0 - discountMultiplier) * 100));
        }
    }
    
    /**
     * The tagged-curio discount, the same arithmetic as the NeoForge build's handler.
     */
    private static void applyTaggedCurioDiscount(SpellCostCalcEvent event, Player player) {
        if (!ModPresence.isLoaded(CompatIds.CURIOS)) {
            return;
        }
        int matching = CuriosAccess.countTagged(player, CURIO_SPELL_DISCOUNT_TAG);
        if (matching <= 0) {
            return;
        }
        double perCurio = AnsConfig.VIRTUE_RING_DISCOUNT.get();
        if (perCurio <= 0.0) {
            return;
        }
        // Multiplicative stack: each tagged curio multiplies cost by (1 - perCurio),
        // then clamped so the combined discount never exceeds the configured cap,
        // and floored at 1 mana so spells never round to free unless 0-cost already.
        double factor = Math.pow(Math.max(0.0, 1.0 - perCurio), matching);
        factor = Math.max(factor, 1.0 - AnsConfig.MAX_TOTAL_CURIO_DISCOUNT.get());
        int original = event.currentCost;
        int discounted = (int) Math.max(original > 0 ? 1 : 0, Math.round(original * factor));
        event.currentCost = discounted;

        if (AnsConfig.debugEnabled()) {
            LOGGER.info("[CurioDiscount] {} matching curios -> {}% cost -> {} (was {})",
                matching, (int) (factor * 100), discounted, original);
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

        // Only Blasphemy discounts apply here. The Virtue Ring zeroed the cost at HIGHEST
        // priority (VirtueRingHandler) by converting mana to aura, so this LOW-priority
        // handler doesn't see Virtue Ring wearers (we return early when cost <= 0 above).
        // Blasphemy discount
        String spellSchool = determineSpellSchool(event);
        BlasphemyDiscountResult blasphemyResult = calculateBlasphemyDiscount(player, spellSchool);

        if (blasphemyResult.hasBlasphemy) {
            multiplier *= (1.0 - blasphemyResult.totalDiscount);

            // ANS-MED-026: SLF4J does not accept `{:.1f}` — format the percent with
            // String.format and pass plain `{}` args (mirrors the onSpellCostCalc log).
            logDebug("Blasphemy discount applied: {} (school: {}, matching: {})",
                String.format("%.1f%%", blasphemyResult.totalDiscount * 100), spellSchool, blasphemyResult.isMatching);
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
        // ANS-HIGH-003: read the spell directly from the event context instead of a
        // ThreadLocal. See CursedRingHandler for the full rationale.
        Spell spell = event.context != null ? event.context.getSpell() : null;
        if (spell == null) {
            return "generic";
        }
        return SpellAnalysis.analyze(spell).dominantSchool();
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
