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

            if (UnifiedCooldownManager.isOnCooldown(player, category)) {
                // ANS-MED-027 (NEEDS VERIFY): Ars Nouveau's SpellCastEvent is documented
                // as @Cancelable in 4.12.x (parent SpellEvent class). If a future Ars
                // version makes it non-cancellable, this will be a silent no-op and
                // cooldown enforcement will fall back to the per-spell-class gating in
                // SpellResolver. Verify cancellability in dev when upgrading Ars.
                event.setCanceled(true);
            }
        }
    }
    public static void commit(ServerPlayer player, com.hollingsworth.arsnouveau.api.spell.SpellContext context) {
        if (!UnifiedCooldownManager.isEnabled()) return;
        CooldownCategory category = SpellAnalysis.analyze(context.getSpell()).category();
        long end = UnifiedCooldownManager.applyCooldownAndGetEnd(player, category,
            com.otectus.arsnspells.casting.ArsCastPayments.isCrossCast(context));
        PacketHandler.sendToClient(new CooldownSyncPayload(category, end), player);
    }
}