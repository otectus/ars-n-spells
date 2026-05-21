package com.otectus.arsnspells.client;

import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.data.AffinityData;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANS-HIGH-019: marked {@code @OnlyIn(Dist.CLIENT)} so this class is not classloaded
 * on dedicated servers. The DistExecutor double-lambda at the call site
 * ({@link com.otectus.arsnspells.network.AffinitySyncPacket}) defers invocation but
 * not classload; the outer lambda's bytecode references this type, so without the
 * annotation a stray classload on the server would NoClassDefFoundError on Minecraft.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientAffinityPacketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAffinityPacketHandler.class);

    private ClientAffinityPacketHandler() {}

    public static void apply(String typeName, int level) {
        // ANS-LOW-001: Minecraft.getInstance() never returns null in any documented version;
        // the previous mc == null check was dead. Only the player can be null (between worlds).
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.player.getCapability(AffinityData.AFFINITY_DATA).ifPresent(data -> {
            try {
                AffinityType type = AffinityType.valueOf(typeName);
                data.setLevel(type, level);
            } catch (IllegalArgumentException e) {
                // ANS-LOW-004: log mod-version skew so the user can diagnose desync,
                // instead of silently dropping the packet.
                LOGGER.warn("Unknown AffinityType from server: {}", typeName);
            }
        });
    }
}
