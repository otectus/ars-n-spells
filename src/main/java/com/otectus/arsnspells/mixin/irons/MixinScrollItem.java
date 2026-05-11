package com.otectus.arsnspells.mixin.irons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * STUB — the 1.20.1 mixin gated Iron's scroll consumption on bridge LP /
 * mana availability. Iron's {@code Scroll} class internals + the
 * surrounding {@code ISpellContainer} / {@code SpellData} surface drifted
 * in 1.21.1-3.x. Pending Phase 11.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.item.Scroll", remap = false)
public abstract class MixinScrollItem {
    // Intentionally empty — see class javadoc.
}
