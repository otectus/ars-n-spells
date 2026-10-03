package com.otectus.arsnspells.mixin.irons;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.otectus.arsnspells.casting.IronsCastLifecycle;
import com.otectus.arsnspells.casting.IronsCastPayments;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A cancelled callee must not fall through to native scroll consumption and completion. */
@Mixin(value = MagicManager.class, remap = false)
public abstract class MixinIronsCastTicker {
    @WrapMethod(method = "lambda$tick$0", require = 1, allow = 1)
    private void arsnspells$caller(boolean flag, Player player, Operation<Void> original) {
        try { original.call(flag, player); }
        finally { if (player instanceof ServerPlayer server) IronsCastLifecycle.clearCaller(server); }
    }
    @Inject(method = "lambda$tick$0", at = @At(value = "INVOKE",
        target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;castSpell(Lnet/minecraft/world/level/Level;ILnet/minecraft/server/level/ServerPlayer;Lio/redspace/ironsspellbooks/api/spells/CastSource;Z)V",
        shift = At.Shift.AFTER), cancellable = true, require = 3, allow = 3)
    private void arsnspells$stopAfterRejectedEffect(boolean flag, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer server)) return;
        if (IronsCastLifecycle.aborted(server)) ci.cancel();
        // A paid pulse that left too little for the next one is this channel's final pulse.
        // Nothing native follows an intermediate pulse's invocation, so the tick continues.
        else IronsCastLifecycle.completeExhaustedChannel(server);
    }
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "lambda$tick$0", at = @At(value = "INVOKE",
        target = "Lio/redspace/ironsspellbooks/api/spells/CastSource;consumesMana()Z"), require = 1, allow = 1)
    private boolean arsnspells$deferChannelAffordability(io.redspace.ironsspellbooks.api.spells.CastSource source,
            Operation<Boolean> original, @Local(argsOnly = true) Player player) {
        // The ticker's base-cost * 2 forecast precedes final cost listeners and reads the
        // Iron's pool in Iron's units, so it is wrong for a routed or converted payer. When ANS
        // prices this channel, IronsCastLifecycle.afterEffect applies the same rule to the
        // exact price each pulse paid; exempt casts keep the native forecast.
        MagicData data = MagicData.getPlayerMagicData(player);
        return IronsCastPayments.ownsChannelAffordability(data) == null && original.call(source);
    }
}
