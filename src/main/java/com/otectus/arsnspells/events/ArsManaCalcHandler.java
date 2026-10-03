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
            event.setRegen(Math.max(0.0, event.getRegen() + absAdd));
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
        event.setMax((int) Math.max(0, Math.round(event.getMax() + ironsMax * conversionRate())));
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

        // 3.3.5: one deferred sync per player, applying the newest maximum. Ars recalculates
        // its max several times a tick (both player-tick phases, equipment reconciliation), and
        // each call used to queue its own task holding the value it saw. A value computed
        // before an equipment change could then be applied after the newer one.
        scheduleCeilingSync(player, event.getMax());
    }

    private static final java.util.Map<java.util.UUID, Integer> PENDING_CEILINGS =
        new java.util.concurrent.ConcurrentHashMap<>();

    static void scheduleCeilingSync(Player player, int max) {
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) {
            EquipmentIntegration.syncIronsMaxToArs(player, max);
            return;
        }
        java.util.UUID id = player.getUUID();
        // A queued task already exists; it will read this newer value when it runs.
        if (PENDING_CEILINGS.put(id, max) != null) return;
        server.tell(new TickTask(server.getTickCount(), () -> {
            Integer latest = PENDING_CEILINGS.remove(id);
            net.minecraft.server.level.ServerPlayer live = server.getPlayerList().getPlayer(id);
            // The player may have left, respawned into a new entity, or the mode may have
            // changed since this was queued; the live entity and routing decide.
            if (latest == null || live == null || live.isRemoved()) return;
            ManaUnificationMode mode = BridgeManager.getCurrentMode();
            if (!BridgeManager.isUnificationEnabled() || mode == null || !mode.isArsPrimary()) return;
            EquipmentIntegration.syncIronsMaxToArs(live, latest);
        }));
    }

    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_CEILINGS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        // An unrun task must not leave a mark that suppresses the next world's first sync.
        PENDING_CEILINGS.clear();
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
        if (!(entity instanceof Player) || entity.level().isClientSide()) return false;
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
