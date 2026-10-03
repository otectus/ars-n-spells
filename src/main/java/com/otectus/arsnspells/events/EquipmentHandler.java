package com.otectus.arsnspells.events;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.equipment.EquipmentIntegration;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-mod equipment-change notification. Fires whenever a player swaps
 * any armor / off-hand / main-hand slot, and on login, and refreshes the
 * Ars→Iron's gear-bonus modifiers via {@link EquipmentIntegration}.
 *
 * <p>A once-per-second tick also refreshes the bonuses so that dynamically
 * applied Ars sources — mana_regen / mana_boost potions and effects, perk
 * changes — propagate to Iron's even though they fire no equipment-change
 * event. This replaces the Forge 1.20.1 {@code MixinArsPotionEffects} mixin:
 * because Ars 5.x expresses those bonuses as {@code PerkAttributes} modifiers
 * and {@link EquipmentIntegration} reads that aggregate, a periodic recompute
 * captures them with no separate potion code and no double-counting.
 *
 * <p>Respawn and dimension-change refreshes are owned by
 * {@link CapabilityResyncHandler} (which also replays the other per-player
 * capabilities), so they are intentionally not duplicated here.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class EquipmentHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EquipmentHandler.class);
    private static final java.util.Set<java.util.UUID> DIRTY = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private EquipmentHandler() {}

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (AnsConfig.debugEnabled()) {
            LOGGER.info("[Equipment] {} swapped slot {}: {} -> {}",
                player.getName().getString(),
                event.getSlot(),
                event.getFrom(),
                event.getTo());
        }
        // Deliberately no recomputation here. NeoForge posts this event before vanilla removes
        // the old stack's attribute modifiers and adds the new one's, so a ceiling computed now
        // mixes the new stack with the old attributes; scrolling the hotbar then pushed a
        // transient, wrong max into the shared pool's mirror. The DIRTY mark is reconciled at
        // this player's tick end, after the swap, against the attributes actually in force.
        DIRTY.add(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.otectus.arsnspells.bridge.ModeChangeCleanup.reconcile(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Once per second — picks up dynamic Ars perk/potion mana sources. The
        // recompute is cheap and only churns Iron's attributes when a value changed.
        if (DIRTY.remove(player.getUUID()) || player.tickCount % 20 == 0) {
            if (com.otectus.arsnspells.bridge.ManaTrace.enabled()) {
                var before = com.otectus.arsnspells.bridge.ManaTrace.Snapshot.of(player);
                EquipmentIntegration.recomputeFor(player);
                com.otectus.arsnspells.bridge.ManaTrace.reconciled(player, before);
            } else {
                EquipmentIntegration.recomputeFor(player);
            }
        }
        // Ars potion effects mirrored onto Iron's in ISS_PRIMARY, every tick as on Forge.
        if (com.otectus.arsnspells.compat.IronsCompat.isLoaded()) {
            com.otectus.arsnspells.equipment.PotionContributions.reconcile(player);
        }
    }

    @SubscribeEvent
    public static void onCurioChange(top.theillusivec4.curios.api.event.CurioChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) DIRTY.add(player.getUUID());
    }
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { DIRTY.remove(event.getEntity().getUUID()); }
    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) { DIRTY.clear(); }
}
