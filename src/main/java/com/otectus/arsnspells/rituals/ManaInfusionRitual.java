package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import com.otectus.arsnspells.config.AnsConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;

public class ManaInfusionRitual extends AbstractRitual {
    @Override
    protected void tick() {}

    @Override
    public void onEnd() {
        if (this.getWorld() == null || this.getWorld().isClientSide()) {
            return;
        }
        Player player = findNearestPlayer(8);
        if (player == null) {
            return;
        }
        MagicData data = MagicData.getPlayerMagicData(player);
        if (data != null) {
            data.addMana(AnsConfig.RITUAL_MANA_INFUSION_AMOUNT.get().floatValue());
        }
    }

    @Nullable
    private Player findNearestPlayer(int radius) {
        if (this.getWorld() == null || this.getPos() == null) return null;
        AABB area = new AABB(this.getPos()).inflate(radius);
        return this.getWorld().getEntitiesOfClass(Player.class, area).stream()
            .min(Comparator.comparingDouble(p -> p.distanceToSqr(
                this.getPos().getX() + 0.5, this.getPos().getY() + 0.5, this.getPos().getZ() + 0.5)))
            .orElse(null);
    }

    @Override
    public ResourceLocation getRegistryName() {
        return ResourceLocation.fromNamespaceAndPath("ars_n_spells", "mana_infusion");
    }
}
