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
        // ANS-MED-016: bounded readUtf.
        this.categoryName = buf.readUtf(32);
        // ANS-MED-017: clamp the timestamp to a sane ceiling so a hostile server
        // cannot set an effectively-infinite cooldown via Long.MAX_VALUE. 1e12
        // ticks is ~1.5 million IRL years — plenty of headroom for any legitimate
        // cooldown.
        long raw = buf.readLong();
        this.timestamp = Math.max(0L, Math.min(raw, 1_000_000_000_000L));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(categoryName, 32);
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
