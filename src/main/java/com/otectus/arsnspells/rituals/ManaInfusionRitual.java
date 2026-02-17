package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;

public class ManaInfusionRitual extends AbstractRitual {
    @Override
    public void tick() {}

    public void onFinishing(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        MagicData data = MagicData.getPlayerMagicData(player);
        if (data != null) {
            data.addMana(500f);
        }
    }

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation("ars_n_spells", "mana_infusion");
    }
}
