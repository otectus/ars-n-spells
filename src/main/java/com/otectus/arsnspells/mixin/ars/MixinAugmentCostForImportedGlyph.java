package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.otectus.arsnspells.glyph.ImportedIronEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 3.4.0: an augment placed after an imported Iron's glyph adds nothing to the Ars cost. Amplify
 * and Dampen on an imported glyph change the Iron's spell's cast level, and that level's own
 * Iron's cost is what the imported leg charges; charging the Ars augment price as well would bill
 * the same amplification twice.
 */
@Mixin(value = AbstractAugment.class, remap = false)
public abstract class MixinAugmentCostForImportedGlyph {
    @Inject(method = "getCostForPart", at = @At("HEAD"), cancellable = true)
    private void arsnspells$importedGlyphAugmentsAreIronsPriced(AbstractSpellPart part, CallbackInfoReturnable<Integer> cir) {
        if (part instanceof ImportedIronEffect) cir.setReturnValue(0);
    }
}
