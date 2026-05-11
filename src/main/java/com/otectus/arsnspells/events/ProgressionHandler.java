package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.ProgressionData;
import com.otectus.arsnspells.progression.ProgressionAttributes;
import com.otectus.arsnspells.util.SpellAnalysis;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Cross-mod progression: Ars Nouveau casts build per-school cast counts
 * that grant a transient bonus to the matching Iron's Spellbooks
 * {@code <school>_spell_power} attribute. The Iron's-side mirror lives in
 * {@link IronsProgressionHandler} and shares storage via {@link ProgressionData}
 * and modifier UUID via {@link ProgressionAttributes}.
 */
public class ProgressionHandler {

    @SubscribeEvent
    public void onArsSpellCast(SpellCastEvent event) {
        if (!AnsConfig.ENABLE_PROGRESSION_SYSTEM.get() || !AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.get()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            String school = SpellAnalysis.analyze(event.spell).dominantSchool();
            if (!"generic".equals(school)) {
                player.getCapability(ProgressionData.PROGRESSION_DATA).ifPresent(data -> {
                    data.incrementCastCount(school);
                    ProgressionAttributes.applyTransientBonus(player, school, data.getBonusForSchool(school));
                });
            }
        }
    }

    /**
     * Reapply transient bonuses on login from persisted data.
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!AnsConfig.ENABLE_PROGRESSION_SYSTEM.get() || !AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.get()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(ProgressionData.PROGRESSION_DATA).ifPresent(data -> {
                data.getAllCastCounts().forEach((school, count) -> {
                    double bonus = data.getBonusForSchool(school);
                    if (bonus > 0) {
                        ProgressionAttributes.applyTransientBonus(player, school, bonus);
                    }
                });
            });
        }
    }
}
