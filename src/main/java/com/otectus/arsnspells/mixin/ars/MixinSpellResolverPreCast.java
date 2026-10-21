package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.casting.ArsCastPayments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ars 4.12.7: native validator -> enoughMana -> postEvent -> cast method -> expendMana. */
@Mixin(value = SpellResolver.class, remap = false)
public abstract class MixinSpellResolverPreCast {
    @Shadow public SpellContext spellContext;
    @Shadow public abstract int getResolveCost();

    @Inject(method = "enoughMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$validateResources(LivingEntity caster, CallbackInfoReturnable<Boolean> cir) {
        if (!(caster instanceof Player player) || player.level().isClientSide()) return;
        getResolveCost();
        if (ArsCastPayments.handles(spellContext)) {
            cir.setReturnValue(ArsCastPayments.canAfford(player, (SpellResolver)(Object)this));
        }
    }

    @Inject(method = "postEvent", at = @At("RETURN"), cancellable = true)
    private void arsnspells$reserveAfterEvent(CallbackInfoReturnable<Boolean> cir) {
        if (spellContext == null || !(spellContext.getUnwrappedCaster() instanceof Player player)
                || player.level().isClientSide()) return;
        if (cir.getReturnValueZ()) {
            ArsCastPayments.finish(spellContext);
        } else if (!ArsCastPayments.prepare(player, spellContext)) {
            ArsCastPayments.finish(spellContext);
            cir.setReturnValue(true); // Ars interprets true as a cancelled event.
        }
    }

    /**
     * Every resolver entry, by exact descriptor. 3.4.0: the 3.3.x selector named
     * {@code onCastOnBlock} without a descriptor, and Mixin binds such a selector to the first
     * overload only ({@code BlockHitResult}). Ars's player caster uses the {@code UseOnContext}
     * overload, so every player block cast skipped this hook: its payment plan stayed open until
     * the end of the server tick and the cross-cast handler saw it as unresolved even though its
     * effect had applied (the Iron's wheel route then cancelled the proxy's cooldown). Verified
     * against Ars Nouveau 4.12.7, whose resolver declares exactly these four entries. Each entry
     * has two return points (resolved, refused), so 4.12.7 takes eight injections; {@code require}
     * only guarantees that the selectors resolve, and {@code TouchRegressionGameTests} checks
     * that each block overload actually reaches the hook.
     */
    @Inject(method = {
        "onCast(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Z",
        "onCastOnEntity(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;)Z",
        "onCastOnBlock(Lnet/minecraft/world/phys/BlockHitResult;)Z",
        "onCastOnBlock(Lnet/minecraft/world/item/context/UseOnContext;)Z"
    }, at = @At("RETURN"), require = 4)
    private void arsnspells$finishCast(CallbackInfoReturnable<?> cir) {
        // Includes native failure, downstream event veto, and SUCCESS_NO_EXPEND.
        ArsCastPayments.finish(spellContext);
        // 3.4.0: a root that did not resolve has no imported-glyph deliveries in flight.
        if (spellContext != null && cir.getReturnValue() instanceof Boolean ok)
            com.otectus.arsnspells.glyph.GlyphSessions.rootReturned(spellContext, ok);
        if (spellContext != null && spellContext.getUnwrappedCaster() instanceof Player player
                && !player.level().isClientSide() && cir.getReturnValue() instanceof Boolean resolved)
            com.otectus.arsnspells.spell.CrossCastContext.recordResolution(player, resolved);
    }
}