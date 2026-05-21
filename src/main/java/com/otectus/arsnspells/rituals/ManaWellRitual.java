package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * ANS-HIGH-002: routes the mana grant through {@link BridgeManager}. See
 * {@link ManaInfusionRitual} for full rationale.
 */
public class ManaWellRitual extends AbstractRitual {
    @Override
    protected void tick() {
        if (this.getWorld() == null || this.getWorld().isClientSide()) {
            return;
        }
        int range = AnsConfig.MANA_WELL_RANGE.get();
        AABB area = new AABB(this.getPos()).inflate(range);
        float regenRate = AnsConfig.MANA_WELL_REGEN_RATE.get().floatValue();

        this.getWorld().getEntitiesOfClass(Player.class, area).forEach(p ->
            BridgeManager.getBridge().addMana(p, regenRate));
    }

    @Override
    public void onEnd() {}

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation("ars_n_spells", "mana_well");
    }
}
