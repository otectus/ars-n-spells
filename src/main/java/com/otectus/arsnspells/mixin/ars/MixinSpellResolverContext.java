package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.util.CasterContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DISABLED in ars_n_spells.mixins.json pending Phase 2 verification against
 * Ars Nouveau 5.11.4. Re-enable in the config once `@Shadow public
 * SpellContext spellContext` and `canCast(LivingEntity)` are confirmed
 * against the current `SpellResolver`. The 1.21.1 hotfix on 2026-05-10
 * disabled this preemptively — same drift-risk family as
 * `MixinSpellResolverMana`.
 *
 * Purpose: captures the spell context for use by cost calculation
 * handlers. Injects into canCast() which is called by all cast paths and
 * triggers SpellCostCalcEvent via getResolveCost() -> enoughMana().
 *
 * Phase 2 work: verify field + method signatures against the actual
 * upstream jar.
 */
@Mixin(value = SpellResolver.class, remap = false)
public class MixinSpellResolverContext {
    @Shadow public SpellContext spellContext;

    @Inject(method = "canCast", at = @At("HEAD"))
    private void arsnspells$captureContext(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player) {
            SpellResolver resolver = (SpellResolver) (Object) this;
            CasterContext.set(player, resolver.spell);
        }
    }

    @Inject(method = "canCast", at = @At("RETURN"))
    private void arsnspells$clearContext(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        CasterContext.clear();
    }
}
