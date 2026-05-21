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
        if (event.getEntity() instanceof ServerPlayer player) {
            reapplyAllBonuses(player);
        }
    }

    /**
     * ANS-HIGH-024: also re-apply on respawn. Transient attribute modifiers don't
     * survive death (the new Player entity gets fresh attributes), so without this
     * a player who casts 200 fire spells, dies, respawns sees +0% fire spell power
     * until their next fire cast incrementally re-applies it.
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reapplyAllBonuses(player);
        }
    }

    /**
     * ANS-HIGH-024: same for dimension change (Player instance is replaced).
     */
    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reapplyAllBonuses(player);
        }
    }

    private void reapplyAllBonuses(ServerPlayer player) {
        if (!AnsConfig.ENABLE_PROGRESSION_SYSTEM.get() || !AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.get()) {
            return;
        }
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
