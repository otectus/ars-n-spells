package com.otectus.arsnspells.events;

import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.cooldown.CooldownCategory;
import com.otectus.arsnspells.cooldown.UnifiedCooldownManager;
import com.otectus.arsnspells.util.SpellAnalysis;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.network.CooldownSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;

public class CooldownHandler {
    @SubscribeEvent
    public void onArsSpellCast(SpellCastEvent event) {
        if (!UnifiedCooldownManager.isEnabled() || !AnsConfig.ENABLE_COOLDOWN_SYSTEM.get()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            // Use standard Ars Nouveau 4.12.7 field: spell
            CooldownCategory category = SpellAnalysis.analyze(event.spell).category();
            
            // CRITICAL FIX: Only check ARS-namespaced cooldowns
            if (UnifiedCooldownManager.isOnCooldown(player, category, "ars")) {
                event.setCanceled(true);
            } else {
                long cooldownEnd = UnifiedCooldownManager.applyCooldownAndGetEnd(player, category, false, "ars");
                // Logic: High-fidelity sync ensuring the client HUD mirrors the lockout
                PacketHandler.sendToClient(new CooldownSyncPayload(category, cooldownEnd), player);
            }
        }
    }
}
