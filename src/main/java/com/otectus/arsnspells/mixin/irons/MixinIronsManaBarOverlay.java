package com.otectus.arsnspells.mixin.irons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * STUB — the 1.20.1 mixin targeted Forge's {@code ForgeGui} via Iron's
 * {@code ManaBarOverlay.render(ForgeGui, GuiGraphics, float, int, int)}
 * method signature. NeoForge 1.21.1 removed {@code ForgeGui} entirely
 * (replaced by {@code RegisterGuiLayersEvent} + {@code LayeredDraw.Layer}).
 *
 * Replacement strategy: instead of mixing into Iron's overlay, register a
 * NeoForge layer at higher priority than Iron's that wraps or replaces
 * the mana bar based on the active {@code ManaUnificationMode}. See
 * {@link com.otectus.arsnspells.client.ManaBarController} for the planned
 * integration point. This mixin remains as an inert {@code @Pseudo} so
 * the mixin runtime does not error if Iron's is absent.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.gui.overlays.ManaBarOverlay", remap = false)
public abstract class MixinIronsManaBarOverlay {
    // Intentionally empty — see class javadoc.
}
