package com.otectus.arsnspells.spell;

import com.otectus.arsnspells.bridge.BridgeManager;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.config.ManaUnificationMode;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CrossCastIronsHandler {

    @SubscribeEvent
    public void onIronsSpellCast(SpellOnCastEvent event) {
        Player player = event.getEntity();
        if (player == null || !BridgeManager.isUnificationEnabled()) {
            return;
        }

        ManaUnificationMode mode = BridgeManager.getCurrentMode();

        CrossCastContext.Entry entry = CrossCastContext.peek(player);
        if (entry != null && entry.type == CrossSpellType.IRONS_SPELLBOOKS) {
            if (entry.spellId != null && !entry.spellId.equals(event.getSpellId())) {
                CrossCastContext.clear(player);
                return;
            }
            if (mode == ManaUnificationMode.SEPARATE) {
                int issCost = Math.max(0, Math.round(entry.issCost));
                event.setManaCost(issCost);
                if (!player.isCreative() && entry.arsCost > 0.0f) {
                    BridgeManager.getBridge().consumeMana(player, entry.arsCost);
                }
            }
            CrossCastContext.clear(player);
            return;
        }

        if (mode == ManaUnificationMode.ARS_PRIMARY) {
            int adjusted = (int) Math.round(event.getManaCost() * AnsConfig.CONVERSION_RATE_IRON_TO_ARS.get());
            event.setManaCost(Math.max(0, adjusted));
        }
    }
}
