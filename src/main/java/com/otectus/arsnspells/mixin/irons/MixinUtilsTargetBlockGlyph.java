package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.glyph.runtime.AdapterFrame;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adapter-scoped: Iron's target-block pick for an imported glyph's caster lands on the Ars block hit. */
@Mixin(value = Utils.class, remap = false)
public abstract class MixinUtilsTargetBlockGlyph {

    @Inject(method = "getTargetBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private static void arsnspells$importedGlyphBlock(Level level, LivingEntity entity, ClipContext.Fluid fluid, double reach,
                                                       CallbackInfoReturnable<BlockHitResult> cir) {
        AdapterFrame frame = AdapterFrame.forCaster(entity);
        if (frame != null && frame.targetBlock != null) cir.setReturnValue(frame.targetBlock);
    }
}
