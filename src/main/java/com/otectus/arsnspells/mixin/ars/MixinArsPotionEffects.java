package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.common.event.ManaCapEvents;
import com.otectus.arsnspells.equipment.PotionContributions;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The same reconciliation runs on ticks and immediately during a mode transition. */
@Mixin(value = ManaCapEvents.class, remap = false)
public abstract class MixinArsPotionEffects {
    @Inject(method = "playerOnTick", at = @At("HEAD"), require = 1)
    private static void arsnspells$redirectPotionEffects(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        if (event.phase == TickEvent.Phase.END) PotionContributions.reconcile(event.player);
    }
}
