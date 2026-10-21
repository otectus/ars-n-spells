package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.glyph.runtime.AdapterFrame;
import io.redspace.ironsspellbooks.api.util.RaycastBuilder;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Adapter-scoped: while an imported glyph runs a native Iron's call for its caster, that caster's
 * raycasts land on the Ars hit (see {@link AdapterFrame}). The raycast's own entity filter still
 * applies, so a spell that may only target, say, its caster's summons cannot be pointed at
 * anything else. Every other raycast is untouched.
 */
@Mixin(value = RaycastBuilder.class, remap = false)
public abstract class MixinRaycastBuilderGlyph {

    @Shadow @Final private Entity originEntity;
    @Shadow private Predicate<? super Entity> filter;

    @Inject(method = "performRaycast", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$importedGlyphTarget(CallbackInfoReturnable<HitResult> cir) {
        AdapterFrame frame = AdapterFrame.forCaster(originEntity);
        if (frame == null || frame.raycast == null) return;
        HitResult hit = frame.raycast;
        if (hit instanceof EntityHitResult entityHit && filter != null && !filter.test(entityHit.getEntity())) {
            Entity refused = entityHit.getEntity();
            hit = BlockHitResult.miss(refused.position(), Direction.UP, refused.blockPosition());
        }
        cir.setReturnValue(hit);
    }
}
