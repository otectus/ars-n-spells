package com.otectus.arsnspells.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Command-requested S2C only; the server captures only the requesting player's journal. */
public record JournalSnapshotPayload(SchoolJournalSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<JournalSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ars_n_spells", "journal_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JournalSnapshotPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, value) -> value.snapshot.write(buffer), buffer -> new JournalSnapshotPayload(SchoolJournalSnapshot.read(buffer)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handleOnClient(JournalSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.otectus.arsnspells.client.screen.SchoolJournalScreen.open(payload.snapshot));
    }
}
