package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.otectus.arsnspells.casting.ArsCastPayments;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Commit only the reservation attached to this native resolver's context. */
@Mixin(value = SpellResolver.class, remap = false)
public abstract class MixinSpellResolverMana {
    @Shadow public SpellContext spellContext;

    @Inject(method = "expendMana", at = @At("HEAD"), cancellable = true)
    private void arsnspells$expendMana(CallbackInfo ci) {
        if (spellContext != null && spellContext.getUnwrappedCaster() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.otectus.arsnspells.events.CooldownHandler.commit(player, spellContext);
            if (ArsCastPayments.commit(player, spellContext)) ci.cancel();
        }
    }
}
