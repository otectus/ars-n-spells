package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.spell.CrossCastContext;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DISABLED in ars_n_spells.mixins.json pending Phase 2 verification against
 * Iron's Spells 'n Spellbooks 3.15.6. Re-enable in the config once
 * `@Shadow private float mana` and `@Shadow private ServerPlayer
 * serverPlayer` are confirmed against the current `MagicData` class.
 * The 1.21.1 hotfix on 2026-05-10 disabled this preemptively after
 * `MixinManaCapability` failed for the symmetric reason on the Ars side
 * — Iron's 3.15.6 may have renamed/retyped either shadow field, and a
 * load-time `InvalidMixinException` would abort Iron's mod load the same
 * way Ars's was aborted.
 *
 * Phase 2 work: decompile Iron's 3.15.6 `MagicData` and confirm field
 * names + types; or replace with Iron's `SpellOnCastEvent` /
 * `SpellPreCastEvent` listeners that don't require shadowing internals.
 */
@Mixin(value = MagicData.class, remap = false)
public abstract class MixinIronsMagicDataMana {
    @Shadow private float mana;
    @Shadow private ServerPlayer serverPlayer;

    @Inject(method = "getMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$getMana(CallbackInfoReturnable<Float> cir) {
        ServerPlayer player = serverPlayer;
        if (player == null) {
            return;
        }

        CrossCastContext.ManaCheckOverride override = CrossCastContext.getManaCheckOverride(player);
        if (override != null) {
            if (override.isUnlimited()) {
                cir.setReturnValue(Float.MAX_VALUE);
                return;
            }
            if (override.issPercent > 0.0f && Math.abs(override.issPercent - 1.0f) > 1.0e-4f) {
                cir.setReturnValue(mana / override.issPercent);
                return;
            }
        }

        if (!shouldRedirectToArs()) {
            return;
        }
        cir.setReturnValue(BridgeManager.getBridge().getMana(player));
    }

    @Inject(method = "setMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$setMana(float amount, CallbackInfo ci) {
        ServerPlayer player = serverPlayer;
        if (player == null) {
            return;
        }
        if (!shouldRedirectToArs()) {
            return;
        }
        BridgeManager.getBridge().setMana(player, amount);
        this.mana = BridgeManager.getBridge().getMana(player);
        ci.cancel();
    }

    @Inject(method = "addMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$addMana(float amount, CallbackInfo ci) {
        ServerPlayer player = serverPlayer;
        if (player == null) {
            return;
        }
        if (!shouldRedirectToArs()) {
            return;
        }
        float current = BridgeManager.getBridge().getMana(player);
        BridgeManager.getBridge().setMana(player, current + amount);
        this.mana = BridgeManager.getBridge().getMana(player);
        ci.cancel();
    }

    private static boolean shouldRedirectToArs() {
        if (!BridgeManager.isUnificationEnabled()) {
            return false;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        return mode == ManaUnificationMode.ARS_PRIMARY;
    }
}
