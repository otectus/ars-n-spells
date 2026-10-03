package com.otectus.arsnspells.mixin.irons;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.arsnspells.spell.CastValidationScope;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/** Wrap the invocation, including overwritten bodies and cancelling injections. Never rescue a native failure. */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class MixinIronsCastValidation {
    @WrapMethod(method = "canBeCastedBy", require = 1, allow = 1)
    private CastResult arsnspells$validation(int level, CastSource source, MagicData data, Player player,
                                           Operation<CastResult> original) {
        if (!(player instanceof ServerPlayer) || data == null) return original.call(level, source, data, player);
        // Admission is provisional: only the final SpellOnCastEvent can determine affordability.
        return CastValidationScope.with(data, player.getUUID(), true, 0,
            () -> original.call(level, source, data, player));
    }
}
