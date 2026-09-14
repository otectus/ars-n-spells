package com.otectus.arsnspells.bridge;

import net.minecraft.server.level.ServerPlayer;

/** Send both native HUDs after a transition, without writing either dormant balance. */
public final class NativeManaSync {
    private NativeManaSync() {}
    public static void send(ServerPlayer player) {
        if (player.connection == null || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
        var cap = com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.getMana(player);
        if (cap != null) cap.syncToClient(player);
        if (com.otectus.arsnspells.compat.IronsCompat.isLoaded()) Irons.send(player);
    }
    private static final class Irons {
        static void send(ServerPlayer player) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new io.redspace.ironsspellbooks.network.SyncManaPacket(
                io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player)));
        }
    }
}
