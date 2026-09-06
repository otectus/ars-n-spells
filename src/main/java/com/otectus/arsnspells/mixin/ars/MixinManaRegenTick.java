package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.common.event.ManaCapEvents;
import com.otectus.arsnspells.bridge.ArsRegenTickScope;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Names the window in which Ars Nouveau runs its native mana regeneration tick (audit V06).
 *
 * <p>Injection target, confirmed against the pinned Ars Nouveau 4.12.7 jar
 * ({@code ars-nouveau-401955-6688854.jar}, sha256
 * {@code 1f1debc282a0c379c1141f2840ea294eede6f6b544c589663f40bbe17b59a1af}):
 * {@code com/hollingsworth/arsnouveau/common/event/ManaCapEvents.playerOnTick}, descriptor
 * {@code (Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V}. That method computes the
 * per-tick amount from {@code ManaUtil.getManaRegen(Player)} and applies it through the single
 * {@code IManaCap.addMana:(D)D} call it makes. Ars mod classes are not reobfuscated, hence
 * {@code remap = false}.
 *
 * <p>This mixin moves nothing. It only marks the scope, so
 * {@link MixinManaCapability#arsnspells$addMana} can tell Ars's regen apart from every other
 * caller of the same method - which is the entire finding. ANS used to make {@code addMana} a
 * blanket no-op in the shared modes, so a mana potion or a Void Jar reported success and moved
 * nothing.
 *
 * <p>HEAD and RETURN rather than an instruction-level {@code @Redirect}: an {@code INVOKE}
 * injection point is rejected outright when another mod {@code @Overwrite}-merges the target,
 * and these mixins live in the required config where that is a hard boot failure. The repo
 * enforces that in {@code MixinInjectionPointImmunityTest}.
 */
@Mixin(value = ManaCapEvents.class, remap = false)
public abstract class MixinManaRegenTick {

    @Inject(method = "playerOnTick", at = @At("HEAD"))
    private static void arsnspells$enterRegenTick(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        Player player = event.player;
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
    private static void arsnspells$exitRegenTick(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        ArsRegenTickScope.exit();
    }
}
