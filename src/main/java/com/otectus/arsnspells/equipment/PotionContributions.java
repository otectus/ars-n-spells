package com.otectus.arsnspells.equipment;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.ManaRegenBridge;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.AnsModifierIds;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

/**
 * Mirrors Ars Nouveau potion effects onto Iron's mana attributes while Iron's owns the pool
 * (ISS_PRIMARY), the same redirect the Forge 1.20.1 build runs.
 *
 * <p>An Ars Mana Regen potion raises Ars's regen, which ISS_PRIMARY does not read, so without
 * this the potion does nothing. The redirect uses the Forge build's rates: 0.5 mana/sec per
 * effect level for {@code ars_nouveau:mana_regen} and {@code ars_nouveau:mana_boost}, and
 * 10 max mana per level for {@code mana_boost}. Neither Ars 4.12.7 nor Ars 5.13 registers
 * {@code mana_boost}; the lookup stays so both builds behave the same if an addon adds it.
 *
 * <p>The potion's own attribute modifier on Ars's {@code MANA_REGEN_BONUS} is not gear, so
 * {@link EquipmentIntegration#arsGearBonus} does not count it and the potion is mirrored once.
 *
 * <p>Iron's-only: callers must be behind {@code IronsCompat.isLoaded()}. Runs every server
 * player tick from {@code EquipmentHandler} and on every mode or config reconcile.
 */
public final class PotionContributions {
    private static final ResourceLocation POTION_MANA_REGEN_ID =
        ResourceLocation.parse(AnsModifierIds.ARS_POTION_MANA_REGEN);
    private static final ResourceLocation POTION_MAX_MANA_ID =
        ResourceLocation.parse(AnsModifierIds.ARS_POTION_MAX_MANA);
    private static final ResourceLocation ARS_MANA_REGEN_EFFECT =
        ResourceLocation.fromNamespaceAndPath("ars_nouveau", "mana_regen");
    private static final ResourceLocation ARS_MANA_BOOST_EFFECT =
        ResourceLocation.fromNamespaceAndPath("ars_nouveau", "mana_boost");

    private static Holder<MobEffect> manaRegenEffect;
    private static Holder<MobEffect> manaBoostEffect;
    private static volatile boolean effectsResolved;

    private PotionContributions() {}

    public static void reconcile(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        boolean redirecting = BridgeManager.isUnificationEnabled()
            && mode != null && mode.isIssPrimary();
        // V14: the removal is not gated on the redirect. Switching away from ISS_PRIMARY
        // must take the last potion modifier off, whatever turned the redirect off.
        if (!redirecting) {
            clearPotionModifiers(player);
            return;
        }
        redirectManaRegenPotions(player);
        redirectMaxManaPotions(player);
    }

    private static void clearPotionModifiers(Player player) {
        try {
            remove(player.getAttribute(AttributeRegistry.MANA_REGEN), POTION_MANA_REGEN_ID);
            remove(player.getAttribute(AttributeRegistry.MAX_MANA), POTION_MAX_MANA_ID);
        } catch (Exception e) {
            // Iron's attributes unresolvable: nothing of ours can be on them.
        }
    }

    private static void redirectManaRegenPotions(Player player) {
        try {
            AttributeInstance regenAttr = player.getAttribute(AttributeRegistry.MANA_REGEN);
            if (regenAttr == null) return;
            double arsRegenBonus = arsRegenBonus(player);
            if (arsRegenBonus > 0) {
                // Absolute mana/sec on the Ars side; Iron's MANA_REGEN is a percentage of the
                // pool, so ManaRegenBridge converts units. The pool conversion rate goes on top.
                double absRegenPerSec = arsRegenBonus * AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
                apply(regenAttr, POTION_MANA_REGEN_ID, ManaRegenBridge.convertArsToIrons(absRegenPerSec, player));
            } else {
                remove(regenAttr, POTION_MANA_REGEN_ID);
            }
        } catch (Exception e) {
            // Iron's API unavailable.
        }
    }

    private static void redirectMaxManaPotions(Player player) {
        try {
            AttributeInstance maxManaAttr = player.getAttribute(AttributeRegistry.MAX_MANA);
            if (maxManaAttr == null) return;
            double arsMaxManaBonus = arsMaxManaBonus(player);
            if (arsMaxManaBonus > 0) {
                apply(maxManaAttr, POTION_MAX_MANA_ID, arsMaxManaBonus * AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get());
            } else {
                remove(maxManaAttr, POTION_MAX_MANA_ID);
            }
        } catch (Exception e) {
            // Iron's API unavailable.
        }
    }

    /** Only rewrite the modifier when its value changed: this runs every tick. */
    private static void apply(AttributeInstance instance, ResourceLocation id, double amount) {
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null && existing.amount() == amount) return;
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void remove(AttributeInstance instance, ResourceLocation id) {
        if (instance != null && instance.getModifier(id) != null) instance.removeModifier(id);
    }

    private static void resolveEffects() {
        if (!effectsResolved) {
            manaRegenEffect = BuiltInRegistries.MOB_EFFECT.getHolder(ARS_MANA_REGEN_EFFECT).orElse(null);
            manaBoostEffect = BuiltInRegistries.MOB_EFFECT.getHolder(ARS_MANA_BOOST_EFFECT).orElse(null);
            effectsResolved = true;
        }
    }

    /** 0.5 mana/sec per level of mana_regen and of mana_boost. */
    static double arsRegenBonus(Player player) {
        resolveEffects();
        double bonus = 0.0;
        if (manaRegenEffect != null) {
            MobEffectInstance regen = player.getEffect(manaRegenEffect);
            if (regen != null) bonus += (regen.getAmplifier() + 1) * 0.5;
        }
        if (manaBoostEffect != null) {
            MobEffectInstance boost = player.getEffect(manaBoostEffect);
            if (boost != null) bonus += (boost.getAmplifier() + 1) * 0.5;
        }
        return bonus;
    }

    /** 10 max mana per level of mana_boost. */
    static double arsMaxManaBonus(Player player) {
        resolveEffects();
        if (manaBoostEffect == null) return 0.0;
        MobEffectInstance boost = player.getEffect(manaBoostEffect);
        return boost == null ? 0.0 : (boost.getAmplifier() + 1) * 10.0;
    }
}
