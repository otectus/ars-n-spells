package com.otectus.arsnspells.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import com.otectus.arsnspells.affinity.AffinityType;

public class AffinitySyncPacket {
    private final java.util.Map<String, Integer> levels;
    private final boolean replacement;
    private static final int MAX_ENTRIES = 1024;

    public AffinitySyncPacket(AffinityType type, int level) {
        this(type.name(), level);
    }

    public AffinitySyncPacket(String school, int level) {
        this(java.util.Map.of(com.otectus.arsnspells.util.SchoolKeys.normalize(school), level), false);
    }

    public AffinitySyncPacket(java.util.Map<String, Integer> levels, boolean replacement) {
        if (levels.size() > MAX_ENTRIES) throw new IllegalArgumentException("Affinity snapshot exceeds " + MAX_ENTRIES + " schools");
        java.util.Map<String, Integer> clean = new java.util.TreeMap<>();
        levels.forEach((school, level) -> clean.put(com.otectus.arsnspells.util.SchoolKeys.normalize(school), Math.max(0, Math.min(100, level))));
        this.levels = java.util.Map.copyOf(clean);
        this.replacement = replacement;
    }

    public AffinitySyncPacket(FriendlyByteBuf buf) {
        replacement = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid affinity snapshot size");
        java.util.Map<String, Integer> clean = new java.util.TreeMap<>();
        for (int i = 0; i < count; i++) clean.put(buf.readUtf(256), Math.max(0, Math.min(100, buf.readInt())));
        levels = java.util.Map.copyOf(clean);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(replacement);
        buf.writeVarInt(levels.size());
        levels.forEach((school, level) -> { buf.writeUtf(school, 256); buf.writeInt(level); });
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) {
            return;
        }
        context.enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.otectus.arsnspells.client.ClientAffinityPacketHandler.apply(levels, replacement)));
        context.setPacketHandled(true);
    }
}
