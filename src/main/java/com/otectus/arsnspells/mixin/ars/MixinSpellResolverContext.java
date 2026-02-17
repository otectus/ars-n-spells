package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.util.CasterContext;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SpellResolver.class, remap = false)
public class MixinSpellResolverContext {
    // Added require = 0 for stability. This mixin captures casting context for scaling logic.
    @Inject(method = "resolve", at = @At("HEAD"), require = 0)
    private void arsnspells$captureContext(SpellContext context, CallbackInfoReturnable<Boolean> cir) {
        if (context.getCaster() instanceof Player player) {
            SpellResolver resolver = (SpellResolver) (Object) this;
            CasterContext.set(player, resolver.spell);
        }
    }

    @Inject(method = "resolve", at = @At("RETURN"), require = 0)
    private void arsnspells$clearContext(SpellContext context, CallbackInfoReturnable<Boolean> cir) {
        CasterContext.clear();
    }
}