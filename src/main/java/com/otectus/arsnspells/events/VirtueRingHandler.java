package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCostCalcEvent;
import com.otectus.arsnspells.casting.ArsCastPayments;
import com.otectus.arsnspells.compat.SanctifiedLegacyCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.contract.ResourceUnit;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Pure alternative aura quote, owned by the exact Ars context. */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public final class VirtueRingHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpellCostCalc(SpellCostCalcEvent event) {
        if (event.context == null || !(event.context.getUnwrappedCaster() instanceof Player player)
                || player.level().isClientSide() || player.isCreative()
                || !SanctifiedLegacyCompat.isVirtueAuraCostPathActive(player) || event.currentCost <= 0) return;
        long cost = Math.max(1, Math.round(event.currentCost * AnsConfig.ARS_VIRTUE_AURA_MULTIPLIER.get()));
        ArsCastPayments.alternative(event.context, ResourceUnit.AURA, (int) Math.min(Integer.MAX_VALUE, cost));

    }

    public static void clearPendingAuraCost(Player player) {}
    public static void clearPendingAuraCost(UUID player) {}
    public static int getPendingAuraCost(Player player) { return -1; }
}