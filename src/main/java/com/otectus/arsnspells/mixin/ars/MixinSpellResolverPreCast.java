package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.casting.CastingAuthority;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.events.CursedRingHandler;
import com.otectus.arsnspells.events.LPDeathPrevention;
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
 * Injects at the HEAD of SpellResolver.resolve() to validate BEFORE
 * any spell logic executes.
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
     * This validates ALL Ars Nouveau spell casts to ensure player has sufficient resources.
     * Works for:
     * - Standard mana validation (prevents casting without mana)
     * - Cursed Ring LP/health validation
     * - Virtue Ring discounted mana validation
     * - Unified mana pool validation
     */
    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$validatePreCast(SpellContext context, CallbackInfoReturnable<Boolean> cir) {
        // Only validate for players
        if (context == null) {
            return;
        }

        LivingEntity caster = context.getUnwrappedCaster();
        if (!(caster instanceof Player player)) {
            return;
        }

        // Skip client-side validation (server is authoritative)
        if (player.level().isClientSide()) {
            return;
        }

        // Get the spell resolver instance
        SpellResolver resolver = (SpellResolver) (Object) this;
        int cost = resolver.getResolveCost();

        // Skip validation for creative mode players
        if (player.isCreative()) {
            return;
        }

        // If cost is zero, still validate Cursed Ring LP costs (SpellCostCalcEvent sets mana cost to 0)
        if (cost <= 0) {
            if (SanctifiedLegacyCompat.isAvailable() && SanctifiedLegacyCompat.isWearingCursedRing(player)) {
                int pendingLpCost = CursedRingHandler.getPendingLPCost(player);
                if (pendingLpCost > 0) {
                    LOGGER.info("ðŸ” PRE-CAST VALIDATION (LP): Player={}, LP Cost={}, Side=SERVER",
                        player.getName().getString(), pendingLpCost);

                    boolean hasEnough = SanctifiedLegacyCompat.hasEnoughLP(player, pendingLpCost);
                    if (!hasEnough) {
                        boolean deathPenalty = AnsConfig.DEATH_ON_INSUFFICIENT_LP.get();
                        if (deathPenalty) {
                            LOGGER.warn("âŒ Insufficient LP but death penalty enabled - allowing cast");
                            LPDeathPrevention.markSpellCast(player);
                            return;
                        }

                        LOGGER.warn("âŒ SPELL CAST DENIED (LP) for {}", player.getName().getString());
                        cir.setReturnValue(false);
                        cir.cancel();
                        CursedRingHandler.clearPendingLPCost(player);

                        // Apply minor health penalty silently (1 heart)
                        SanctifiedLegacyCompat.applySilentHealthLoss(player, 2.0f);

                        if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                            player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("Â§cInsufficient LP - Spell Cancelled"),
                                true
                            );
                        }
                    } else {
                        LPDeathPrevention.markSpellCast(player);
                    }
                }
            }
            return;
        }

        LOGGER.info("🔍 PRE-CAST VALIDATION: Player={}, Cost={}, Side=SERVER",
            player.getName().getString(), cost);

        // HARD GATE: Validate resources BEFORE spell execution
        boolean canCast = CastingAuthority.canCastArsSpell(player, resolver);

        if (!canCast) {
            // DENY THE CAST - spell never executes
            LOGGER.warn("❌ SPELL CAST DENIED for {}", player.getName().getString());

            cir.setReturnValue(false);
            cir.cancel();

            // Message already sent by CastingAuthority
        } else {
            LOGGER.info("✅ SPELL CAST ALLOWED for {}", player.getName().getString());
        }
    }
}
