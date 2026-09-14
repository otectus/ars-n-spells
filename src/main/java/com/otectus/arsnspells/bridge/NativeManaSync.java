package com.otectus.arsnspells.bridge;

import net.minecraft.server.level.ServerPlayer;

/** Send both native HUDs after a transition, without writing either dormant balance. */
public final class NativeManaSync {
    private NativeManaSync() {}
    public static void send(ServerPlayer player) {
        if (player.connection == null || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
        com.otectus.arsnspells.util.ManaUtil.getNativeMana(player).ifPresent(cap ->
            com.hollingsworth.arsnouveau.common.network.Networking.sendToPlayerClient(
                new com.hollingsworth.arsnouveau.common.network.PacketUpdateMana(cap.getCurrentMana(), cap.getMaxMana(), cap.getGlyphBonus(), cap.getBookTier()), player));
        if (com.otectus.arsnspells.compat.IronsCompat.isLoaded()) Irons.send(player);
    }
    private static final class Irons {
        static void send(ServerPlayer player) {
            io.redspace.ironsspellbooks.setup.Messages.sendToPlayer(new io.redspace.ironsspellbooks.network.SyncManaPacket(
                io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player)), player);
        }
    }
}
