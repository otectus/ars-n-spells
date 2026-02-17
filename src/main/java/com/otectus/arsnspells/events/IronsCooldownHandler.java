package com.otectus.arsnspells.events;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.cooldown.CooldownCategory;
import com.otectus.arsnspells.cooldown.SpellCategorizer;
import com.otectus.arsnspells.cooldown.UnifiedCooldownManager;
import com.otectus.arsnspells.network.CooldownSyncPacket;
import com.otectus.arsnspells.network.PacketHandler;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class IronsCooldownHandler {
    @SubscribeEvent
    public void onIronsSpellCast(SpellPreCastEvent event) {
        // CRITICAL FIX: Do NOT apply unified cooldowns to Iron's Spellbooks
        // Iron's has its own internal cooldown system that should not be interfered with
        // Only apply unified cooldowns if explicitly configured for cross-mod cooldowns
        
        if (!AnsConfig.ENABLE_COOLDOWN_SYSTEM.get()) {
            return;
        }
        
        // Only apply if cross-mod cooldowns are explicitly enabled
        if (!AnsConfig.ENABLE_CROSS_MOD_COOLDOWNS.get()) {
            return;
        }
        
        Player player = event.getEntity();
        if (player == null || event.getSchoolType() == null) {
            return;
        }
        
        CooldownCategory category = SpellCategorizer.categorizeIronsSpell(event.getSchoolType().getId());
        
        // CRITICAL FIX: Only check IRONS-namespaced cooldowns
        if (UnifiedCooldownManager.isOnCooldown(player, category, "irons")) {
            event.setCanceled(true);
        } else {
            long cooldownEnd = UnifiedCooldownManager.applyCooldownAndGetEnd(player, category, false, "irons");
            if (!player.level().isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                PacketHandler.sendToClient(new CooldownSyncPacket(category, cooldownEnd), serverPlayer);
            }
        }
    }
}
