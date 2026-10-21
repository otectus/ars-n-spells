package com.otectus.arsnspells.compat.irons.glyph;

import com.otectus.arsnspells.glyph.GlyphChildren;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Iron's-isolated glue between Iron's native cast slot and charged or channelled imported
 * glyphs. Registered on the event bus only when Iron's is loaded.
 *
 * <p>Each native {@code castSpell} invocation of a child (a long cast's release, each channel
 * pulse) runs inside the child's targeting scope, so it sees the Ars target and its spawns are
 * delivered. Once a tick, every child is checked: a deferred child starts when the slot frees,
 * and a child whose cast has left the slot is finished, resuming the rest of the Ars recipe when
 * its final invocation reached its effect and dropping it otherwise.
 */
public final class IronsGlyphChildHooks {
    public static IronTargetingScope.Scope enter(ServerPlayer player, AbstractSpell spell) {
        if (player == null || GlyphChildren.of(player.getUUID()) == null) return null;
        return IronsGlyphInvoker.enterChildInvocation(player, spell);
    }

    public static void exit(IronTargetingScope.Scope scope, boolean effectReached, boolean finalInvocation) {
        IronsGlyphInvoker.exitChildInvocation(scope, effectReached, finalInvocation);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) return;
        var children = GlyphChildren.all();
        if (children.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (GlyphChildren.Child child : children) {
            ServerPlayer player = child.invocation.caster();
            if (player == null || player.isRemoved() || player.hasDisconnected()) {
                GlyphChildren.finish(child, false, server);
                continue;
            }
            MagicData data = MagicData.getPlayerMagicData(player);
            if (!child.started) {
                if (child.pendingStart != null && !data.isCasting()) {
                    var start = child.pendingStart;
                    start.accept(child);
                    if (!child.started) GlyphChildren.finish(child, false, server);
                } else if (++child.waitTicks > IronsGlyphInvoker.MAX_CHILD_WAIT_TICKS) {
                    GlyphChildren.finish(child, false, server);
                }
                continue;
            }
            boolean stillRunning = data.isCasting() && child.spell.toString().equals(data.getCastingSpellId());
            if (!stillRunning) GlyphChildren.finish(child, child.completed, server);
        }
    }
}
