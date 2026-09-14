package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.otectus.arsnspells.casting.ArsCastPayments;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.ResourceUnit;
import com.otectus.arsnspells.util.SpellAnalysis;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Pure alternative LP quote. Resolution and targets never create or consume a payment. */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public final class CursedRingHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpellCostCalc(SpellCostCalcEvent event) {
        if (event.context == null || !(event.context.getUnwrappedCaster() instanceof Player player)
                || player.level().isClientSide() || player.isCreative()
                || !SanctifiedLegacyCompat.isCursedRingCostPathActive(player) || event.currentCost <= 0) return;
        var analysis = SpellAnalysis.analyze(event.context.getSpell());
        int cost = SanctifiedLegacyCompat.calculateLPCost(event.currentCost, analysis.firstEffect());
        double discount = SanctifiedLegacyCompat.getBlasphemyLPMultiplier(player, analysis.dominantSchool());
        if (discount < 1) cost = (int) Math.max(AnsConfig.ARS_LP_MINIMUM_COST.get(), Math.round(cost * discount));
        ArsCastPayments.alternative(event.context, ResourceUnit.LP, cost);

    }

    /** Legacy cleanup entry points; there is no player-wide pending payment queue. */
    public static void clearPendingLPCost(Player player) {}
    public static void clearPendingLPCost(UUID player) {}
    public static int getPendingLPCost(Player player) { return -1; }
}