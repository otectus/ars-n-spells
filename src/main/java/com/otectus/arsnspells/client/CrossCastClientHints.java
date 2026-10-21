package com.otectus.arsnspells.client;

import com.otectus.arsnspells.spell.target.TargetHint;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Client-only: the crosshair target a cross-cast request reports as its aim hint. */
public final class CrossCastClientHints {
    private CrossCastClientHints() {}

    public static TargetHint current() {
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof EntityHitResult entityHit) return TargetHint.entity(entityHit.getEntity().getId());
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK)
            return TargetHint.block(blockHit.getBlockPos(), blockHit.getDirection());
        return TargetHint.NONE;
    }
}
