package com.otectus.arsnspells.mixin.ars;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * STUB — the 1.20.1 mixin patched AN's {@code ManaCapEvents.playerOnTick(PlayerTickEvent)}
 * to redirect mana-regen / max-mana potions onto Iron's attributes. NeoForge
 * 1.21.1 split {@code TickEvent.PlayerTickEvent} into Pre/Post and AN 5.x
 * almost certainly retargeted its own subscription. Per the porting plan
 * (Section 2 / mixin retarget strategy) the cleanest replacement is a
 * direct {@code PlayerTickEvent.Post} handler in
 * {@code events/ArsPotionEffectRedirectHandler}, not a mixin.
 *
 * Inert {@code @Pseudo} kept so the mixin runtime does not error if the
 * target class moves between AN versions.
 */
@Pseudo
@Mixin(targets = "com.hollingsworth.arsnouveau.common.event.ManaCapEvents", remap = false)
public abstract class MixinArsPotionEffects {
    // Intentionally empty — see class javadoc.
}
