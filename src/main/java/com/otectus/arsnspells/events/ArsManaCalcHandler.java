package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent;
import com.hollingsworth.arsnouveau.api.event.MaxManaCalcEvent;
import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.ManaRegenBridge;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import net.minecraft.server.TickTask;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Folds Iron's Spellbooks' <em>gear-derived</em> {@code MANA_REGEN} and {@code MAX_MANA}
 * bonuses into Ars Nouveau's mana calculations when the active mode is ARS_PRIMARY (Ars is the
 * source of truth and should inherit Iron's gear bonuses), then syncs the resulting ceiling
 * back onto Iron's attribute so Iron's own clamping cannot destroy the surplus.
 *
 * <p>All cross-system regen translation goes through {@link ManaRegenBridge} so the unit
 * mismatch (Iron's percentage-of-pool vs. Ars absolute mana/sec) is handled centrally.
 *
 * <p>Other modes are no-ops here: ISS_PRIMARY runs Iron's natively, HYBRID shares the pool
 * through {@code MixinManaCapability}, and SEPARATE keeps the pools independent.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class ArsManaCalcHandler {

    private ArsManaCalcHandler() {}

    @SubscribeEvent
    public static void onManaRegenCalc(ManaRegenCalcEvent event) {
        if (!shouldFoldIron(event.getEntity())) {
            return;
        }
        Player player = (Player) event.getEntity();
        double ironsRegen;
        try {
            // The gear bonus, not the whole attribute: the base regen is Iron's own baseline,
            // not something the player earned from equipment, and folding it in hands out a
            // permanent bonus for having Iron's installed.
            ironsRegen = EquipmentIntegration.ironsGearRegenBonus(player);
        } catch (Throwable t) {
            return;
        }
        if (!Double.isFinite(ironsRegen) || ironsRegen == 0.0) {
            return;
        }
        double absAdd = ManaRegenBridge.convertIronsToArs(ironsRegen, player) * conversionRate();
        if (absAdd != 0.0) {
            event.setRegen(event.getRegen() + absAdd);
        }
    }

    @SubscribeEvent
    public static void onMaxManaCalc(MaxManaCalcEvent event) {
        if (!shouldFoldIron(event.getEntity())) {
            return;
        }
        Player player = (Player) event.getEntity();
        double ironsMax;
        try {
            ironsMax = EquipmentIntegration.ironsGearMaxManaBonus(player);
        } catch (Throwable t) {
            return;
        }
        if (!Double.isFinite(ironsMax) || ironsMax == 0.0) {
            return;
        }
        event.setMax(event.getMax() + (int) Math.round(ironsMax * conversionRate()));
    }

    /**
     * After every {@link MaxManaCalcEvent} handler has run, raise Iron's {@code MAX_MANA} to
     * match Ars's final max.
     *
     * <p>Without this, ARS_PRIMARY silently destroys mana. {@code MagicData.setMana} clamps
     * <em>every</em> write down to the {@code MAX_MANA} attribute, so a ceiling below the
     * current pool does not cap mana — it deletes it, on the next write, whatever that write
     * happens to be. This is the same failure {@code applyArsBonusesToIrons} documents for
     * HYBRID; ARS_PRIMARY reaches it by a different route because
     * {@code EquipmentIntegration.recomputeFor} deliberately clears the modifier in this mode
     * (writing it there would feed straight back into the calc above).
     *
     * <p>ANS-MED-001: deferred to the next server tick to break any potential reentrancy chain
     * — Iron's {@code MAX_MANA} mutations downstream might in turn fire
     * {@code MaxManaCalcEvent}. In the no-reentry case the only observable difference is one
     * tick of latency on the attribute sync.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void syncIronsMaxAfterCalc(MaxManaCalcEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!IronsCompat.isLoaded() || !BridgeManager.isUnificationEnabled()) {
            return;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode == null || !mode.isArsPrimary()) {
            return;
        }

        final int finalMax = event.getMax();
        if (player.getServer() != null) {
            player.getServer().tell(new TickTask(0,
                () -> EquipmentIntegration.syncIronsMaxToArs(player, finalMax)));
        } else {
            EquipmentIntegration.syncIronsMaxToArs(player, finalMax);
        }
    }

    /**
     * {@code conversion_rate_iron_to_ars}. The fold crosses a unit boundary — an Iron's-scale
     * bonus being added to an Ars-scale pool — so the same rate every other cross-scale path
     * honours applies here. Reading it raw would make the key silently directional.
     */
    private static double conversionRate() {
        try {
            return AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get();
        } catch (IllegalStateException configNotLoaded) {
            return 1.0;
        }
    }

    private static boolean shouldFoldIron(LivingEntity entity) {
        if (!(entity instanceof Player)) return false;
        if (!IronsCompat.isLoaded()) return false;
        if (!BridgeManager.isUnificationEnabled()) return false;
        // respect_armor_bonuses is the server owner's switch for "gear should not move the
        // mana pool". EquipmentIntegration honours it on the Ars->Iron's direction; this is
        // the same switch on the Iron's->Ars direction, which was skipping it entirely.
        if (!AnsConfig.flag(AnsConfig.respectArmorBonuses, true)) return false;
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        return mode == ManaUnificationMode.ARS_PRIMARY;
    }
}
