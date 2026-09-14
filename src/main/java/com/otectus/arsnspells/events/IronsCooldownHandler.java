package com.otectus.arsnspells.events;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.cooldown.CooldownCategory;
import com.otectus.arsnspells.cooldown.SpellCategorizer;
import com.otectus.arsnspells.cooldown.UnifiedCooldownManager;
import com.otectus.arsnspells.network.CooldownSyncPacket;
import com.otectus.arsnspells.network.PacketHandler;
import com.otectus.arsnspells.spell.CrossCastNbt;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class IronsCooldownHandler {
    /**
     * ANS-LOW-032: renamed from {@code onIronsSpellCast} because the handler listens
     * to {@code SpellPreCastEvent}, not {@code SpellOnCastEvent}. Forge auto-discovers
     * {@code @SubscribeEvent} methods by signature, so the rename is transparent to
     * the event bus.
     */
    @SubscribeEvent
    public void onIronsSpellPreCast(SpellPreCastEvent event) {
        // ANS cross-cast proxies carry a 0s cooldown of their own; the delegated Ars
        // cast applies the real category cooldown. Running the unified cooldown here
        // would gate the wheel slot on a cooldown the Ars spell also charges.
        if (CrossCastNbt.isArsCrossProxyId(event.getSpellId())) {
            return;
        }

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

        // Cooldowns are global per category — an Iron's OFFENSIVE cast collides with an
        // Ars OFFENSIVE cast and vice versa. This is the documented behavior in 1.9.0+.
        if (UnifiedCooldownManager.isOnCooldown(player, category)) {
            event.setCanceled(true);
        }
    }

    public static void commit(Player player, io.redspace.ironsspellbooks.api.spells.AbstractSpell spell,
                              io.redspace.ironsspellbooks.api.spells.CastSource source, boolean cross) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || !source.respectsCooldown() || CrossCastNbt.isArsCrossProxyId(spell.getSpellId())
                || !UnifiedCooldownManager.isEnabled() || !AnsConfig.ENABLE_CROSS_MOD_COOLDOWNS.get()) return;
        CooldownCategory category = SpellCategorizer.categorizeIronsSpell(spell.getSchoolType().getId());
        long end = UnifiedCooldownManager.applyCooldownAndGetEnd(player, category, cross);
        PacketHandler.sendToClient(new CooldownSyncPacket(category, end), serverPlayer);
    }
}