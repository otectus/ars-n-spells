package com.otectus.arsnspells.mixin.irons;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.arsnspells.bridge.IronsRegenScope;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Names Iron's regeneration tick so a routed ARS_PRIMARY write can tell it apart from a spend.
 * The body, including another mod's overwrite of it, runs unchanged inside the scope.
 *
 * <p>{@code require = 0}: this is a safety net, not a payment boundary. Verified against Iron's
 * 1.20.1-3.15.0 and 3.16.3, where {@code ManaStabilityGameTests} proves it applies; if a future
 * Iron's renames the method, the pack still loads and only Iron's native clamp returns.
 */
@Mixin(value = MagicManager.class, remap = false)
public abstract class MixinIronsManaRegen {
    @WrapMethod(method = "regenPlayerMana", require = 0, allow = 1)
    private boolean arsnspells$scopeRegen(ServerPlayer player, MagicData data, Operation<Boolean> original) {
        if (player == null) return original.call(player, data);
        return IronsRegenScope.run(player.getUUID(), () -> original.call(player, data));
    }
}
