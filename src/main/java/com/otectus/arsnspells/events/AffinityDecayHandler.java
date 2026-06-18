package com.otectus.arsnspells.events;

import com.otectus.arsnspells.config.AnsConfig;
import com.otectus.arsnspells.data.AffinityData;
import com.otectus.arsnspells.data.AttachmentTypes;
import com.otectus.arsnspells.network.AffinitySyncPayload;
import com.otectus.arsnspells.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;

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
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!AnsConfig.ENABLE_AFFINITY_SYSTEM.get() || !AnsConfig.ENABLE_AFFINITY_DECAY.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
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

        AffinityData data = player.getData(AttachmentTypes.AFFINITY.get());
        // getAllLevels() returns a copy, so mutating the original via setLevel
        // inside the loop is safe (no ConcurrentModificationException).
        for (Map.Entry<String, Integer> entry : data.getAllLevels().entrySet()) {
            int level = entry.getValue() == null ? 0 : entry.getValue();
            if (level <= 0) {
                continue;
            }
            int decay = (int) Math.max(1, Math.floor(level * perInterval));
            int newLevel = Math.max(0, level - decay);
            if (newLevel != level) {
                data.setLevel(entry.getKey(), newLevel);
                PacketHandler.sendToClient(new AffinitySyncPayload(entry.getKey(), newLevel), player);
            }
        }
    }
}
