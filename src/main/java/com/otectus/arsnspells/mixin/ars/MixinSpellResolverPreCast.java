package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellValidationError;
import com.hollingsworth.arsnouveau.common.util.PortUtil;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.AlternativePayment;
import com.otectus.arsnspells.casting.CastLedger;
import com.otectus.arsnspells.casting.CastingAuthority;
import com.otectus.arsnspells.casting.QuoteService;
import com.otectus.arsnspells.compat.AlternativeResourceAccess;
import com.otectus.arsnspells.contract.AttemptState;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.ResourceAccess;
import com.otectus.arsnspells.contract.CompatibilityStatus;
import com.otectus.arsnspells.contract.ResourceAmount;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.events.CursedRingHandler;
import com.otectus.arsnspells.events.LPDeathPrevention;
import com.otectus.arsnspells.events.VirtueRingHandler;
import com.otectus.arsnspells.spell.CrossCastContext;
import com.otectus.arsnspells.util.CrossCastTrace;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/**
 * PRE-CAST VALIDATION MIXIN
 *
 * This is the HARD GATE that prevents Ars Nouveau spells from executing
 * if the player doesn't have sufficient resources.
 *
 * Injects at the HEAD of SpellResolver.canCast() to validate BEFORE
 * any spell logic executes. canCast() is called by onCast(), onCastOnBlock(),
 * and onCastOnEntity() — covering all Ars Nouveau cast paths.
 *
 * IMPORTANT: Since we cancel the native canCast() entirely to prevent
 * stale ManaCap data from failing enoughMana(), we must replicate the
 * native spell recipe validation (spellValidator.validate) ourselves.
 *
 * <p>Injection target, confirmed against the pinned Ars Nouveau 4.12.7 jar
 * ({@code ars-nouveau-401955-6688854.jar}, sha256
 * {@code 1f1debc282a0c379c1141f2840ea294eede6f6b544c589663f40bbe17b59a1af}):
 * {@code com/hollingsworth/arsnouveau/api/spell/SpellResolver.canCast}, descriptor
 * {@code (Lnet/minecraft/world/entity/LivingEntity;)Z}. Ars mod classes are not
 * reobfuscated, hence {@code remap = false}.
 */
@Mixin(value = SpellResolver.class, remap = false)
public abstract class MixinSpellResolverPreCast {
    private static final Logger LOGGER = LoggerFactory.getLogger(MixinSpellResolverPreCast.class);

    @Shadow public SpellContext spellContext;
    @Shadow public Spell spell;
    @Shadow public boolean silent;
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

        // FIX: When mana unification is disabled AND no Sanctified rings are active,
        // let Ars Nouveau handle canCast() natively. This prevents the mod from
        // interfering with vanilla Ars mana validation when unification is off.
        if (!BridgeManager.isUnificationEnabled()) {
            // Ask whether the ring's cost path is ACTIVE, not merely whether the ring is worn.
            // A ring whose system is toggled off charges nothing, so ANS has no reason to take
            // over validation — pre-cast and mana expenditure must agree on this, or one side
            // waives a cost the other never collects.
            if (!SanctifiedLegacyCompat.isAnyRingCostPathActive(player)) {
                return; // Let native Ars canCast() run
            }
        }

        // Replicate native spell recipe validation since we bypass the rest of canCast().
        // This checks: non-empty recipe, starts with cast method, max one cast method,
        // augment caps, and glyph limits — matching Ars Nouveau's StandardSpellValidator.
        List<SpellValidationError> validationErrors = ArsNouveauAPI.getInstance()
            .getSpellCastingSpellValidator()
            .validate(this.spell.recipe);

        if (!validationErrors.isEmpty()) {
            if (!this.silent) {
                PortUtil.sendMessageNoSpam(entity, validationErrors.get(0).makeTextComponentExisting());
            }
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        SpellResolver resolver = (SpellResolver) (Object) this;
        int cost = resolver.getResolveCost();

        if (player.isCreative()) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        // If cost is zero, validate alternate resource costs.
        // Both CursedRingHandler and VirtueRingHandler set mana cost to 0
        // during SpellCostCalcEvent and store their respective pending costs.
        if (cost <= 0) {
            if (SanctifiedLegacyCompat.isAvailable()) {
                // Cursed Ring LP validation. Gated on the active path, not just the worn ring:
                // a stale pending cost from before the toggle was flipped must not be validated
                // (and then charged) after the owner disabled the system.
                if (SanctifiedLegacyCompat.isCursedRingCostPathActive(player)) {
                    int pendingLpCost = CursedRingHandler.getPendingLPCost(player);
                    if (pendingLpCost > 0) {
                        LOGGER.debug("PRE-CAST VALIDATION (LP): Player={}, LP Cost={}",
                            player.getName().getString(), pendingLpCost);

                        // V23/V24: the LP leg is reserved here, at the same boundary the mana
                        // legs reserve at, and keyed on this cast's own attempt id. It used to
                        // be a bare "do you have enough?" whose answer no later handler could
                        // correlate with the charge it eventually made, so the cast could be
                        // validated against one staged cost and charged against another.
                        if (AnsConfig.DEATH_ON_INSUFFICIENT_LP.get()
                                && !SanctifiedLegacyCompat.hasEnoughLP(player, pendingLpCost)) {
                            // Death penalty is opt-in and deliberately opens no leg: the debt is
                            // settled by killing the caster, not by taking a resource.
                            LPDeathPrevention.markSpellCast(player);
                            cir.setReturnValue(true);
                            cir.cancel();
                            return;
                        }
                        if (!arsnspells$reserveAlternativeLeg(player, ResourceUnit.LP, pendingLpCost)) {
                            LOGGER.warn("SPELL CAST DENIED (LP) for {}", player.getName().getString());
                            CursedRingHandler.clearPendingLPCost(player);
                            SanctifiedLegacyCompat.applySilentHealthLoss(player, 2.0f);

                            if (AnsConfig.SHOW_LP_COST_MESSAGES.get()) {
                                player.displayClientMessage(
                                    Component.literal("\u00a7cInsufficient LP - Spell Cancelled"), true);
                            }
                            cir.setReturnValue(false);
                            cir.cancel();
                            return;
                        }
                        LPDeathPrevention.markSpellCast(player);
                        cir.setReturnValue(true);
                        cir.cancel();
                        return;
                    }
                }

                // Virtue Ring aura validation \u2014 Covenant of the Seven owns the aura
                // state; we read/spend via SanctifiedLegacyCompat reflection bridges.
                if (SanctifiedLegacyCompat.isVirtueAuraCostPathActive(player)) {
                    int pendingAuraCost = VirtueRingHandler.getPendingAuraCost(player);
                    if (pendingAuraCost > 0) {
                        LOGGER.debug("PRE-CAST VALIDATION (Aura): Player={}, Aura Cost={}",
                            player.getName().getString(), pendingAuraCost);

                        // V23/V24: reserve the aura leg rather than merely asking whether the
                        // ambient sample looks big enough. A drain that comes up short is a
                        // partial payment, and PaymentOpenFailurePolicy - not this mixin -
                        // decides what a partial payment means for the cast.
                        if (!arsnspells$reserveAlternativeLeg(player, ResourceUnit.AURA, pendingAuraCost)) {
                            LOGGER.warn("SPELL CAST DENIED (Aura) for {}", player.getName().getString());
                            VirtueRingHandler.clearPendingAuraCost(player);

                            int currentAura = SanctifiedLegacyCompat.getCovenantAura(player);
                            player.displayClientMessage(
                                Component.literal("\u00a7bInsufficient Aura: Need " + pendingAuraCost
                                    + ", have " + currentAura),
                                true);
                            cir.setReturnValue(false);
                            cir.cancel();
                            return;
                        }
                        // Aura is sufficient — allow the cast
                        cir.setReturnValue(true);
                        cir.cancel();
                        return;
                    }
                }
            }
            // Zero-cost spell with no ring — always allow, bypass native enoughMana()
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        LOGGER.debug("PRE-CAST VALIDATION: Player={}, Cost={}", player.getName().getString(), cost);

        // HARD GATE: Validate resources BEFORE spell execution.
        // We MUST take full ownership of the canCast result here.
        // If we return without cancelling, Ars's native enoughMana() will run
        // and check ManaCap.getCurrentMana() which may return stale data (0)
        // because playerOnTick is suppressed in ISS_PRIMARY mode.
        boolean canCast = CastingAuthority.canCastArsSpell(player, resolver);

        // V01: getResolveCost() above posted the cost event that opened this cast's
        // attempt and attached its quote. Reserve those legs now, at the gate, so the
        // spell either starts already paid for or does not start. Nothing was taken
        // during the cost query itself.
        if (canCast) {
            canCast = arsnspells$reserveQuotedLegs(player);
        }

        if (!canCast) {
            LOGGER.warn("SPELL CAST DENIED for {}", player.getName().getString());
        }

        // Always set the return value — both pass and fail — to prevent
        // Ars's native enoughMana() from running with stale ManaCap data.
        cir.setReturnValue(canCast);
        cir.cancel();

        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        java.util.UUID attemptId = entry != null ? entry.attemptId : null;
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.RESOURCE_CHECK,
            "cost", cost, "approved", canCast);
    }

    /**
     * Reserve one alternative payment leg for this cast (audit V23, V24).
     *
     * <p>The leg is opened against a real {@link CastAttempt}, keyed on the same
     * (player, carrier) pair the mana legs use, so the charge and the later commit or release
     * are provably about the same cast rather than about whatever a FIFO deque happened to
     * hold. The payment policy is read here, at initiation, and carried on the leg: a config
     * reload between this gate and the resolve must not change the terms of a payment that is
     * already open.
     *
     * <p>An insufficient balance denies before any debit at all, and a partial drain follows
     * {@code payment_open_failure_policy} rather than being reported as success.
     *
     * @return whether the cast may proceed
     */
    @Unique
    private boolean arsnspells$reserveAlternativeLeg(Player player, ResourceUnit unit, int amount) {
        if (amount <= 0) {
            return true;
        }
        String carrierIdentity = CastLedger.carrierIdentity(
            this.spellContext != null ? this.spellContext.getCasterTool() : null);
        CastAttempt attempt = CastLedger.findOpen(player.getUUID(), carrierIdentity)
            .orElseGet(() -> CastLedger.open(
                player.getUUID(), carrierIdentity, 0,
                QuoteService.quoteNativeCast(unit, amount, QuoteService.currentRules()),
                player.level().getGameTime()));

        java.util.function.Function<java.util.UUID, Player> resolver =
            id -> id.equals(player.getUUID()) ? player : null;
        ResourceAccess access;
        CompatibilityStatus status;
        if (unit == ResourceUnit.LP) {
            access = AlternativeResourceAccess.lp(resolver);
            status = AlternativeResourceAccess.lpStatus();
        } else {
            access = AlternativeResourceAccess.aura(resolver);
            status = AlternativeResourceAccess.auraStatus();
        }

        AlternativePayment.Result result = AlternativePayment.reserve(
            attempt.attemptId(), player.getUUID(), unit, amount, access, status,
            AnsConfig.getPaymentOpenFailurePolicy());
        if (!result.allowsCast()) {
            // The leg released whatever it took; drop the attempt too, so nothing is left
            // holding a reservation for a cast that will not happen.
            if (!attempt.state().isTerminal()) {
                CastLedger.fail(attempt, CastLedger.forPlayer(player));
            }
            return false;
        }
        return true;
    }

    /** Float slop below which a shortfall is rounding, not a genuine partial drain. */
    @Unique
    private static final double ARSNSPELLS$RESERVE_TOLERANCE = 1.0e-3d;

    /**
     * Take the reservation for the open attempt, if there is one.
     *
     * <p>A partial drain is a denial, not a discount: {@link ResourceAccess#debit} reports
     * what actually moved, and if any leg came up short the whole reservation is released
     * (exactly once, via the ledger) and the cast is refused. The alternative - letting the
     * spell run on a half-paid reservation - is the mana duplication this ticket exists to
     * make impossible.
     *
     * @return whether the cast may proceed
     */
    @Unique
    private boolean arsnspells$reserveQuotedLegs(Player player) {
        String carrierIdentity = CastLedger.carrierIdentity(
            this.spellContext != null ? this.spellContext.getCasterTool() : null);
        Optional<CastAttempt> open = CastLedger.findOpen(player.getUUID(), carrierIdentity);
        if (open.isEmpty()) {
            // No attempt: ANS is not pricing this cast, so Ars pays for it natively.
            return true;
        }
        CastAttempt attempt = open.get();
        if (attempt.state() != AttemptState.REQUESTED) {
            // Already reserved by an earlier canCast for the same in-flight cast. Reserving
            // again would take the price a second time.
            return attempt.state() == AttemptState.RESERVED;
        }

        ResourceAccess access = CastLedger.forPlayer(player);
        List<ResourceAmount> reserved = CastLedger.reserve(attempt, access);
        for (ResourceAmount owed : attempt.quote().legs()) {
            double taken = 0.0d;
            for (ResourceAmount leg : reserved) {
                if (leg.unit() == owed.unit()) {
                    taken += leg.amount();
                }
            }
            if (taken + ARSNSPELLS$RESERVE_TOLERANCE < owed.amount()) {
                CastLedger.fail(attempt, access);
                CastingAuthority.sendDenialMessage(player,
                    "§cNot Enough Mana: the cast needs " + (int) owed.amount() + " "
                        + owed.unit() + " and only " + (int) taken + " could be reserved");
                return false;
            }
        }
        return true;
    }
}
