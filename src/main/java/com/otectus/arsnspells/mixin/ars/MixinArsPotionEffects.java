package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.common.event.ManaCapEvents;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
@Mixin(value = ManaCapEvents.class, remap = false)
public abstract class MixinArsPotionEffects {

    @Unique
    private static final UUID POTION_MANA_REGEN_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    @Unique
    private static final UUID POTION_MAX_MANA_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    /**
     * Intercept mana regeneration tick to apply potion effects to unified pool.
     */
    @Inject(method = "playerOnTick", at = @At("HEAD"))
    private static void arsnspells$redirectPotionEffects(PlayerTickEvent event, CallbackInfo ci) {
        Player player = event.player;

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
        arsnspells$redirectManaRegenPotions(player);
        arsnspells$redirectMaxManaPotions(player);
    }

    /**
     * Redirect mana regeneration potion effects to Iron's mana regen attribute.
     */
    @Unique
    private static void arsnspells$redirectManaRegenPotions(Player player) {
        try {
            // Check if player has any Ars mana regen effects
            double arsRegenBonus = arsnspells$calculateArsRegenBonus(player);

            AttributeInstance regenAttr = player.getAttribute(AttributeRegistry.MANA_REGEN.get());
            if (regenAttr == null) return;

            if (arsRegenBonus > 0) {
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
            } else {
                // Remove modifier if no longer active
                regenAttr.removeModifier(POTION_MANA_REGEN_ID);
            }
        } catch (Exception e) {
            // Silently fail if Iron's API is unavailable
        }
    }

    /**
     * Redirect max mana potion effects to Iron's max mana attribute.
     */
    @Unique
    private static void arsnspells$redirectMaxManaPotions(Player player) {
        try {
            double arsMaxManaBonus = arsnspells$calculateArsMaxManaBonus(player);

            AttributeInstance maxManaAttr = player.getAttribute(AttributeRegistry.MAX_MANA.get());
            if (maxManaAttr == null) return;

            if (arsMaxManaBonus > 0) {
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
            } else {
                maxManaAttr.removeModifier(POTION_MAX_MANA_ID);
            }
        } catch (Exception e) {
            // Silently fail
        }
    }

    /**
     * Calculate total mana regen bonus from Ars potion effects.
     */
    @Unique
    private static double arsnspells$calculateArsRegenBonus(Player player) {
        double bonus = 0.0;

        for (MobEffectInstance effect : player.getActiveEffects()) {
            String effectName = effect.getEffect().getDescriptionId().toLowerCase();
            if (effectName.contains("mana_regen") || effectName.contains("mana_boost")) {
                bonus += (effect.getAmplifier() + 1) * 0.5;
            }
        }

        return bonus;
    }

    /**
     * Calculate total max mana bonus from Ars potion effects.
     */
    @Unique
    private static double arsnspells$calculateArsMaxManaBonus(Player player) {
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
