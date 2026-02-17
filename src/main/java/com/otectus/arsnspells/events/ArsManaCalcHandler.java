package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent;
import com.hollingsworth.arsnouveau.api.event.MaxManaCalcEvent;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Applies Iron's gear mana bonuses to Ars mana calculations when Ars is primary.
 */
@Mod.EventBusSubscriber(modid = "ars_n_spells")
public class ArsManaCalcHandler {

    @SubscribeEvent
    public static void onMaxManaCalc(MaxManaCalcEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }
        if (!BridgeManager.isIronsSpellbooksLoaded()) {
            return;
        }
        if (!AnsConfig.respectArmorBonuses.get()) {
            return;
        }

        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode == null || !mode.isArsPrimary()) {
            return;
        }

        EquipmentIntegration.ManaBonus ironBonus = EquipmentIntegration.getIronManaBonuses(player);
        if (ironBonus.maxMana == 0.0) {
            return;
        }

        double conversionRate = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
        int updatedMax = (int) Math.max(0, Math.round(event.getMax() + ironBonus.maxMana * conversionRate));
        event.setMax(updatedMax);
    }

    @SubscribeEvent
    public static void onManaRegenCalc(ManaRegenCalcEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }
        if (!BridgeManager.isIronsSpellbooksLoaded()) {
            return;
        }
        if (!AnsConfig.respectArmorBonuses.get()) {
            return;
        }

        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode == null || !mode.isArsPrimary()) {
            return;
        }

        EquipmentIntegration.ManaBonus ironBonus = EquipmentIntegration.getIronManaBonuses(player);
        if (ironBonus.manaRegen == 0.0) {
            return;
        }

        double conversionRate = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
        double updatedRegen = Math.max(0.0, event.getRegen() + ironBonus.manaRegen * conversionRate);
        event.setRegen(updatedRegen);
    }
}
