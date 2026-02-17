package com.otectus.arsnspells.affinity;

import com.otectus.arsnspells.data.AffinityData;
import net.minecraft.world.entity.player.Player;

public class AffinityBonuses {
    public static float getAttributeMultiplier(Player player, AffinityType type) {
        return player.getCapability(AffinityData.AFFINITY_DATA).map(data -> {
            int level = data.getLevel(type);
            // Logic finalized: Provides a 0.5% boost per element level, up to 50% power ceiling.
            return 1.0f + (level * 0.005f);
        }).orElse(1.0f);
    }
}