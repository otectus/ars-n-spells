package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDampen;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Amplify and Dampen on an imported glyph change its effective Iron's level, and Iron's prices
 * that level in the imported leg. They therefore cost nothing in the Ars leg; otherwise the same
 * potency would be charged twice. Every other augment, and these two on any other part, keep
 * Ars's price.
 */
@Mixin(value = AbstractAugment.class, remap = false)
public abstract class MixinImportedAugmentCost {

    @Inject(method = "getCostForPart", at = @At("HEAD"), cancellable = true)
    private void arsnspells$importedLevelAugmentsArePricedByIrons(AbstractSpellPart part, CallbackInfoReturnable<Integer> cir) {
        Object self = this;
        if (part instanceof ImportedIronEffect && (self == AugmentAmplify.INSTANCE || self == AugmentDampen.INSTANCE)) {
            cir.setReturnValue(0);
        }
    }
}
