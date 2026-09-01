package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.IManaBridge;
import com.otectus.arsnspells.casting.CastingAuthority;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.spell.CrossCastContext;
import com.otectus.arsnspells.spell.CrossSpellType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.otectus.arsnspells.util.CrossCastTrace;

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
            LivingEntity caster = spellContext.getUnwrappedCaster();
            if (caster instanceof Player player) {
                if (SanctifiedLegacyCompat.isCursedRingCostPathActive(player)) {
                    // LP was consumed in CursedRingHandler.onSpellResolve.
                    ci.cancel();
                    return;
                }
                // Must gate on ENABLE_VIRTUE_AURA_SYSTEM, not merely on the ring being worn.
                // VirtueRingHandler checks the toggle before consuming aura; this cancel did
                // not, so with the toggle off nothing took aura and nothing took mana and Ars
                // spells were free. Both halves now read the same predicate.
                if (SanctifiedLegacyCompat.isVirtueAuraCostPathActive(player)) {
                    // Aura was already consumed via SanctifiedLegacyCompat.consumeCovenantAura
                    // in VirtueRingHandler's SpellResolveEvent.Post hook.
                    ci.cancel();
                    return;
                }
            }
        }
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode == null || !(mode.isIssPrimary() || mode.isHybrid())) {
            return;
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

        // Same helper CastingAuthority validated with, so the amount charged is exactly the
        // amount checked. This used to round to an int here and not there, which at low
        // conversion rates rounded small spells down to free.
        float cost = CastingAuthority.effectiveArsCost(Math.max(0, getResolveCost()));
        if (cost <= 0.0f) {
            // Cancel like every other exit does. Falling through ran Ars's native
            // expendMana, which is the one path in this method that did not.
            ci.cancel();
            return;
        }

        boolean consumed = BridgeManager.consumeManaForMode(player, cost, true);
        // ANS-MED-010: cancel even on consume failure. Otherwise the upstream Ars
        // native expendMana would run with stale ManaCap data and decrement the Ars
        // pool, even though our bridge already failed — producing a double-deduct
        // when the bridge returns later in a successful state.
        ci.cancel();

        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        java.util.UUID attemptId = entry != null ? entry.attemptId : null;
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.RESOURCE_SPEND,
            "mode", mode, "cost", cost, "consumed", consumed);
    }

    /**
     * ANS-CRIT-002: this TAIL hook used to consume the Iron's side of a SEPARATE-mode
     * cross-cast AFTER Ars had already drained, and silently swallowed consume failures,
     * producing a one-way Ars drain when Iron's was empty. The Iron's side is now
     * pre-consumed atomically with the Ars cost-calc in CrossCastingHandler.onArsSpellCost,
     * which sets entry.issCost to 0 to signal "already paid". This TAIL now exists only
     * to drain the context entry from ACTIVE_CASTS once the Ars resolve completes.
     */
    @Inject(method = "expendMana", at = @At("TAIL"))
    private void arsnspells$consumeCrossCastSecondary(CallbackInfo ci) {
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode != ManaUnificationMode.SEPARATE) {
            return;
        }
        if (spellContext == null) {
            return;
        }
        LivingEntity caster = spellContext.getUnwrappedCaster();
        if (!(caster instanceof Player player)) {
            return;
        }
        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        if (entry == null || entry.type != CrossSpellType.ARS_NOUVEAU) {
            return;
        }
        // Drain the context entry. entry.issCost will be 0 in the happy path (already
        // pre-consumed by onArsSpellCost); the take() is just lifecycle cleanup.
        CrossCastContext.take(player);
        if (player.isCreative() || entry.issCost <= 0.0f) {
            return;
        }
        // Defensive: if for some reason a cross-cast entry reached TAIL with issCost > 0
        // (e.g. an upstream future bug skipped the pre-consume), still pay it here AND
        // log a warning so the regression is visible.
        IManaBridge issBridge = BridgeManager.getSecondaryBridge();
        if (issBridge == null) {
            return;
        }
        boolean consumed = issBridge.consumeMana(player, entry.issCost);
        if (!consumed) {
            org.slf4j.LoggerFactory.getLogger(MixinSpellResolverMana.class)
                .warn("Cross-cast Iron's-side consume failed at TAIL for {}: needed {}; pre-consume should have handled this",
                    player.getName().getString(), entry.issCost);
        }
    }
}
