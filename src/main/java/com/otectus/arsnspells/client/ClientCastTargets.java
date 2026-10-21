package com.otectus.arsnspells.client;

import com.otectus.arsnspells.spell.target.CastTargetHint;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Captures what the local player is aiming at when a cross-cast request is sent.
 *
 * <p>Client-only: it reads {@link Minecraft#hitResult}, the crosshair target the game itself
 * computed this frame. The result is a hint for the server, which re-picks the target; see
 * {@link CastTargetHint}.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientCastTargets {

    private ClientCastTargets() {
    }

    public static CastTargetHint capture(Player player) {
        ResourceLocation dimension = player.level().dimension().location();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) {
            return CastTargetHint.block(yaw, pitch, dimension, block.getBlockPos(), block.getDirection(), block.getLocation());
        }
        if (hit instanceof EntityHitResult entity && hit.getType() == HitResult.Type.ENTITY) {
            return CastTargetHint.entity(yaw, pitch, dimension, entity.getEntity().getId(), entity.getLocation());
        }
        return CastTargetHint.aimOnly(yaw, pitch, dimension);
    }
}
