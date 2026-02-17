package com.otectus.arsnspells.network;

import com.otectus.arsnspells.cooldown.CooldownCategory;
import com.otectus.arsnspells.cooldown.UnifiedCooldownManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class CooldownSyncPacket {
    private final String categoryName;
    private final long timestamp;

    public CooldownSyncPacket(CooldownCategory category, long timestamp) {
        this.categoryName = category.name();
        this.timestamp = timestamp;
    }

    public CooldownSyncPacket(FriendlyByteBuf buf) {
        this.categoryName = buf.readUtf();
        this.timestamp = buf.readLong();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(categoryName);
        buf.writeLong(timestamp);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) {
            return;
        }
        context.enqueueWork(() -> {
            try {
                CooldownCategory cat = CooldownCategory.valueOf(categoryName);
                UnifiedCooldownManager.setClientCooldownEnd(cat, timestamp);
            } catch (IllegalArgumentException ignored) {
            }
        });
        context.setPacketHandled(true);
    }
}
