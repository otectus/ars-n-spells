package com.otectus.arsnspells.affinity;

import com.otectus.arsnspells.data.AffinityData;
import net.minecraft.world.entity.player.Player;

public class AffinityBonuses {
    /**
     * Multiplier applied to spell power for the school the player is casting.
     * Built from {@link AffinityCalculator#getDamageBonus} so the per-level
     * curve lives in one place.
     */
    public static float getAttributeMultiplier(Player player, AffinityType type) {
        return player.getCapability(AffinityData.AFFINITY_DATA).map(data -> {
            int level = data.getLevel(type);
            return 1.0f + AffinityCalculator.getDamageBonus(type, level);
        }).orElse(1.0f);
    }
}
