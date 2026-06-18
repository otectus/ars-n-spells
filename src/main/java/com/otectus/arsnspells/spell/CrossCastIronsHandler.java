package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrossCastIronsHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrossCastIronsHandler.class);

    @SubscribeEvent
    public void onIronsSpellCast(SpellOnCastEvent event) {
        Player player = event.getEntity();
        if (player == null || !BridgeManager.isUnificationEnabled()) {
            return;
        }

        ManaUnificationMode mode = BridgeManager.getCurrentMode();

        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        if (entry != null && entry.type == CrossSpellType.IRONS_SPELLBOOKS) {
            // Different spell than the one we tagged: stale or interleaved cast.
            if (entry.spellId != null && !entry.spellId.equals(event.getSpellId())) {
                CrossCastContext.clear(player);
                return;
            }

            int baseEventCost = event.getManaCost();

            if (mode == ManaUnificationMode.SEPARATE) {
                int issCost = Math.max(0, Math.round(entry.issCost));
                event.setManaCost(issCost);
                if (!player.isCreative() && entry.arsCost > 0.0f) {
                    BridgeManager.getBridge().consumeMana(player, entry.arsCost);
                }
            } else {
                float multiplier = (float) Math.max(0.0, AnsConfig.CROSS_CAST_COST_MULTIPLIER.get());
                int multiplied = Math.max(0, Math.round(baseEventCost * multiplier));
                if (mode == ManaUnificationMode.ARS_PRIMARY) {
                    multiplied = Math.max(0, (int) Math.round(
                        multiplied * AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get()));
                }
                event.setManaCost(multiplied);
            }

            if (AnsConfig.DEBUG_MODE != null && AnsConfig.DEBUG_MODE.get()) {
                LOGGER.info(
                    "[CrossCasting] [DEBUG] Iron's cross-cast spell={} mode={} baseEventCost={} finalCost={}",
                    event.getSpellId(), mode, baseEventCost, event.getManaCost());
            }

            CrossCastContext.clear(player);
            return;
        }

        // Normal Iron's cast (not via cross-cast pipeline). ARS_PRIMARY currency conversion still applies.
        if (mode == ManaUnificationMode.ARS_PRIMARY) {
            int adjusted = (int) Math.round(event.getManaCost() * AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get());
            event.setManaCost(Math.max(0, adjusted));
        }
    }
}
