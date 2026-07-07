package com.otectus.arsnspells.util;

import com.otectus.arsnspells.ArsNSpells;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Audit H4: grants the code-awarded advancements of the loom workflow chain
 * (transcribe / bind / first cross-cast use {@code minecraft:impossible}
 * criteria because those actions have no vanilla trigger). Null-safe and
 * idempotent — safe to call on every occurrence of the action.
 */
public final class AdvancementUtil {

    private AdvancementUtil() {}

    /**
     * Award every remaining criterion of {@code ars_n_spells:<path>} to the
     * player. No-op if the player is null, the advancement is missing (e.g.
     * removed by a datapack), or it is already complete.
     */
    public static void grant(ServerPlayer player, String path) {
        if (player == null || player.getServer() == null) {
            return;
        }
        Advancement advancement = player.getServer().getAdvancements()
            .getAdvancement(new ResourceLocation(ArsNSpells.MODID, path));
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) {
            return;
        }
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }
}
