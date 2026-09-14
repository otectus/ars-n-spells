package com.otectus.arsnspells.mixin.ars;

import com.hollingsworth.arsnouveau.common.capability.ManaCapAttacher;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.otectus.arsnspells.bridge.ManaOwnerBinding;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bind the provider that Ars just attached, before any player can query or mutate it. */
@Mixin(value = ManaCapAttacher.class, remap = false)
public abstract class MixinManaCapAttacher {
    @Inject(method = "attach", at = @At("RETURN"))
    private static void arsnspells$bindAttachedOwner(AttachCapabilitiesEvent<Entity> event, CallbackInfo ci) {
        if (!(event.getObject() instanceof LivingEntity owner)) return;
        var provider = event.getCapabilities().get(new ResourceLocation("ars_nouveau", "mana"));
        if (provider != null) provider.getCapability(CapabilityRegistry.MANA_CAPABILITY).ifPresent(cap -> {
            if (cap instanceof ManaOwnerBinding binding) binding.arsnspells$bindOwner(owner);
        });
    }
}
