package com.otectus.arsnspells.events;

import com.otectus.arsnspells.ArsNSpells;
import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.bridge.IManaBridge;
import com.otectus.arsnspells.bridge.ManaRegenBridge;
import com.otectus.arsnspells.compat.IronsCompat;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Cross-system mana regen synergy. In ISS_PRIMARY and HYBRID modes the Ars
 * native regen tick is suppressed by {@code MixinManaCapability}; without
 * this handler, any Ars-side regen sources (mana_regen potion, source-jar
 * proximity, etc.) would never reach Iron's pool. Once per second this
 * handler reads Ars's regen rate (via {@link ManaRegenBridge}'s
 * Iron's-to-Ars helpers, inverted) and adds it to Iron's MagicData.
 *
 * <p>The math itself lives in {@link ManaRegenBridge}. This handler is
 * just the periodic dispatcher.
 */
@EventBusSubscriber(modid = ArsNSpells.MODID)
public final class RegenSynergyHandler {

    private RegenSynergyHandler() {}

    @SubscribeEvent
    public static void onPlayerTickPost(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!IronsCompat.isLoaded()) {
            return;
        }
        if (!BridgeManager.isUnificationEnabled()) {
            return;
        }
        ManaUnificationMode mode = BridgeManager.getCurrentMode();
        if (mode != ManaUnificationMode.ISS_PRIMARY && mode != ManaUnificationMode.HYBRID) {
            return;
        }
        // Once-per-second cadence — matches Ars's native regen tick.
        if (player.tickCount % 20 != 0) {
            return;
        }

        // Ars regen comes from its ManaRegenCalcEvent path; reading it externally
        // would require firing the event ourselves. Instead, sample Iron's
        // MANA_REGEN attribute and convert the Iron's-side gear regen to an
        // absolute mana/sec figure, then add it to the Iron's pool. Net effect:
        // Iron's keeps its native regen AND picks up any cross-fed Ars-side
        // bonuses that were routed into MANA_REGEN by EquipmentIntegration.
        double ironsRegenAttr;
        try {
            ironsRegenAttr = player.getAttributeValue(AttributeRegistry.MANA_REGEN);
        } catch (Throwable t) {
            return;
        }
        if (ironsRegenAttr <= 0.0) {
            return;
        }
        double synergyMul = AnsConfig.CROSS_SYSTEM_REGEN_MULTIPLIER.get();
        if (synergyMul <= 0.0) {
            return;
        }
        double absRegenPerSec = ManaRegenBridge.ironsToArsRegen(ironsRegenAttr,
            ManaRegenBridge.getCurrentIronsMaxMana(player));
        float add = (float) (absRegenPerSec * synergyMul);
        if (add <= 0.0f) {
            return;
        }
        IManaBridge bridge = BridgeManager.getBridge();
        if (bridge == null) {
            return;
        }
        bridge.setMana(player, Math.min(bridge.getMaxMana(player), bridge.getMana(player) + add));
    }
}
