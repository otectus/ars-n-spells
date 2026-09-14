package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.spell.CastValidationScope;
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
 * Routes Iron's MagicData reads / writes through {@link BridgeManager}
 * when the active mode is ARS_PRIMARY (Ars is the source of truth).
 * Also handles cross-cast mana-check overrides via {@link CrossCastContext}.
 *
 * Verified against Iron's Spells 'n Spellbooks 3.15.6:
 * {@code MagicData.mana} (private float), {@code MagicData.serverPlayer}
 * (private ServerPlayer), and the three intercepted methods (getMana,
 * setMana, addMana) all match the shadows + injections declared below.
 */
@Mixin(value = MagicData.class, remap = false)
public abstract class MixinIronsMagicDataMana {
    @Shadow private float mana;
    @Shadow private ServerPlayer serverPlayer;

    @Inject(method = "getMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$getMana(CallbackInfoReturnable<Float> cir) {
        ServerPlayer player = serverPlayer;
        if (player == null || com.otectus.arsnspells.bridge.NativeManaAccess.active(player,
                com.otectus.arsnspells.contract.ResourceUnit.IRONS_MANA)) {
            return;
        }

        CrossCastContext.ManaCheckOverride override = CrossCastContext.getManaCheckOverride(player);
        if (override != null) {
            if (override.isUnlimited()) {
                cir.setReturnValue(CastValidationScope.apply(this, Float.MAX_VALUE));
                return;
            }
            if (override.issPercent > 0.0f && Math.abs(override.issPercent - 1.0f) > 1.0e-4f) {
                cir.setReturnValue(CastValidationScope.apply(this, mana / override.issPercent));
                return;
            }
        }

        if (!shouldRedirectToArs()) {
            // Fall through to the real body. Any cast-gate adjustment is applied by
            // arsnspells$scaleManaForCastGate on the way out, so that it acts on whatever
            // the body actually returned - including another mod's overwritten value.
            return;
        }
        cir.setReturnValue(CastValidationScope.apply(this, BridgeManager.getBridge().getMana(player)));
    }

    /**
     * Applies the cast-gate adjustment to the value the real {@code getMana} body produced.
     *
     * <p>This is the second, independent layer of the {@code canBeCastedBy} rewrite. The HEAD
     * hook above covers the case where ANS supplies the value; this covers the case where the
     * real body does - including a body another mod has overwritten, which is the situation
     * {@link CastValidationScope} exists to survive.
     *
     * <p>It only runs when {@link #arsnspells$getMana} did <em>not</em> cancel: a cancelling
     * HEAD callback returns before the body's RETURN instructions are reached, so the two
     * hooks never both fire for one call and the adjustment is applied exactly once either way.
     */
    @Inject(method = "getMana", at = @At("RETURN"), cancellable = true, require = 0)
    private void arsnspells$scaleManaForCastGate(CallbackInfoReturnable<Float> cir) {
        if (serverPlayer == null || com.otectus.arsnspells.bridge.NativeManaAccess.active(serverPlayer,
                com.otectus.arsnspells.contract.ResourceUnit.IRONS_MANA)) {
            return;
        }
        if (!CastValidationScope.isActive(this)) {
            return;
        }
        float value = cir.getReturnValueF();
        float adjusted = CastValidationScope.apply(this, value);
        if (adjusted != value) {
            cir.setReturnValue(adjusted);
        }
    }

    @Inject(method = "setMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$setMana(float amount, CallbackInfo ci) {
        ServerPlayer player = serverPlayer;
        if (player == null || com.otectus.arsnspells.bridge.NativeManaAccess.active(player,
                com.otectus.arsnspells.contract.ResourceUnit.IRONS_MANA)) {
            return;
        }
        if (!shouldRedirectToArs()) {
            return;
        }
        BridgeManager.getBridge().setMana(player, amount);

        ci.cancel();
    }

    @Inject(method = "addMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$addMana(float amount, CallbackInfo ci) {
        ServerPlayer player = serverPlayer;
        if (player == null || com.otectus.arsnspells.bridge.NativeManaAccess.active(player,
                com.otectus.arsnspells.contract.ResourceUnit.IRONS_MANA)) {
            return;
        }
        if (!shouldRedirectToArs()) {
            return;
        }
        // Delegate to the bridge's atomic add - do NOT get+set here or we lose any concurrent
        // regen/buff landing between the read and the write (the exact race
        // ArsNativeBridge/IronsBridge.addMana were written to avoid).
        BridgeManager.getBridge().addMana(player, amount);

        ci.cancel();
    }

    @Inject(method = "resetCastingState", at = @At("RETURN"), require = 1)
    private void arsnspells$clearPayment(CallbackInfo ci) {
        com.otectus.arsnspells.casting.IronsCastPayments.clear((MagicData)(Object)this);
    }

    private static boolean shouldRedirectToArs() {
        if (!BridgeManager.isUnificationEnabled()) {
            return false;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        return mode == ManaUnificationMode.ARS_PRIMARY;
    }
}
