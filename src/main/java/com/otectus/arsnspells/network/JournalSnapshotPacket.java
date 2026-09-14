package com.otectus.arsnspells.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Command-requested S2C only; the server captures only the requesting player's journal. */
public record JournalSnapshotPacket(SchoolJournalSnapshot snapshot) {
    public JournalSnapshotPacket(FriendlyByteBuf buffer) { this(SchoolJournalSnapshot.read(buffer)); }
    public void toBytes(FriendlyByteBuf buffer) { snapshot.write(buffer); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        if (context == null) return;
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.otectus.arsnspells.client.screen.SchoolJournalScreen.open(snapshot)));
        context.setPacketHandled(true);
    }
}
