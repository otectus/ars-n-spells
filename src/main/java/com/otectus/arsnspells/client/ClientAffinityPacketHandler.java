package com.otectus.arsnspells.client;

import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.data.AffinityData;
import net.minecraft.client.Minecraft;

public final class ClientAffinityPacketHandler {
    private ClientAffinityPacketHandler() {}

    public static void apply(String typeName, int level) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        mc.player.getCapability(AffinityData.AFFINITY_DATA).ifPresent(data -> {
            try {
                AffinityType type = AffinityType.valueOf(typeName);
                data.setLevel(type, level);
            } catch (IllegalArgumentException ignored) {
            }
        });
    }
}
