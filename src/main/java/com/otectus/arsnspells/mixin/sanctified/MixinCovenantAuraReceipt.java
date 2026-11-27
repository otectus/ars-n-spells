package com.otectus.arsnspells.mixin.sanctified;

import com.otectus.arsnspells.glyph.ImportedExecutionScope;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The receipt for a Covenant-paid imported glyph (audit ANS-001).
 *
 * <p>Covenant 2.2.6-hotfix's {@code ISSSpellEvents.virtueRingCastingEvent} returns early unless the
 * caster wears its own Virtue Ring without the Cursed Ring, and otherwise drains ambient aura through
 * Nature's Aura's {@code IAuraChunk.drainAura}. Reaching that call is the only evidence that Covenant
 * charged anything, so the imported execution's aura route records it there; an aura-route execution
 * without the receipt is refused rather than marked paid. Native casts have no open frame and are
 * not affected.
 *
 * <p>An instruction-level injection point, kept apart from {@link MixinCovenantSpellEvents} (whose
 * HEAD injections stay priority-immune) and listed in the non-required compat config: if another mod
 * overwrites the handler this mixin alone fails to apply, and imported aura executions are then
 * refused, which is the safe direction. Gated by the config plugin on Covenant and Iron's presence.
 */
@Pseudo
@Mixin(value = {}, targets = "net.llenzzz.covenant_of_the_seven.events.ISSSpellEvents", remap = false, priority = 1500)
public abstract class MixinCovenantAuraReceipt {
    @Inject(method = "virtueRingCastingEvent",
        at = @At(value = "INVOKE", target = "Lde/ellpeck/naturesaura/api/aura/chunk/IAuraChunk;drainAura(Lnet/minecraft/core/BlockPos;I)I"),
        require = 0)
    private static void arsnspells$recordAuraReceipt(SpellPreCastEvent event, CallbackInfo ci) {
        if (event != null) ImportedExecutionScope.recordAuraCharged(event.getEntity());
    }
}
