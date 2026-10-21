package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellValidationError;
import com.hollingsworth.arsnouveau.common.spell.validation.StandardSpellValidator;
import com.otectus.arsnspells.glyph.ImportedGlyphCompositionValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** 3.4.0: Ars's standard crafting and casting validators also run the imported-glyph checks. */
@Mixin(value = StandardSpellValidator.class, remap = false)
public abstract class MixinStandardSpellValidator {
    @Unique private boolean arsnspells$castTime;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void arsnspells$remember(boolean enforceCastTimeValidations, CallbackInfo ci) {
        this.arsnspells$castTime = enforceCastTimeValidations;
    }

    @Inject(method = "validate", at = @At("RETURN"), cancellable = true)
    private void arsnspells$importedGlyphs(List<AbstractSpellPart> recipe, CallbackInfoReturnable<List<SpellValidationError>> cir) {
        List<SpellValidationError> extra = ImportedGlyphCompositionValidator.validate(recipe, arsnspells$castTime);
        if (extra.isEmpty()) return;
        List<SpellValidationError> errors = new ArrayList<>(cir.getReturnValue());
        errors.addAll(extra);
        cir.setReturnValue(errors);
    }
}
