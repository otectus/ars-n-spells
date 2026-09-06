package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.data.ProgressionData;
import com.otectus.arsnspells.progression.ProgressionModifiers;
import com.otectus.arsnspells.util.SpellAnalysis;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Cross-mod progression: casting Ars spells builds persistent per-school experience
 * that provides transient attribute bonuses to Iron's spell power.
 *
 * <p>The modifier's identity and its apply/remove sequence live in
 * {@link ProgressionModifiers}. This handler used to carry a second, private id of its own
 * (audit V07), so the same bonus could sit on the attribute twice.
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
                ProgressionData data = player.getData(AttachmentTypes.PROGRESSION.get());
                data.incrementCastCount(school);
                ProgressionModifiers.apply(player, school, data.getBonusForSchool(school));
            }
        }
    }

    /** Reapply transient bonuses on login from persisted data. */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reapplyAll(player);
        }
    }

    /**
     * Reapply every per-school transient attribute bonus from the player's
     * persisted cast counts. Transient modifiers do not survive a new Player
     * entity, so this runs on login (here) and on respawn / dimension change
     * (via {@link CapabilityResyncHandler}).
     *
     * <p>Idempotent, and now actually so (audit V14): the clear is unconditional and runs
     * <em>before</em> the config gate, so a run made while the feature is disabled removes the
     * bonuses instead of returning early and leaving them applied forever. The cast counts
     * behind them are persistent and are not touched either way, so re-enabling the feature
     * replays the same bonuses.
     */
    public static void reapplyAll(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ProgressionModifiers.clearAll(player);
        if (!AnsConfig.ENABLE_PROGRESSION_SYSTEM.get() || !AnsConfig.ENABLE_CROSS_MOD_PROGRESSION.get()) {
            return;
        }
        ProgressionData data = player.getData(AttachmentTypes.PROGRESSION.get());
        ProgressionModifiers.bonuses(data)
            .forEach((school, bonus) -> ProgressionModifiers.apply(player, school, bonus));
    }
}
