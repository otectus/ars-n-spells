package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.common.event.ManaCapEvents;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.ManaRegenBridge;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects Ars Nouveau mana potion effects (mana regen, mana boost) to the
 * unified Iron's mana attributes when Iron's is the primary pool.
 *
 * <p>Without this, Ars potions modify the Ars native pool, which is no longer
 * read in ISS_PRIMARY mode, so the potions appear to do nothing.
 *
 * <p>Ported from the Forge 1.20.1 mixin. NeoForge 1.21.1 deltas: the Ars target
 * {@code ManaCapEvents.playerOnTick} now takes
 * {@link PlayerTickEvent.Pre} (was Forge {@code TickEvent.PlayerTickEvent});
 * mob-effect lookup uses {@link BuiltInRegistries#MOB_EFFECT}; and attribute
 * modifiers are keyed by {@link ResourceLocation} (not UUID) with
 * {@code Operation.ADD_VALUE}.
 */
@Mixin(value = ManaCapEvents.class, remap = false)
public abstract class MixinArsPotionEffects {

    @Unique
    private static final ResourceLocation POTION_MANA_REGEN_ID =
        ResourceLocation.fromNamespaceAndPath("ars_n_spells", "potion_mana_regen");
    @Unique
    private static final ResourceLocation POTION_MAX_MANA_ID =
        ResourceLocation.fromNamespaceAndPath("ars_n_spells", "potion_max_mana");

    @Unique
    private static final ResourceLocation ARS_MANA_REGEN_EFFECT =
        ResourceLocation.fromNamespaceAndPath("ars_nouveau", "mana_regen");
    @Unique
    private static final ResourceLocation ARS_MANA_BOOST_EFFECT =
        ResourceLocation.fromNamespaceAndPath("ars_nouveau", "mana_boost");

    /** Intercept Ars's per-tick mana handling to mirror its potions onto Iron's pool. */
    @Inject(method = "playerOnTick", at = @At("HEAD"), require = 0)
    private static void arsnspells$redirectPotionEffects(PlayerTickEvent.Pre event, CallbackInfo ci) {
        Player player = event.getEntity();

        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }

        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode == null || !mode.isIssPrimary()) {
            // Only redirect when Iron's is primary; otherwise Ars handles it natively.
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        arsnspells$redirectManaRegenPotions(player);
        arsnspells$redirectMaxManaPotions(player);
    }

    /** Mirror Ars mana-regen potions onto Iron's MANA_REGEN attribute. */
    @Unique
    private static void arsnspells$redirectManaRegenPotions(Player player) {
        try {
            double arsRegenBonus = arsnspells$calculateArsRegenBonus(player);

            AttributeInstance regenAttr = player.getAttribute(AttributeRegistry.MANA_REGEN);
            if (regenAttr == null) return;

            if (arsRegenBonus > 0) {
                // arsRegenBonus is absolute mana/sec; Iron's MANA_REGEN is a percentage-of-pool
                // multiplier. ManaRegenBridge converts units; the pool conversion rate is layered on.
                double conversionRate = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
                double absRegenPerSec = arsRegenBonus * conversionRate;
                double ironRegenBonus = ManaRegenBridge.convertArsToIrons(absRegenPerSec, player);

                // Runs every server tick: only churn the attribute when the value changed.
                AttributeModifier existing = regenAttr.getModifier(POTION_MANA_REGEN_ID);
                if (existing == null || existing.amount() != ironRegenBonus) {
                    regenAttr.removeModifier(POTION_MANA_REGEN_ID);
                    regenAttr.addTransientModifier(new AttributeModifier(
                        POTION_MANA_REGEN_ID,
                        ironRegenBonus,
                        AttributeModifier.Operation.ADD_VALUE));
                }
            } else if (regenAttr.getModifier(POTION_MANA_REGEN_ID) != null) {
                regenAttr.removeModifier(POTION_MANA_REGEN_ID);
            }
        } catch (Exception e) {
            // Silently fail if Iron's API is unavailable.
        }
    }

    /** Mirror Ars mana-boost potions onto Iron's MAX_MANA attribute. */
    @Unique
    private static void arsnspells$redirectMaxManaPotions(Player player) {
        try {
            double arsMaxManaBonus = arsnspells$calculateArsMaxManaBonus(player);

            AttributeInstance maxManaAttr = player.getAttribute(AttributeRegistry.MAX_MANA);
            if (maxManaAttr == null) return;

            if (arsMaxManaBonus > 0) {
                double conversionRate = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
                double ironMaxManaBonus = arsMaxManaBonus * conversionRate;

                AttributeModifier existing = maxManaAttr.getModifier(POTION_MAX_MANA_ID);
                if (existing == null || existing.amount() != ironMaxManaBonus) {
                    maxManaAttr.removeModifier(POTION_MAX_MANA_ID);
                    maxManaAttr.addTransientModifier(new AttributeModifier(
                        POTION_MAX_MANA_ID,
                        ironMaxManaBonus,
                        AttributeModifier.Operation.ADD_VALUE));
                }
            } else if (maxManaAttr.getModifier(POTION_MAX_MANA_ID) != null) {
                maxManaAttr.removeModifier(POTION_MAX_MANA_ID);
            }
        } catch (Exception e) {
            // Silently fail.
        }
    }

    /** Total mana-regen bonus from active Ars potion effects (registry-based detection). */
    @Unique
    private static double arsnspells$calculateArsRegenBonus(Player player) {
        double bonus = 0.0;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            ResourceLocation effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            if (effectId == null) continue;
            if (effectId.equals(ARS_MANA_REGEN_EFFECT) || effectId.equals(ARS_MANA_BOOST_EFFECT)) {
                bonus += (effect.getAmplifier() + 1) * 0.5;
            }
        }
        return bonus;
    }

    /** Total max-mana bonus from active Ars potion effects (registry-based detection). */
    @Unique
    private static double arsnspells$calculateArsMaxManaBonus(Player player) {
        double bonus = 0.0;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            ResourceLocation effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            if (effectId == null) continue;
            if (effectId.equals(ARS_MANA_BOOST_EFFECT)) {
                bonus += (effect.getAmplifier() + 1) * 10.0;
            }
        }
        return bonus;
    }
}
