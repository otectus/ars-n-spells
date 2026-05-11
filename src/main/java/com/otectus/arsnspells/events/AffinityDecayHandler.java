package com.otectus.arsnspells.events;

import com.otectus.arsnspells.affinity.AffinityType;
import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.network.AffinitySyncPacket;
import com.otectus.arsnspells.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Periodically decays player affinity levels when no spells of that school
 * are being cast. Implementation of {@code enable_affinity_decay} that, prior
 * to 1.9.0, was promised by config but never actually implemented.
 *
 * <p>Decay math: at each interval (default 60s = 1200 ticks), every non-zero
 * affinity loses {@code level * AFFINITY_DECAY_RATE * (interval / 24000)}.
 * With defaults (rate=0.01, interval=1200), each tick window strips ~0.05% of
 * the current level — so a maxed-out (level 100) school takes roughly 100
 * in-game days of pure inactivity to fully decay.
 *
 * <p>Iron's-independent: registered unconditionally.
 */
public class AffinityDecayHandler {

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!AnsConfig.ENABLE_AFFINITY_SYSTEM.get() || !AnsConfig.ENABLE_AFFINITY_DECAY.get()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        int interval = AnsConfig.AFFINITY_DECAY_INTERVAL_TICKS.get();
        if (interval <= 0) {
            return;
        }
        if (player.tickCount % interval != 0) {
            return;
        }

        double rate = AnsConfig.AFFINITY_DECAY_RATE.get();
        if (rate <= 0.0) {
            return;
        }
        // Per-day rate prorated to per-interval (24000 ticks per Minecraft day).
        double perInterval = rate * (interval / 24000.0);
        if (perInterval <= 0.0) {
            return;
        }

        player.getCapability(AffinityData.AFFINITY_DATA).ifPresent(data -> {
            for (AffinityType type : AffinityType.values()) {
                int level = data.getLevel(type);
                if (level <= 0) {
                    continue;
                }
                int decay = (int) Math.max(1, Math.floor(level * perInterval));
                int newLevel = Math.max(0, level - decay);
                if (newLevel != level) {
                    data.setLevel(type, newLevel);
                    PacketHandler.sendToClient(new AffinitySyncPacket(type, newLevel), player);
                }
            }
        });
    }
}
