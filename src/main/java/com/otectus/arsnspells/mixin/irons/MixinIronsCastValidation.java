package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.casting.IronsCastPayments;
import com.otectus.arsnspells.spell.CastValidationScope;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve every native condition while replacing the native single-pool mana comparison.
 * HEAD/RETURN remain compatible with mods that overwrite canBeCastedBy; instruction
 * injection on this method is deliberately avoided (One Mana Bar compatibility).
 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class MixinIronsCastValidation {
    @Inject(method = "canBeCastedBy", at = @At("HEAD"), require = 1)
    private void arsnspells$openCastValidationScope(int spellLevel, CastSource source,
            MagicData data, Player player, CallbackInfoReturnable<CastResult> cir) {
        CastValidationScope.clear();
        if (player instanceof ServerPlayer serverPlayer && data != null) {
            CastValidationScope.push(data, serverPlayer.getUUID(), true, 0);
        }
    }

    @Inject(method = "canBeCastedBy", at = @At("RETURN"), cancellable = true, require = 1)
    private void arsnspells$closeCastValidationScope(int spellLevel, CastSource source,
            MagicData data, Player player, CallbackInfoReturnable<CastResult> cir) {
        try {
            CastResult nativeResult = cir.getReturnValue();
            if (!(player instanceof ServerPlayer) || data == null || nativeResult == null
                    || (!nativeResult.isSuccess() && !arsnspells$isManaFailure(nativeResult))) return;
            if (IronsCastPayments.canAfford(player, (AbstractSpell)(Object)this, spellLevel, source, data)) {
                if (!nativeResult.isSuccess()) cir.setReturnValue(new CastResult(CastResult.Type.SUCCESS));
            } else {
                cir.setReturnValue(new CastResult(CastResult.Type.FAILURE,
                    Component.translatable("ui.irons_spellbooks.cast_error_mana")));
            }
        } finally {
            CastValidationScope.clear();
        }
    }

    private static boolean arsnspells$isManaFailure(CastResult result) {
        return result.message != null && result.message.getContents() instanceof TranslatableContents contents
            && "ui.irons_spellbooks.cast_error_mana".equals(contents.getKey());
    }
}
