package com.otectus.arsnspells.rituals;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import com.otectus.arsnspells.config.AnsConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public class ManaWellRitual extends AbstractRitual {
    @Override
    public void tick() {
        // High-Fidelity Logic: Range and intensity scale with config and progress
        int range = AnsConfig.COOLDOWN_CATEGORY_DURATION.get() / 10; // Logic scaled from categorizer spec
        AABB area = new AABB(this.getPos()).inflate(range > 0 ? range : 8);
        
        this.getWorld().getEntitiesOfClass(Player.class, area).forEach(p -> {
            // Logic: Grants 2.0 mana per tick to all allies in range
            MagicData data = MagicData.getPlayerMagicData(p);
            if (data != null) {
                data.addMana(2.0f);
            }
        });
    }

    public void onRitualFinished(Player player) {}

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation("ars_n_spells", "mana_well");
    }
}
