package com.otectus.arsnspells.mixin.irons;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.otectus.arsnspells.casting.*;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/**
 * Iron's {@code castSpell}, verified against release bytecode (1.21.1-3.16.3): final cost event,
 * native mana block ({@code getMana} then {@code setMana}), {@code onCast}, native cooldown.
 *
 * <p>ANS prices the cast after the event and pays at Iron's {@code setMana}, which the payment
 * replaces. Another mod acting inside the mana block, such as Animus's affordability check and
 * blood-magic top-up, therefore sees the pool exactly as Iron's would.
 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class MixinIronsCastPayment {
    @WrapMethod(method = "castSpell", require = 1, allow = 1)
    private void arsnspells$invocation(Level level, int spellLevel, ServerPlayer player, CastSource source,
                                      boolean cooldown, Operation<Void> original) {
        var frame = IronsCastLifecycle.enter(player, (AbstractSpell)(Object)this, spellLevel, source, cooldown);
        try {
            if (frame.outcome != null && !frame.outcome.allowed()) { IronsCastLifecycle.abort(frame, null); return; }
            IronsCastPayments.ensurePlan(player, (AbstractSpell)(Object)this, spellLevel, source);
            original.call(level, spellLevel, player, source, cooldown);
            if (!frame.started && !frame.aborted) IronsCastLifecycle.notReached(frame);
            else IronsCastLifecycle.afterEffect(frame);
        } catch (IronsCastLifecycle.PaymentRefused refused) { IronsCastLifecycle.abort(frame, null); }
        catch (RuntimeException error) { IronsCastLifecycle.abort(frame, error); }
        finally { IronsCastLifecycle.exit(frame); }
    }
    @Inject(method = "castSpell", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;",
        shift = At.Shift.AFTER), cancellable = true, require = 1, allow = 1)
    private void arsnspells$priceAfterEvent(Level level, int spellLevel, ServerPlayer player,
            CastSource source, boolean triggerCooldown, CallbackInfo ci,
            @Local MagicData data, @Local SpellOnCastEvent event) {
        var frame = IronsCastLifecycle.current();
        frame.outcome = IronsCastPayments.price(player, (AbstractSpell)(Object)this, spellLevel, source, data, event);
        if (!frame.outcome.allowed()) { IronsCastLifecycle.abort(frame, null); ci.cancel(); }
    }
    @WrapOperation(method = "castSpell", at = @At(value = "INVOKE",
        target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;setMana(F)V"), require = 1, allow = 1)
    private void arsnspells$nativeWrite(MagicData data, float amount, Operation<Void> original) {
        // Throws IronsCastLifecycle.PaymentRefused when the price cannot be paid.
        if (IronsCastLifecycle.nativeWrite(data)) original.call(data, amount);
    }
    @WrapOperation(method = "castSpell", at = @At(value = "INVOKE",
        target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;onCast(Lnet/minecraft/world/level/Level;ILnet/minecraft/world/entity/LivingEntity;Lio/redspace/ironsspellbooks/api/spells/CastSource;Lio/redspace/ironsspellbooks/api/magic/MagicData;)V"), require = 1, allow = 1)
    private void arsnspells$effect(AbstractSpell spell, Level world, int level, LivingEntity caster, CastSource source,
                                  MagicData data, Operation<Void> original) {
        IronsCastLifecycle.startEffect();
        original.call(spell, world, level, caster, source, data);
        IronsCastLifecycle.finishEffect();
    }
}
