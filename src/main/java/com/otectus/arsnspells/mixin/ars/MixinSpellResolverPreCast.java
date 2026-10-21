package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.casting.ArsCastPayments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ars 5.13.1: native validator -> enoughMana -> postEvent -> cast method -> expendMana. */
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
    private void arsnspells$reserveAfterEvent(CallbackInfoReturnable<com.hollingsworth.arsnouveau.api.event.SpellCastEvent> cir) {
        if (spellContext == null || !(spellContext.getUnwrappedCaster() instanceof Player player)
                || player.level().isClientSide()) return;
        if (cir.getReturnValue().isCanceled()) {
            ArsCastPayments.finish(spellContext);
            com.otectus.arsnspells.glyph.runtime.GlyphSessions.rootFinished(spellContext, true);
        } else if (!ArsCastPayments.prepare(player, spellContext)) {
            ArsCastPayments.finish(spellContext);
            // Imported-glyph reservations made at this cast's admission go with the Ars leg.
            com.otectus.arsnspells.glyph.runtime.GlyphSessions.rootFinished(spellContext, true);
            cir.getReturnValue().setCanceled(true); // Ars inspects this event before invoking the cast method.
        }
    }

    // Full descriptors: a bare "onCastOnBlock" binds only one of its two overloads, and the
    // UseOnContext one is the overload Ars's own player casting and ANS's dispatcher use.
    @Inject(method = {
        "onCast(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Z",
        "onCastOnEntity(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;)Z",
        "onCastOnBlock(Lnet/minecraft/world/phys/BlockHitResult;)Z",
        "onCastOnBlock(Lnet/minecraft/world/item/context/UseOnContext;)Z"
    }, at = @At("RETURN"), require = 4)
    private void arsnspells$finishCast(CallbackInfoReturnable<?> cir) {
        // Includes native failure, downstream event veto, and SUCCESS_NO_EXPEND.
        ArsCastPayments.finish(spellContext);
        // Projectiles and delays carrying imported glyphs keep their session open past this point.
        com.otectus.arsnspells.glyph.runtime.GlyphSessions.rootFinished(spellContext, false);
    }
}