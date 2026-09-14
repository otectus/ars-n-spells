package com.otectus.arsnspells.bridge;

import net.minecraft.world.entity.LivingEntity;

/** Ars 4 creates its Forge capability with a null owner; attachment supplies that identity. */
public interface ManaOwnerBinding {
    void arsnspells$bindOwner(LivingEntity owner);
}
