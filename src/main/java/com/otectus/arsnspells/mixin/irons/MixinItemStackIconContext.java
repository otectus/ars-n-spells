package com.otectus.arsnspells.mixin.irons;

import com.otectus.arsnspells.client.icons.CarrierRenderContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Client-only tooltip context, installed alongside the optional native icon adapter. */
@Mixin(ItemStack.class)
public abstract class MixinItemStackIconContext {
    @Inject(method = "getTooltipLines", at = @At("HEAD"))
    private void ans$begin(CallbackInfoReturnable<List<Component>> callback) {
        CarrierRenderContext.push((ItemStack) (Object) this);
    }
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void ans$end(CallbackInfoReturnable<List<Component>> callback) {
        CarrierRenderContext.pop();
    }
}
