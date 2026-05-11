package com.otectus.arsnspells.affinity;

import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.AttachmentTypes;
import net.minecraft.world.entity.player.Player;

public class AffinityBonuses {
    public static float getAttributeMultiplier(Player player, AffinityType type) {
        AffinityData data = player.getData(AttachmentTypes.AFFINITY.get());
        int level = data.getLevel(type);
        // 0.5% boost per element level, capped at 50% (level 100).
        return 1.0f + (level * 0.005f);
    }
}
