package com.otectus.arsnspells.mixin.irons;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin accessor for Iron's Spellbooks' private {@code MagicData.serverPlayer} field.
 *
 * <p>Used by {@link MixinIronsCastValidation} to look up the owning player from a
 * {@code MagicData} reference inside a {@code @Redirect} handler, without having to
 * fall back to reflection.
 *
 * <p>{@code remap = false}: {@code MagicData} is Iron's own class, not vanilla, so
 * there is no mapping to apply (and under mojmap-at-runtime remapping is a no-op
 * anyway).
 */
@Mixin(value = MagicData.class, remap = false)
public interface MagicDataAccessor {
    @Accessor("serverPlayer")
    ServerPlayer arsnspells$getServerPlayer();
}
