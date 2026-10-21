package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.compat.irons.glyph.IronTargetingScope;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 3.4.0: Iron's 3.16.x {@code RaycastBuilder} answers with the Ars target during an
 * imported-glyph execution. The class does not exist in 1.20.1-3.15.0, so it is named by string
 * and the mixin plugin applies this only when the class is on the classpath.
 */
@Mixin(targets = "io.redspace.ironsspellbooks.api.util.RaycastBuilder", remap = false)
public abstract class MixinIronsRaycastBuilder {
    @Shadow @Final private Level level;
    @Shadow @Final private Entity originEntity;
    @Shadow private Vec3 start;
    @Shadow private boolean checkForBlocks;

    @Inject(method = "performRaycast", at = @At("HEAD"), cancellable = true, require = 0)
    private void arsnspells$importedAim(CallbackInfoReturnable<HitResult> cir) {
        HitResult redirected = IronTargetingScope.redirect(level, originEntity, start, checkForBlocks);
        if (redirected != null) cir.setReturnValue(redirected);
    }
}
