package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.IManaBridge;
import com.otectus.arsnspells.config.AnsConfig;
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

/**
 * DISABLED in ars_n_spells.mixins.json pending Phase 2 verification against
 * Ars Nouveau 5.11.4. Re-enable in the config once `@Shadow public
 * SpellContext spellContext` and `@Shadow public abstract int
 * getResolveCost()` are confirmed against the current `SpellResolver`
 * class. The 1.21.1 hotfix on 2026-05-10 disabled this preemptively
 * alongside `MixinManaCapability`, since both belong to the same Ars
 * mana-bridge family that has drifted between 4.12 and 5.x.
 *
 * Phase 2 work: verify `spellContext` field is still public on
 * SpellResolver; verify `expendMana()` method still exists; if either
 * has moved, replace with a `SpellCostCalcEvent` / `SpellCastEvent.Pre`
 * listener.
 */
@Mixin(value = SpellResolver.class, remap = false)
public abstract class MixinSpellResolverMana {
    @Shadow public SpellContext spellContext;
    @Shadow public abstract int getResolveCost();

    @Inject(method = "expendMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$expendMana(CallbackInfo ci) {
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

        int cost = Math.max(0, getResolveCost());
        double conversionRate = AnsConfig.CONVERSION_RATE_ARS_TO_IRON.get();
        cost = (int) Math.round(cost * conversionRate);
        if (cost == 0) {
            return;
        }

        boolean consumed = BridgeManager.consumeManaForMode(player, cost, true);
        if (consumed) {
            ci.cancel();
        }
    }

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
        CrossCastContext.take(player);
        if (player.isCreative() || entry.issCost <= 0.0f) {
            return;
        }
        IManaBridge issBridge = BridgeManager.getSecondaryBridge();
        if (issBridge == null) {
            return;
        }
        boolean consumed = issBridge.consumeMana(player, entry.issCost);
        if (!consumed) {
            // No cancel path here; log in debug to avoid spam
        }
    }
}
