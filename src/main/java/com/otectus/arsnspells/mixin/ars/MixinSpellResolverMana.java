package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.casting.AlternativePayment;
import com.otectus.arsnspells.casting.CastLedger;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.contract.AttemptState;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.util.CrossCastTrace;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * THE VERIFIED NATIVE PAYMENT BOUNDARY (audit V01).
 *
 * <p>Injection target, confirmed against the pinned Ars Nouveau 4.12.7 jar
 * ({@code ars-nouveau-401955-6688854.jar}, sha256
 * {@code 1f1debc282a0c379c1141f2840ea294eede6f6b544c589663f40bbe17b59a1af}):
 * {@code com/hollingsworth/arsnouveau/api/spell/SpellResolver.expendMana}, descriptor
 * {@code ()V}. Ars mod classes are not reobfuscated, hence {@code remap = false}.
 *
 * <p>This is the one place a reservation becomes a payment. Everything before it - the cost
 * events, the pre-cast gate's questions - may be asked any number of times and must move
 * nothing. Upstream builds a fresh {@code SpellCostCalcEvent} on every
 * {@code getResolveCost()} call and calls it from both {@code canCast()} and here, so any
 * design that charged during a cost query charged an unpredictable number of times.
 *
 * <p>The native Ars debit is cancelled only when ANS holds a reservation for this cast, i.e.
 * only for the leg ANS has already paid itself. With no open attempt ANS took nothing, and
 * Ars is left to charge its own pool exactly as it always did.
 */
@Mixin(value = SpellResolver.class, remap = false)
public abstract class MixinSpellResolverMana {
    @Shadow public SpellContext spellContext;
    @Shadow public abstract int getResolveCost();

    @Inject(method = "expendMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$expendMana(CallbackInfo ci) {
        // SANCTIFIED LEGACY INTEGRATION: Skip mana consumption for Cursed Ring and Virtue Ring
        // - Cursed Ring: LP was consumed in CursedRingHandler.onSpellResolve
        // - Virtue Ring: Aura was consumed in VirtueRingHandler.onSpellResolve
        if (spellContext != null) {
            LivingEntity ringCaster = spellContext.getUnwrappedCaster();
            if (ringCaster instanceof Player ringPlayer) {
                if (SanctifiedLegacyCompat.isCursedRingCostPathActive(ringPlayer)) {
                    // V23/V24: the LP leg was reserved at the pre-cast gate against this cast's
                    // own attempt. Commit it here, at the same boundary the mana legs commit at,
                    // so the alternative payment settles as a leg of the same transaction rather
                    // than as an unrelated drain in a resolve handler. Commit is idempotent, so
                    // the Post-resolve backstop cannot charge a second time.
                    arsnspells$commitAlternativeLeg(ringPlayer);
                    ci.cancel();
                    return;
                }
                // Must gate on ENABLE_VIRTUE_AURA_SYSTEM, not merely on the ring being worn.
                // VirtueRingHandler checks the toggle before consuming aura; this cancel did
                // not, so with the toggle off nothing took aura and nothing took mana and Ars
                // spells were free. Both halves now read the same predicate.
                if (SanctifiedLegacyCompat.isVirtueAuraCostPathActive(ringPlayer)) {
                    // Same as the LP leg above: the aura was drained into a reservation at the
                    // pre-cast gate, and this is where that reservation becomes payment.
                    arsnspells$commitAlternativeLeg(ringPlayer);
                    ci.cancel();
                    return;
                }
            }
        }

        if (spellContext == null) {
            return;
        }
        LivingEntity caster = spellContext.getUnwrappedCaster();
        if (!(caster instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }

        String carrierIdentity = CastLedger.carrierIdentity(spellContext.getCasterTool());
        Optional<CastAttempt> open = CastLedger.findOpen(player.getUUID(), carrierIdentity);
        if (open.isEmpty()) {
            // ANS never opened an attempt for this cast, so it is holding nothing. Let Ars
            // charge its own pool - cancelling here would make the spell free.
            return;
        }

        CastAttempt attempt = open.get();
        if (attempt.state() != AttemptState.RESERVED) {
            // Quoted but never reserved, or already settled. Nothing has been taken on ANS's
            // behalf, so the native debit must still run.
            return;
        }

        // Commit exactly once. The state machine has no COMMITTED -> COMMITTED edge, so a
        // second arrival here throws rather than silently paying twice.
        CastLedger.commitAndComplete(attempt);
        ci.cancel();

        CrossCastTrace.log(attempt.attemptId(), player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.RESOURCE_SPEND,
            "carrier", carrierIdentity, "paid", attempt.paidLegs());
    }

    /**
     * Settle the alternative payment leg this cast reserved, if it reserved one (audit V23).
     *
     * <p>Idempotent by construction: {@link AlternativePayment#commit} removes the leg, so the
     * ring handlers' Post-resolve backstop finds nothing left to settle. That is what makes a
     * spell whose resolve fires more than once cost its price once.
     */
    @org.spongepowered.asm.mixin.Unique
    private void arsnspells$commitAlternativeLeg(Player player) {
        String carrierIdentity = CastLedger.carrierIdentity(
            spellContext == null ? null : spellContext.getCasterTool());
        CastLedger.findOpen(player.getUUID(), carrierIdentity).ifPresent(attempt -> {
            AlternativePayment.commit(attempt.attemptId());
            // The attempt itself never reserved a mana leg (the ring zeroed the cost), so it is
            // cancelled rather than committed; cancel refunds an empty reservation list.
            if (!attempt.state().isTerminal()) {
                CastLedger.cancel(attempt, CastLedger.forPlayer(player));
            }
        });
    }
}
