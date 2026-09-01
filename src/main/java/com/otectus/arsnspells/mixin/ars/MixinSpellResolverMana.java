package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.IManaBridge;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.spell.CrossCastContext;
import com.otectus.arsnspells.spell.CrossSpellType;
import com.otectus.arsnspells.casting.CastingAuthority;
import com.otectus.arsnspells.util.CrossCastTrace;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes Ars Nouveau's spell-mana consumption through {@link BridgeManager}.
 * HEAD inject on {@code expendMana} cancels the native consumption when
 * the active mode is ISS_PRIMARY / HYBRID (Iron's pays the cost via
 * {@link com.otectus.arsnspells.bridge.IronsBridge#consumeMana}); TAIL
 * inject handles SEPARATE mode's secondary-pool drain for cross-cast.
 *
 * Verified against Ars 5.11.4: {@code SpellResolver.spellContext} (public),
 * {@code SpellResolver.getResolveCost()} (public, int), and
 * {@code SpellResolver.expendMana()} (public, void) all match the
 * shadows declared below.
 */
@Mixin(value = SpellResolver.class, remap = false)
public abstract class MixinSpellResolverMana {
    @Shadow public SpellContext spellContext;
    @Shadow public abstract int getResolveCost();

    @Inject(method = "expendMana", at = @At("HEAD"), cancellable = true, require = 0)
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

        // Same helper CastingAuthority validates with, so the amount charged is exactly the
        // amount checked. This used to round to an int here and not there, which at the
        // config's 0.01 rate floor rounded every spell under 50 mana down to free.
        float cost = CastingAuthority.effectiveArsCost(Math.max(0, getResolveCost()));
        if (cost <= 0.0f) {
            // Cancel like every other exit does. Falling through ran Ars's native expendMana,
            // which is the one path in this method that did not.
            ci.cancel();
            return;
        }

        boolean consumed = BridgeManager.consumeManaForMode(player, cost, true);
        // ANS-MED-010: cancel even when the consume failed. Otherwise Ars's native expendMana
        // runs afterwards against possibly-stale ManaCap data and decrements the Ars pool too,
        // double-charging the player for one cast.
        ci.cancel();

        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        java.util.UUID attemptId = entry != null ? entry.attemptId : null;
        CrossCastTrace.log(attemptId, player, CrossCastTrace.Side.S,
            CrossCastTrace.Stage.RESOURCE_SPEND,
            "mode", mode, "cost", cost, "consumed", consumed);
    }

    @Inject(method = "expendMana", at = @At("TAIL"), require = 0)
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
