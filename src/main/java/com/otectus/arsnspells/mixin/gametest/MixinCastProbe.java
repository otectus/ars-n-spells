package com.otectus.arsnspells.mixin.gametest;

import com.llamalad7.mixinextras.sugar.Local;
import com.otectus.arsnspells.gametest.CastProbe;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GameTest only: a third-party injection inside Iron's native mana block, at the same call
 * Animus uses (the {@code MagicData.getMana()} read that precedes Iron's mana write). Never
 * applied in a normal game: {@code ArsNSpellsMixinPlugin} applies it only when the GameTest
 * run configuration sets {@code -Dans.gametest.castProbe=true}. Behaviour lives in
 * {@link CastProbe}.
 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class MixinCastProbe {
    @Inject(method = "castSpell", at = @At(value = "INVOKE",
        target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;getMana()F"), cancellable = true, require = 1, allow = 1)
    private void arsnspells$castProbe(Level level, int spellLevel, ServerPlayer player, CastSource source,
                                      boolean triggerCooldown, CallbackInfo ci,
                                      @Local MagicData data, @Local SpellOnCastEvent event) {
        if (!CastProbe.allowNativeBlock(player, data, event.getManaCost())) ci.cancel();
    }
}
