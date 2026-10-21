package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.compat.irons.glyph.IronTargetingScope;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * 3.4.0: Iron's aim helpers answer with the Ars target during an imported-glyph execution.
 *
 * <p>Inert unless {@link IronTargetingScope} has an active scope for the aiming entity on this
 * thread, which only an imported glyph's own execution opens; every native cast traces exactly
 * as before. {@code internalRaycastForEntity} is where every {@code raycastForEntity} overload
 * ends in Iron's 1.20.1-3.15.0; 3.16.x routes them through {@code RaycastBuilder} instead (see
 * {@link MixinIronsRaycastBuilder}), so that injection is optional.
 */
@Mixin(value = Utils.class, remap = false)
public abstract class MixinIronsAimHelpers {
    @Inject(method = "internalRaycastForEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private static void arsnspells$importedAimEntity(Level level, Entity origin, Vec3 start, Vec3 end, boolean checkForBlocks,
                                                     float bbInflation, Predicate<? super Entity> filter,
                                                     CallbackInfoReturnable<HitResult> cir) {
        HitResult redirected = IronTargetingScope.redirect(level, origin, start, checkForBlocks);
        if (redirected != null) cir.setReturnValue(redirected);
    }

    @Inject(method = "raycastForBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private static void arsnspells$importedAimBlock(Level level, Vec3 start, Vec3 end, ClipContext.Fluid fluid,
                                                    CallbackInfoReturnable<BlockHitResult> cir) {
        BlockHitResult redirected = IronTargetingScope.redirectBlock(level, null, start);
        if (redirected != null) cir.setReturnValue(redirected);
    }

    @Inject(method = "getTargetBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private static void arsnspells$importedTargetBlock(Level level, LivingEntity entity, ClipContext.Fluid fluid, double reach,
                                                       CallbackInfoReturnable<BlockHitResult> cir) {
        BlockHitResult redirected = IronTargetingScope.redirectBlock(level, entity, entity.getEyePosition());
        if (redirected != null) cir.setReturnValue(redirected);
    }
}
