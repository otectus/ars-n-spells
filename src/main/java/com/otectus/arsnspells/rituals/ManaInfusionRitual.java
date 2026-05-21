package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * ANS-HIGH-002: routes the mana grant through {@link BridgeManager} instead of
 * importing {@code io.redspace.ironsspellbooks.api.magic.MagicData} directly.
 * The previous design imported MagicData at the class level; even though
 * registration is gated by Iron's-loaded, the top-level import made the class
 * brittle — any future reference to the type (reflection, diagnostic scan,
 * IDE inspection feature) would NoClassDefFoundError on Iron's-less servers.
 * Going through the bridge also makes the ritual useful on Ars-only setups.
 */
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
        BridgeManager.getBridge().addMana(player,
            AnsConfig.RITUAL_MANA_INFUSION_AMOUNT.get().floatValue());
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
        return new ResourceLocation("ars_n_spells", "mana_infusion");
    }
}
