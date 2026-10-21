package com.otectus.arsnspells.events;

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

    /**
     * One root cast's progression credit, at the cast's success boundary.
     *
     * <p>3.4.0: was a {@code SpellCastEvent} listener, which is posted before the form runs, so
     * a Touch miss or a cast refused later still counted (see {@link AffinityHandler#credit}).
     * Cross-mod progression feeds Iron's spell power, so it only counts with Iron's loaded, as
     * when this handler was registered only in that case.
     */
    public static void credit(ServerPlayer player, com.hollingsworth.arsnouveau.api.spell.Spell spell,
                              com.hollingsworth.arsnouveau.api.spell.SpellContext context) {
        if (!AnsConfig.ENABLE_PROGRESSION_SYSTEM.get() || !AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.get()
            || !com.otectus.arsnspells.compat.IronsCompat.isLoaded()) {
            return;
        }
        {
            // Same school the affinity handler credits: the cast's explicit school when it
            // carries one, otherwise the glyphs'.
            String school = SpellAnalysis.analyze(spell, context).schoolKey();
            if (!com.otectus.arsnspells.util.SchoolKeys.GENERIC.equals(school)) {
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

    /**
     * Re-apply the persisted per-school bonuses as transient modifiers.
     *
     * <p>V14: static and public so {@code ModeChangeCleanup} can run it as step 2 of its
     * reconcile sequence. It reads the persisted cast counts and writes only the transient
     * modifier, which is what makes "progression data is preserved when the feature is
     * disabled" true: turning the feature off removes the modifier, not the counts.
     */
    public static void reapplyAllBonuses(ServerPlayer player) {
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
