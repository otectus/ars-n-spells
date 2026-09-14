package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.common.event.ManaCapEvents;
import com.otectus.arsnspells.bridge.ArsRegenTickScope;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ars 5.13.1.1400 playerOnTick(PlayerTickEvent.Pre) owns the native ManaCap.addMana(D)D regen call. */
@Mixin(value = ManaCapEvents.class, remap = false)
public abstract class MixinManaRegenTick {

    @Inject(method = "playerOnTick", at = @At("HEAD"))
    private static void arsnspells$enterRegenTick(PlayerTickEvent.Pre event, CallbackInfo ci) {
        Player player = event.getEntity();
        if (player != null) {
            ArsRegenTickScope.enter(player.getUUID());
        }
    }

    /**
     * Leaves the scope at every return.
     *
     * <p>{@code playerOnTick} returns early in several places (wrong phase, client side, the
     * regen interval not elapsed), and all of them must clear the mark or the next unrelated
     * {@code addMana} on this thread would be mistaken for regen and suppressed.
     */
    @Inject(method = "playerOnTick", at = @At("RETURN"))
    private static void arsnspells$exitRegenTick(PlayerTickEvent.Pre event, CallbackInfo ci) {
        ArsRegenTickScope.exit();
    }
}
