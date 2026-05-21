package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent;
import com.hollingsworth.arsnouveau.api.event.MaxManaCalcEvent;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.ManaRegenBridge;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.EventPriority;
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

    /**
     * After all MaxManaCalcEvent handlers have run, sync Iron's MAX_MANA attribute
     * to match Ars's final max. Prevents Iron's tick from clamping Ars mana.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void syncIronsMaxAfterCalc(MaxManaCalcEvent event) {
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

        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode == null || !mode.isArsPrimary()) {
            return;
        }

        // ANS-MED-001 (NEEDS VERIFY): defer the syncIronsMaxToArs call to the next
        // server tick to break any potential reentrancy chain — Iron's MAX_MANA
        // mutations downstream might in turn fire MaxManaCalcEvent. The defensive
        // tell() pattern has no observable behaviour change in the no-reentry case
        // (one-tick latency on attribute sync) but eliminates the stack-overflow
        // hazard if Iron's internal event behaviour ever evolves.
        final int finalMax = event.getMax();
        if (player.getServer() != null) {
            player.getServer().tell(new net.minecraft.server.TickTask(0,
                () -> EquipmentIntegration.syncIronsMaxToArs(player, finalMax)));
        } else {
            EquipmentIntegration.syncIronsMaxToArs(player, finalMax);
        }
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

        // Iron's MANA_REGEN attribute is a percentage-of-pool multiplier; the Ars regen
        // event expects an absolute mana/sec delta. Going through ManaRegenBridge is
        // mandatory — adding ironBonus.manaRegen directly is a unit-mismatch bug that
        // can produce hundreds of mana/sec on geared wizards.
        double absRegenPerSec = ManaRegenBridge.convertIronsToArs(ironBonus.manaRegen, player);
        double conversionRate = AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
        double updatedRegen = Math.max(0.0, event.getRegen() + absRegenPerSec * conversionRate);
        event.setRegen(updatedRegen);
    }
}
