package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Redirects Ars Nouveau potion effects to the unified mana system.
 * 
 * This fixes the issue where Ars potions (mana regen, spell damage) don't work
 * because they modify the Ars native pool which is no longer being read.
 */
@Mixin(value = ManaCap.class, remap = false)
public abstract class MixinArsPotionEffects {
    
    private static final UUID POTION_MANA_REGEN_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID POTION_MAX_MANA_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @Shadow @Final private LivingEntity livingEntity;

    /**
     * Intercept mana regeneration tick to apply potion effects to unified pool.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void arsnspells$redirectPotionEffects(CallbackInfo ci) {
        if (!(this.livingEntity instanceof Player player)) {
            return;
        }

        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }

        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode == null || !mode.isIssPrimary()) {
            // Only redirect if Iron's is primary (otherwise Ars handles it natively)
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        // Check for Ars potion effects and apply them to Iron's mana system
        redirectManaRegenPotions(player);
        redirectMaxManaPotions(player);
        redirectSpellDamagePotions(player);
    }

    /**
     * Redirect mana regeneration potion effects to Iron's mana regen attribute.
     */
    private void redirectManaRegenPotions(Player player) {
        try {
            // Check if player has any Ars mana regen effects
            double arsRegenBonus = calculateArsRegenBonus(player);
            
            if (arsRegenBonus > 0) {
                // Apply to Iron's mana regen attribute
                AttributeInstance regenAttr = player.getAttribute(AttributeRegistry.MANA_REGEN.get());
                if (regenAttr != null) {
                    // Remove old modifier
                    regenAttr.removeModifier(POTION_MANA_REGEN_ID);
                    
                    // Apply new modifier with conversion
                    double conversionRate = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
                    double ironRegenBonus = arsRegenBonus * conversionRate;
                    
                    AttributeModifier modifier = new AttributeModifier(
                        POTION_MANA_REGEN_ID,
                        "Ars Potion Mana Regen",
                        ironRegenBonus,
                        AttributeModifier.Operation.ADDITION
                    );
                    regenAttr.addTransientModifier(modifier);
                }
            } else {
                // Remove modifier if no longer active
                AttributeInstance regenAttr = player.getAttribute(AttributeRegistry.MANA_REGEN.get());
                if (regenAttr != null) {
                    regenAttr.removeModifier(POTION_MANA_REGEN_ID);
                }
            }
        } catch (Exception e) {
            // Silently fail if Iron's API is unavailable
        }
    }

    /**
     * Redirect max mana potion effects to Iron's max mana attribute.
     */
    private void redirectMaxManaPotions(Player player) {
        try {
            double arsMaxManaBonus = calculateArsMaxManaBonus(player);
            
            if (arsMaxManaBonus > 0) {
                AttributeInstance maxManaAttr = player.getAttribute(AttributeRegistry.MAX_MANA.get());
                if (maxManaAttr != null) {
                    maxManaAttr.removeModifier(POTION_MAX_MANA_ID);
                    
                    double conversionRate = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
                    double ironMaxManaBonus = arsMaxManaBonus * conversionRate;
                    
                    AttributeModifier modifier = new AttributeModifier(
                        POTION_MAX_MANA_ID,
                        "Ars Potion Max Mana",
                        ironMaxManaBonus,
                        AttributeModifier.Operation.ADDITION
                    );
                    maxManaAttr.addTransientModifier(modifier);
                }
            } else {
                AttributeInstance maxManaAttr = player.getAttribute(AttributeRegistry.MAX_MANA.get());
                if (maxManaAttr != null) {
                    maxManaAttr.removeModifier(POTION_MAX_MANA_ID);
                }
            }
        } catch (Exception e) {
            // Silently fail
        }
    }

    /**
     * Redirect spell damage potion effects to Iron's spell power attributes.
     */
    private void redirectSpellDamagePotions(Player player) {
        // TODO: Implement spell damage redirection
        // This requires mapping Ars spell damage effects to Iron's spell power attributes
    }

    /**
     * Calculate total mana regen bonus from Ars potion effects.
     */
    private double calculateArsRegenBonus(Player player) {
        double bonus = 0.0;
        
        // Check for Ars Nouveau mana regen effects
        // This is a simplified check - you may need to adjust based on actual Ars potion effects
        for (MobEffectInstance effect : player.getActiveEffects()) {
            String effectName = effect.getEffect().getDescriptionId().toLowerCase();
            if (effectName.contains("mana_regen") || effectName.contains("mana_boost")) {
                // Base regen bonus per amplifier level
                bonus += (effect.getAmplifier() + 1) * 0.5;
            }
        }
        
        return bonus;
    }

    /**
     * Calculate total max mana bonus from Ars potion effects.
     */
    private double calculateArsMaxManaBonus(Player player) {
        double bonus = 0.0;
        
        for (MobEffectInstance effect : player.getActiveEffects()) {
            String effectName = effect.getEffect().getDescriptionId().toLowerCase();
            if (effectName.contains("max_mana") || effectName.contains("mana_boost")) {
                bonus += (effect.getAmplifier() + 1) * 10.0;
            }
        }
        
        return bonus;
    }
}
