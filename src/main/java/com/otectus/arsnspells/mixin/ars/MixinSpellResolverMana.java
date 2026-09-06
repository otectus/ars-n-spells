package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.casting.AttemptLedgerService;
import com.otectus.arsnspells.casting.CastingAuthority;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.contract.CastAttempt;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.spell.CrossCastContext;
import com.otectus.arsnspells.spell.CrossSpellType;
import com.otectus.arsnspells.util.CrossCastTrace;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The verified native payment boundary for an Ars cast (audit V01).
 *
 * <p>{@code SpellResolver.expendMana()} is the one place Ars actually moves mana, and Ars 5.13.1
 * calls it from exactly one site: {@code onCast} invokes it only after
 * {@code AbstractCastMethod.onCast} has returned {@code CastResolveType.SUCCESS}. That makes it
 * the honest commit point. Cost <em>queries</em> - {@code getResolveCost()} and
 * {@code getExpendedCost()}, each of which posts a fresh {@code SpellCostCalcEvent} - are asked
 * many times per cast and must move nothing; the cost-calc handler therefore only prices, and
 * this is the only seam that pays.
 *
 * <p>Two things happen here, in this order:
 *
 * <ol>
 *   <li><b>Commit the reservation, exactly once.</b> The pre-cast mixin already reserved the
 *       attempt's quoted legs, so the mana has been held since before the spell ran. Committing
 *       turns the hold into a payment; a second call is a no-op because {@code commit} only acts
 *       on a {@code RESERVED} attempt.</li>
 *   <li><b>Suppress the native Ars debit only for the leg ANS pays itself.</b> Cancelling
 *       wholesale would make a cast free in a mode where Ars is still the payer; not cancelling
 *       at all double-charges the leg ANS has just committed.</li>
 * </ol>
 *
 * <p>Verified against the pinned Ars 5.13.1.1400: {@code SpellResolver.spellContext} (public
 * field), {@code getResolveCost()} (public, {@code ()I}) and {@code expendMana()} (public,
 * {@code ()V}) all match the shadows below.
 */
@Mixin(value = SpellResolver.class, remap = false)
public abstract class MixinSpellResolverMana {
    @Shadow public SpellContext spellContext;
    @Shadow public abstract int getResolveCost();

    @Inject(method = "expendMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$expendMana(CallbackInfo ci) {
        if (spellContext == null) {
            return;
        }
        LivingEntity caster = spellContext.getUnwrappedCaster();
        if (!(caster instanceof Player player) || player.level().isClientSide()) {
            return;
        }

        // Commit first, and unconditionally: a reservation exists only because this cast was
        // going to happen, and reaching expendMana is Ars confirming that it did.
        boolean ansPaidThisLeg = arsnspells$commitOpenAttempt(player);

        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        boolean ansOwnsTheArsLeg = mode != null && (mode.isIssPrimary() || mode.isHybrid());
        if (!ansOwnsTheArsLeg && !ansPaidThisLeg) {
            // Ars still owns this leg and ANS has not paid it. Let the native debit run.
            return;
        }

        if (ansPaidThisLeg) {
            // The ledger has already moved the mana for this cast. Running the native debit as
            // well is the double-spend BillingRoute.nativeAlsoDebits exists to make impossible.
            ci.cancel();
            CrossCastTrace.log(arsnspells$traceId(player), player, CrossCastTrace.Side.S,
                CrossCastTrace.Stage.RESOURCE_SPEND, "mode", mode, "paidBy", "ledger");
            return;
        }

        // A native Ars cast in a mode where the Iron's pool is authoritative: no attempt was
        // opened, so ANS charges the converted amount here and suppresses the native debit.
        // Same helper CastingAuthority validates with, so the amount charged is exactly the
        // amount checked. This used to round to an int here and not there, which at the
        // config's 0.01 rate floor rounded every spell under 50 mana down to free.
        float cost = CastingAuthority.effectiveArsCost(Math.max(0, getResolveCost()));
        boolean consumed = cost <= 0.0f
            || BridgeManager.consumeManaForMode(player, cost, ResourceUnit.ARS_MANA);
        // ANS-MED-010: cancel even when the consume failed. Otherwise Ars's native expendMana
        // runs afterwards against possibly-stale ManaCap data and decrements the Ars pool too,
        // double-charging the player for one cast.
        ci.cancel();

        CrossCastTrace.log(arsnspells$traceId(player), player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.RESOURCE_SPEND,
            "mode", mode, "cost", cost, "consumed", consumed);
    }

    /**
     * Commit the open attempt for this player's carrier, if there is one.
     *
     * @return whether ANS holds a committed payment for this cast, i.e. whether the native debit
     *         must be suppressed
     */
    @Unique
    private static boolean arsnspells$commitOpenAttempt(Player player) {
        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        if (entry == null || entry.type != CrossSpellType.ARS_NOUVEAU
            || entry.carrierIdentity == null) {
            return false;
        }
        CastAttempt attempt =
            AttemptLedgerService.findOpen(player, entry.carrierIdentity).orElse(null);
        if (attempt == null) {
            return false;
        }
        // Idempotent: commit() only advances a RESERVED attempt, so a second expendMana on the
        // same attempt (a recast leg) finds it already COMMITTED and moves nothing.
        AttemptLedgerService.commit(attempt);
        return !attempt.paidLegs().isEmpty();
    }

    @Unique
    private static java.util.UUID arsnspells$traceId(Player player) {
        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        return entry != null ? entry.attemptId : null;
    }
}
