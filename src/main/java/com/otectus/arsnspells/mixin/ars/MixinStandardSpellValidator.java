package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.common.spell.validation.CombinedSpellValidator;
import com.hollingsworth.arsnouveau.common.spell.validation.StandardSpellValidator;
import com.otectus.arsnspells.glyph.GlyphCompositionValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the imported-glyph composition rules to both of Ars's spell validators: the editor's and
 * the one every cast runs. Ars builds each lazily, and the cast-time one reads Ars's server config
 * in its constructor, so the rules are appended where each is built rather than at startup.
 */
@Mixin(value = StandardSpellValidator.class, remap = false)
public abstract class MixinStandardSpellValidator {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void arsnspells$addImportedGlyphRules(boolean enforceCastTimeValidations, CallbackInfo ci) {
        if (((StandardSpellValidator) (Object) this).combinedValidator instanceof CombinedSpellValidator combined
                && !combined.validators.contains(GlyphCompositionValidator.INSTANCE)) {
            combined.validators.add(GlyphCompositionValidator.INSTANCE);
        }
    }
}
