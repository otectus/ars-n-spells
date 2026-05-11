package com.otectus.arsnspells.mixin.ars;

import org.spongepowered.asm.mixin.Mixin;

/**
 * STUB — pre-cast hard-gate that cancelled an Ars spell when the bridge
 * mana check failed. AN's {@code SpellResolver} internals (shadowed
 * {@code spellContext} / {@code spell} fields, intercepted method names)
 * shifted between 4.12 and 5.x. The replacement strategy lives in the
 * porting plan as either a re-shadow of the new field names or a switch
 * to a {@code SpellCastEvent.Pre} handler at HIGHEST priority.
 */
@Mixin(targets = "com.hollingsworth.arsnouveau.api.spell.SpellResolver", remap = false)
public abstract class MixinSpellResolverPreCast {
    // Intentionally empty — see class javadoc.
}
