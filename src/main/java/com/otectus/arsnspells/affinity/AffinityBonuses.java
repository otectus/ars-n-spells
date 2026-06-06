package com.otectus.arsnspells.affinity;

import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.AttachmentTypes;
import net.minecraft.world.entity.player.Player;

public class AffinityBonuses {
    /**
     * Damage multiplier from the player's affinity in {@code schoolKey} (a full
     * school id such as {@code "irons_spellbooks:fire"}). 0.5% per level, capped
     * at +50%. The explicit {@code min} mirrors the 0..100 level clamp in
     * {@link AffinityData#setLevel} so the cap survives any future clamp change.
     */
    public static float getAttributeMultiplier(Player player, String schoolKey) {
        if (schoolKey == null) {
            return 1.0f;
        }
        AffinityData data = player.getData(AttachmentTypes.AFFINITY.get());
        int level = data.getLevel(schoolKey);
        return 1.0f + Math.min(0.50f, level * 0.005f);
    }
}
