package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.common.event.ManaCapEvents;
import com.otectus.arsnspells.bridge.ManaMutationRouter;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks Ars Nouveau's mana <b>regeneration tick</b> so it, and only it, can be suppressed
 * (audit V06).
 *
 * <p>{@code MixinManaCapability} used to suppress regeneration by cancelling
 * {@code ManaCap.addMana} outright in the shared-pool modes. That is far too wide a net:
 * {@code addMana} is the capability's entire additive surface, so a potion, a ritual, an artifact
 * or another integration mod granting the player Ars mana was silently discarded along with the
 * regen, with no log line and no way for the player to tell.
 *
 * <p>This brackets the one method that regenerates. In the pinned Ars 5.13.1.1400,
 * {@code ManaCapEvents.playerOnTick(PlayerTickEvent$Pre)} contains exactly one invocation of
 * {@code ManaCap.addMana(D)D}, immediately after the {@code ManaUtil.getManaRegen} /
 * {@code MEAN_TPS} / {@code REGEN_INTERVAL} division that computes the per-tick amount. While
 * this bracket is held for a player, an {@code addMana} for that player is the regen call;
 * outside it, it is a third-party grant and {@code MixinManaCapability} routes it to the
 * authoritative pool.
 *
 * <p><b>Why not {@code @Redirect} on that invocation.</b> It would be more direct, and it is what
 * this mixin did first. But Mixin's {@code Injector.findTargetNodes} rejects an instruction-level
 * injection point outright when another mod has {@code @Overwrite}-merged the target method, and
 * that throw happens during PREPARE, before {@code require} is consulted - so {@code require = 0}
 * does not soften it and the load aborts for every mod in the chain. {@code HEAD} and
 * {@code RETURN} override {@code checkPriority} to return {@code true} and are structurally
 * immune, which is why this mod's injection points are restricted to them.
 *
 * <p><b>Returned-value semantics.</b> {@code ManaCap.addMana} returns the balance <em>after</em>
 * the add. {@code playerOnTick} discards that value and uses only the fact that the call happened
 * to decide whether to re-sync the capability to the client, so the suppressed path returns the
 * unchanged current balance: the tick still syncs, it just syncs a pool that did not grow.
 * Returning {@code 0} instead would be a lie about the balance for any future Ars version that
 * reads it.
 */
@Mixin(value = ManaCapEvents.class, remap = false)
public abstract class MixinManaCapEventsRegen {

    /**
     * Open the bracket. Setting is idempotent, which also self-heals the one leak this design
     * admits: if {@code playerOnTick} throws past the RETURN injections, the flag stays set until
     * the next tick opens and closes it again - one tick of suppressed third-party grants for one
     * player, rather than a permanent state.
     */
    @Inject(method = "playerOnTick", at = @At("HEAD"), require = 0)
    private static void arsnspells$beginRegenTick(PlayerTickEvent.Pre event, CallbackInfo ci) {
        Player player = event.getEntity();
        if (player != null) {
            ManaMutationRouter.enter(player.getUUID(), ManaMutationRouter.Direction.REGEN_TICK);
        }
    }

    /** Close the bracket. {@code RETURN} injects at every return, including the early ones. */
    @Inject(method = "playerOnTick", at = @At("RETURN"), require = 0)
    private static void arsnspells$endRegenTick(PlayerTickEvent.Pre event, CallbackInfo ci) {
        Player player = event.getEntity();
        if (player != null) {
            ManaMutationRouter.exit(player.getUUID(), ManaMutationRouter.Direction.REGEN_TICK);
        }
    }
}
