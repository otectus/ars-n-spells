package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class ManaWellRitual extends AbstractRitual {
    @Override
    protected void tick() {
        if (this.getWorld() == null || this.getWorld().isClientSide()) {
            return;
        }
        int range = AnsConfig.MANA_WELL_RANGE.get();
        AABB area = new AABB(this.getPos()).inflate(range);
        float regenRate = AnsConfig.MANA_WELL_REGEN_RATE.get().floatValue();

        this.getWorld().getEntitiesOfClass(Player.class, area).forEach(p -> {
            MagicData data = MagicData.getPlayerMagicData(p);
            if (data != null) {
                data.addMana(regenRate);
            }
        });
    }

    @Override
    public void onEnd() {}

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation("ars_n_spells", "mana_well");
    }
}
