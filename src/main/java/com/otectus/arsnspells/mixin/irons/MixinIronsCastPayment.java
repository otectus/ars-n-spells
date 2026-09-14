package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.casting.IronsCastPayments;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerRecasts;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Pinned 1.21.1-3.16.3 ordering: cost event -> payment -> onCast. Cancelling here prevents effects. */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class MixinIronsCastPayment {
    @Inject(method = "castSpell", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;",
        shift = At.Shift.AFTER), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD, require = 1)
    private void arsnspells$commitBeforeEffects(Level level, int spellLevel, ServerPlayer player,
            CastSource source, boolean triggerCooldown, CallbackInfo ci, MagicData data,
            PlayerRecasts recasts, boolean isRecast, SpellOnCastEvent event) {
        if (!IronsCastPayments.commit(player, (AbstractSpell)(Object)this, spellLevel, source, data, event)) {
            io.redspace.ironsspellbooks.api.util.Utils.serverSideCancelCast(player);
            ci.cancel();
        } else {
            com.otectus.arsnspells.events.IronsCooldownHandler.commit(player, (AbstractSpell)(Object)this,
                source, IronsCastPayments.isCrossCast(player));
        }
    }
}
