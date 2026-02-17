package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.aura.AuraManager;
import com.otectus.arsnspells.casting.CastingAuthority;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.events.CursedRingHandler;
import com.otectus.arsnspells.events.LPDeathPrevention;
import com.otectus.arsnspells.events.VirtueRingHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PRE-CAST VALIDATION MIXIN
 *
 * This is the HARD GATE that prevents Ars Nouveau spells from executing
 * if the player doesn't have sufficient resources.
 *
 * Injects at the HEAD of SpellResolver.canCast() to validate BEFORE
 * any spell logic executes. canCast() is called by onCast(), onCastOnBlock(),
 * and onCastOnEntity() — covering all Ars Nouveau cast paths.
 */
@Mixin(value = SpellResolver.class, remap = false)
public abstract class MixinSpellResolverPreCast {
    private static final Logger LOGGER = LoggerFactory.getLogger(MixinSpellResolverPreCast.class);

    @Shadow public SpellContext spellContext;
    @Shadow public abstract int getResolveCost();

    /**
     * CRITICAL: This runs BEFORE the spell resolves.
     * If this cancels, the spell NEVER executes.
     *
     * Validates ALL Ars Nouveau spell casts to ensure player has sufficient resources.
     * Works for:
     * - Standard mana validation (prevents casting without mana)
     * - Cursed Ring LP/health validation
     * - Virtue Ring aura validation
     * - Unified mana pool validation
     */
    @Inject(method = "canCast", at = @At("HEAD"), cancellable = true)
    private void arsnspells$validatePreCast(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player player)) {
            return;
        }

        if (player.level().isClientSide()) {
            return;
        }

        SpellResolver resolver = (SpellResolver) (Object) this;
        int cost = resolver.getResolveCost();

        if (player.isCreative()) {
            return;
        }

        // If cost is zero, validate alternate resource costs.
        // Both CursedRingHandler and VirtueRingHandler set mana cost to 0
        // during SpellCostCalcEvent and store their respective pending costs.
        if (cost <= 0) {
            if (SanctifiedLegacyCompat.isAvailable()) {
                // Cursed Ring LP validation
                if (SanctifiedLegacyCompat.isWearingCursedRing(player)) {
                    int pendingLpCost = CursedRingHandler.getPendingLPCost(player);
                    if (pendingLpCost > 0) {
                        LOGGER.debug("PRE-CAST VALIDATION (LP): Player={}, LP Cost={}",
                            player.getName().getString(), pendingLpCost);

                        boolean hasEnough = SanctifiedLegacyCompat.hasEnoughLP(player, pendingLpCost);
                        if (!hasEnough) {
                            boolean deathPenalty = AnsConfig.DEATH_ON_INSUFFICIENT_LP.get();
                            if (deathPenalty) {
                                LPDeathPrevention.markSpellCast(player);
                                return;
                            }

                            LOGGER.warn("SPELL CAST DENIED (LP) for {}", player.getName().getString());
                            cir.setReturnValue(false);
                            cir.cancel();
                            CursedRingHandler.clearPendingLPCost(player);

                            SanctifiedLegacyCompat.applySilentHealthLoss(player, 2.0f);

                            if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                                player.displayClientMessage(
                                    Component.literal("\u00a7cInsufficient LP - Spell Cancelled"), true);
                            }
                        } else {
                            LPDeathPrevention.markSpellCast(player);
                        }
                    }
                }

                // Virtue Ring aura validation
                if (SanctifiedLegacyCompat.isWearingVirtueRing(player)) {
                    int pendingAuraCost = VirtueRingHandler.getPendingAuraCost(player);
                    if (pendingAuraCost > 0) {
                        LOGGER.debug("PRE-CAST VALIDATION (Aura): Player={}, Aura Cost={}",
                            player.getName().getString(), pendingAuraCost);

                        boolean hasEnough = AuraManager.hasEnoughAura(player, pendingAuraCost);
                        if (!hasEnough) {
                            LOGGER.warn("SPELL CAST DENIED (Aura) for {}", player.getName().getString());
                            cir.setReturnValue(false);
                            cir.cancel();
                            VirtueRingHandler.clearPendingAuraCost(player);

                            if (AnsConfig.SHOW_AURA_MESSAGES.get()) {
                                int currentAura = AuraManager.getAura(player);
                                player.displayClientMessage(
                                    Component.literal("\u00a7bInsufficient Aura: Need " + pendingAuraCost
                                        + ", have " + currentAura),
                                    true);
                            }
                        }
                    }
                }
            }
            return;
        }

        LOGGER.debug("PRE-CAST VALIDATION: Player={}, Cost={}", player.getName().getString(), cost);

        // HARD GATE: Validate resources BEFORE spell execution
        boolean canCast = CastingAuthority.canCastArsSpell(player, resolver);

        if (!canCast) {
            LOGGER.warn("SPELL CAST DENIED for {}", player.getName().getString());
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
